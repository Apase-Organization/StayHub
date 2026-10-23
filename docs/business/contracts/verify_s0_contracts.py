"""Stdlib docs contract verifier, not application behavior or DB concurrency proof.

Run exactly: python3 docs/business/contracts/verify_s0_contracts.py --self-test
Only three named JSON inputs + explicit Markdown manifest are read. No discovery,
network, subprocess, project runner, generated output or production activation.
"""
import copy
import hashlib
import json
from collections import Counter
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path
import re
import sys
import time

BASE = Path(__file__).resolve().parent
ROOT = BASE.parents[2]
DOCS = [
    "business/PaymentBusinessRules.md", "business/PropertyInventoryBusinessRules.md",
    "business/OverviewBusiness.md", "business/S0DecisionLog.md", "tasklist/TaskList.md",
    "architecture/0_DemoSystem.md", "architecture/1_SystemOverview.md",
    "architecture/2_TechStack.md", "architecture/3_API_Design.md",
    "database/0_DatabaseDesignDemo.md", "database/1_DomainOverview.md",
    "database/2_RelationalDiagram.md", "database/3_CoreERD.md",
    "database/4_ClassDiagram.md", "database/5_User.md", "database/6_Properties.md",
    "database/7_Booking.md", "database/8_Payment.md", "database/9_Reviews.md",
    "database/10_Discounts.md", "ui/UXUI_DesignSystem.md", "ui/ApplicationDesigned.md",
    "contributors/Rules.md", "contributors/FirstStep.md",
]
S0 = {79, 97, 81, 84, 86, 88, 90, 109}
GROUP_IDS = {
    "quotes": "full-1m hybrid-1m full-6m8 hybrid-6m8 cleaning-discount funding-excess tiny-online-zero zero-total malformed-deposit-config",
    "allocations": "tie cleaning zero-base",
    "coverage": "net-not-gross-primitive under-net-primitive",
    "inventory": "all-items-happy family-second-night-zero plans-share-pool",
    "refunds": "free-hybrid-uncollected free-hybrid-direct host-inability free-full late-no-cleaning late-cleaning-partial-direct late-cleaning-online-only after-arrival",
    "pools": "cash-boundary cash-over policy-boundary policy-over no-liquidity",
    "attempts": "retry-success wrong-total-not-O exact-expiry resold-closed extra-retry-success duplicate-winner attempt-failure-retry worker-third-pending worker-all-failed worker-expired fourth-creation retry-original-deadline",
    "occupancy": "child-boundaries child18 fraction no-adult over-capacity",
    "dates": "before-cutoff cutoff future-after-cutoff max-nights too-long zero-nights",
    "reviews": "review-before-end review-exact-end no-show-review second-review",
    "arrivals": "all-arrived-boundary before24h partial-arrival host-tap admin-no-legal-bypass actual-before-booking actual-future",
    "completions": "early-checkout checkout-boundary calendar-only unresolved-case",
    "caps": "last-slot race-loser user-held consumed-cancel-no-restore missing-quota",
}


def require(ok, label):
    if not ok:
        raise ValueError(label)


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, f"duplicate JSON key {key}")
        result[key] = value
    return result


def load(name):
    return json.loads((BASE / name).read_text(), object_pairs_hook=unique_object,
                      parse_constant=lambda x: (_ for _ in ()).throw(ValueError(x)))


def whole(value):
    return int(Decimal(str(value)).quantize(Decimal(1), rounding=ROUND_HALF_UP))


def integer(x, low=0):
    return type(x) is int and x >= low


def same_typed(a, b):
    if type(a) is not type(b):
        return False
    if isinstance(a, dict):
        return set(a) == set(b) and all(same_typed(a[k], b[k]) for k in a)
    if isinstance(a, list):
        return len(a) == len(b) and all(same_typed(x,y) for x,y in zip(a,b))
    return a == b


def allocate(total, weights):
    require(integer(total) and all(integer(w) for w in weights), "allocation units")
    base = sum(weights)
    require(total <= base, "allocation exceeds base")
    if base == 0:
        return [0] * len(weights)
    # Integer quotient/remainder, stable input ordinal ties; no float precision loss.
    shares = [total * w // base for w in weights]
    order = sorted(range(len(weights)), key=lambda i: (-(total * weights[i] % base), i))
    for i in order[:total - sum(shares)]:
        shares[i] += 1
    return shares


def quote(i, p):
    require(i["paymentMode"] in p["collection"]["modes"], "unknown payment mode")
    if i.get("depositBps", p["money"]["depositBps"]) != p["money"]["depositBps"]:
        return {"errorCode": "ERR_PAYMENT_CONFIG"}
    room, clean, d = i["roomCharges"], i["cleaningFee"], i["discount"]
    require(all(integer(x) for x in (room, clean, d)), "quote units")
    s = room + clean
    gross = whole(Decimal(s) * p["money"]["commissionBps"] / 10000)
    if d > gross or d > s:
        return {"errorCode": "ERR_DISCOUNT_FUNDING"}
    t, net = s - d, gross - d
    o = t if i["paymentMode"] == "FULL_PREPAY" else whole(Decimal(t) * p["money"]["depositBps"] / 10000)
    if t <= 0 or o <= 0:
        return {"errorCode": "ERR_NONPOSITIVE_BOOKING_AMOUNT"}
    if o < net:
        return {"errorCode": "ERR_DEPOSIT_COVERAGE"}
    return dict(subtotal=s, totalStay=t, grossCommission=gross, netRetention=net,
                dueOnline=o, balanceDueHost=t-o, platformHeldHostPayable=o-net,
                hostEntitlement=s-gross)


def evaluate(group, c, p, quotes):
    if group == "quotes":
        return quote(c["input"], p)
    if group == "allocations":
        return allocate(c["discount"], c["charges"])
    if group == "coverage":
        require(c["scope"] == "GUARD_UNIT_ONLY_NOT_VALID_MVP_QUOTE_CONFIG", "coverage primitive scope")
        require(c["grossCommission"]-c["discount"] == c["netRetention"], "primitive net retention")
        return c["netRetention"] >= 0 and c["dueOnline"] > 0 and c["dueOnline"] >= c["netRetention"]
    if group == "inventory":
        totals = Counter()
        for item in c["items"]:
            require(integer(item["quantity"],1), "inventory quantity")
            totals[str(item["roomTypeId"])] += item["quantity"]
        remaining = c["remainingByType"]
        accepted = all(k in remaining and remaining[k] and min(remaining[k]) >= q for k,q in totals.items())
        after = {k:[v-totals[k] for v in nights] for k,nights in remaining.items()} if accepted else remaining
        return dict(accepted=accepted,remainingByType=after)
    if group == "refunds":
        if c["actualStay"]:
            return {"errorCode": "ERR_REFUND_CASE_REQUIRED"}
        qi = quotes[c["quote"]]["input"]
        discount = allocate(qi["discount"], [qi["roomCharges"], qi["cleaningFee"]])
        net = [qi["roomCharges"]-discount[0], qi["cleaningFee"]-discount[1]]
        online_lines = allocate(c["online"], net)
        direct_lines = allocate(c["direct"], [v-a for v, a in zip(net, online_lines)])
        full = c["hours"] >= p["refund"]["freeWindowHours"] or c["reason"] == "HOST_INABILITY"
        rp = c["online"] if full else online_lines[1]
        rh = c["direct"] if full else direct_lines[1]
        return dict(platformRefundable=rp, hostDirectRefundResponsibility=rh, totalPolicyRefund=rp+rh)
    if group == "pools":
        return 0 < c["request"] <= min(c["collected"]-c["paid"]-c["reserved"], c["policy"]-c["paid"]-c["reserved"], c["liquid"])
    if group == "attempts":
        active = c["elapsedSeconds"] < p["collection"]["holdSeconds"]
        if c["duplicate"]:
            return "NO_OP"
        event = c["event"]
        if event == "FAILURE":
            return "ATTEMPT_ONLY"
        if event == "WORKER":
            if c["credited"] or c["closed"]:
                return "KEEP"
            return "RELEASE" if not active or (c["createdAttempts"] == p["collection"]["maxCheckoutCreations"] and c["allFailed"] and not c["pending"]) else "KEEP"
        if event == "CREATE":
            if not active or c["closed"]:
                return "ERR_HOLD_EXPIRED"
            return "ERR_PAYMENT_ATTEMPTS_EXHAUSTED" if c["createdAttempts"] >= p["collection"]["maxCheckoutCreations"] else "CREATE_WITH_ORIGINAL_EXPIRY"
        expected = quote(quotes[c["quote"]]["input"], p)["dueOnline"]
        return "CONFIRM" if active and c["pending"] and not c["closed"] and not c["credited"] and c["amount"] == expected else "RECONCILE"
    if group == "occupancy":
        return integer(c["adults"], p["stay"]["minAdultsPerRoom"]) and all(type(a) is int and p["stay"]["childMinAge"] <= a <= p["stay"]["childMaxAge"] for a in c["childrenAges"]) and c["adults"]+len(c["childrenAges"]) <= c["capacity"]
    if group == "dates":
        created = datetime.fromisoformat(c["createdAt"]).astimezone(timezone(timedelta(minutes=p["stay"]["utcOffsetMinutes"])))
        checkin, checkout = date.fromisoformat(c["checkIn"]), date.fromisoformat(c["checkOut"])
        return checkin >= created.date() and p["stay"]["minNights"] <= (checkout-checkin).days <= p["stay"]["maxNights"] and (checkin != created.date() or created.strftime("%H:%M") < p["stay"]["sameDayCutoff"])
    if group == "reviews":
        return c["completed"] and c["actualStay"] and not c["existing"] and 0 <= c["elapsedSeconds"] < p["review"]["windowSeconds"]
    if group == "arrivals":
        a = c["actualSeconds"]
        return len(a) == c["expectedRooms"] and len(a) > 0 and all(0 <= t <= c["nowSeconds"] for t in a) and (c["acknowledged"] or c["adminCaseVerified"]) and c["nowSeconds"] >= max(a)+p["arrival"]["eligibilityDelaySeconds"] and c["legal"] and c["liquid"] and not c["dispute"]
    if group == "completions":
        return c["secondsAfterScheduledCheckout"] >= 0 and c["actualStay"] and not c["unresolvedCase"]
    if group == "caps":
        q = c["quota"]
        return integer(q, 1) and c["consumedGlobal"]+c["reservedGlobal"] < q and c["consumedUser"]+c["reservedUser"] < p["discount"]["perUserCodeConsumptions"]
    raise ValueError(group)


def policy_checks(p):
    # Independent approved oracles: editing both fixtures and calculations cannot
    # silently approve a new rate, deadline, clearance or legal assumption.
    fixed = {
        ("money", "commissionBps"):1500, ("money", "depositBps"):2000,
        ("money", "guestServiceFee"):0, ("money", "rounding"):"HALF_UP",
        ("money", "discountFunder"):"PLATFORM", ("money", "providerFeePayer"):"PLATFORM",
        ("collection", "holdSeconds"):900, ("collection", "maxCheckoutCreations"):3,
        ("collection", "retryExtendsHold"):False, ("collection", "returnReadOnly"):True,
        ("collection", "hostApproval"):False, ("discount", "perUserCodeConsumptions"):1,
        ("discount", "codesPerBookingMax"):1, ("discount", "allowZeroOnline"):False,
        ("discount", "allowZeroTotal"):False, ("discount", "restoreConsumedOnCancelRefund"):False,
        ("discount", "attemptFailureReleases"):False, ("stay", "timezone"):"Asia/Ho_Chi_Minh",
        ("stay", "utcOffsetMinutes"):420, ("stay", "checkIn"):"14:00", ("stay", "checkOut"):"11:00",
        ("stay", "minNights"):1, ("stay", "maxNights"):30, ("stay", "sameDayCutoff"):"20:00",
        ("stay", "adultMinAge"):18, ("stay", "childMinAge"):0, ("stay", "childMaxAge"):17,
        ("stay", "minAdultsPerRoom"):1, ("stay", "childPricing"):False, ("stay", "extraBeds"):False,
        ("arrival", "eligibilityDelaySeconds"):86400, ("arrival", "hostTapSufficient"):False,
        ("arrival", "maxBackdateSeconds"):None, ("review", "windowSeconds"):1209600,
        ("review", "allowNoShow"):False, ("refund", "freeWindowHours"):48,
        ("refund", "platformSlaBusinessDays"):[5,10], ("refund", "autoChargeDirectBalance"):False,
    }
    for (section, field), value in fixed.items():
        require(type(p[section][field]) is type(value) and p[section][field] == value, f"approved policy {section}.{field}")
    require(p["policyVersion"] == "s0-policy-v1" and p["status"] == "FROZEN_DESIGN" and p["sourceCompatibility"] == "NONE_IMPLEMENTATION_REQUIRED", "policy version/status")
    require(p["inventory"] == {"saleModes":["WHOLE_PLACE","ROOM_BASED"],"linkedWholeRoomSale":False,"pool":"TRULY_INTERCHANGEABLE","plansShareStock":True,"wholePlaceQuantity":1,"bedroomsDescriptive":True,"physicalAssignment":"FUTURE","onePropertyDateCurrency":True,"cancellation":"WHOLE_BOOKING"}, "approved inventory boundaries")
    require(set(p["approval"]["tasks"]) == S0 and p["approval"]["authority"] == "USER_PRODUCT_OWNER_QUESTIONNAIRE", "approval provenance")
    require(p["discount"]["campaignQuota"] == {"requiredAtPublish":True,"type":"POSITIVE_FINITE_INTEGER","default":None}, "campaign input, not real budget")
    for key, value in p["clearance"].items():
        expected = 95 if key == "releaseTask" else "BLOCKED" if key == "status" else True if key == "paidPublishingBlocked" else None if key in {"calendarSource","calendarVersion","businessDayCutoff","taxRate","taxRefundTreatment"} else False
        require(type(value) is type(expected) and value == expected, f"release remains blocked {key}")
    return len(fixed)


def validate_fixtures(f, p):
    require(f["fixtureVersion"] == "s0-fixtures-v1" and f["policyVersion"] == p["policyVersion"] and f["status"] == "FROZEN_DESIGN", "fixture metadata")
    require(f["sourceCompatibility"] == "NONE_IMPLEMENTATION_REQUIRED" and f["currency"] == "VND" and f["moneyUnit"] == "WHOLE_VND_MINOR_UNIT", "fixture design units")
    require(set(f) == set(GROUP_IDS) | {"fixtureVersion","policyVersion","status","sourceCompatibility","currency","moneyUnit"}, "fixture group manifest")
    quotes = {c["id"]: c for c in f["quotes"]}
    count = 0
    for group, ids in GROUP_IDS.items():
        require([c["id"] for c in f[group]] == ids.split(), f"case IDs/order {group}")
        for c in f[group]:
            result = evaluate(group, c, p, quotes)
            require(same_typed(result,c["expected"]), f"{group}/{c['id']}: {result!r} != {c['expected']!r}")
            count += 1
    # Fixed business outcomes independent of JSON expected labels (not just sums).
    require(quote(quotes["hybrid-6m8"]["input"], p) == dict(subtotal=7000000,totalStay=6800000,grossCommission=1050000,netRetention=850000,dueOnline=1360000,balanceDueHost=5440000,platformHeldHostPayable=510000,hostEntitlement=5950000), "approved 6.8m anchor")
    return count


def dto(value, kind, api):
    if kind.endswith("?"):
        return value is None or dto(value, kind[:-1], api)
    if kind.endswith("[]"):
        return type(value) is list and all(dto(v, kind[:-2], api) for v in value)
    if kind in api["dtos"]:
        if type(value) is not dict:
            return False
        fields = api["dtos"][kind]
        optional = set(api["requestRules"]["optionalFields"].get(kind, []))
        return set(fields)-optional <= set(value) <= set(fields) and all(dto(v, fields[k], api) for k, v in value.items())
    if kind in api["enums"]:
        return value in api["enums"][kind]
    if kind == "errorCode":
        return value in api["errors"]
    if kind in {"money","version","positiveInteger","nonnegativeInteger","childAge","rating"}:
        return integer(value, 1 if kind in {"positiveInteger","rating"} else 0) and (kind != "childAge" or value <= 17) and (kind != "rating" or value <= 5)
    if kind in {"VND","HCM"}:
        return value == ("VND" if kind == "VND" else "Asia/Ho_Chi_Minh")
    if kind == "false":
        return value is False
    if kind == "null":
        return value is None
    if kind == "hash":
        return type(value) is str and re.fullmatch(r"[a-f0-9]{64}", value) is not None
    if kind in {"date","instant"}:
        if type(value) is not str:
            return False
        # Uppercase RFC3339 profile; lexical gate before calendar/time parsing.
        pattern = r"[0-9]{4}-[0-9]{2}-[0-9]{2}"
        if kind == "instant":
            pattern += r"T(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9](?:\.[0-9]+)?(?:Z|[+-](?:[01][0-9]|2[0-3]):[0-5][0-9])"
        if re.fullmatch(pattern, value) is None:
            return False
        try:
            parsed = date.fromisoformat(value) if kind == "date" else datetime.fromisoformat(value.replace("Z", "+00:00"))
            return kind == "date" or parsed.tzinfo is not None
        except (ValueError, TypeError):
            return False
    if kind == "positiveDecimalString":
        return type(value) is str and re.fullmatch(r"\d+(\.\d+)?", value) is not None and Decimal(value) > 0
    return kind == "string" and type(value) is str and len(value) > 0


def api_checks(a, p, f):
    require(a["apiVersion"] == "s0-api-v1" and a["policyVersion"] == p["policyVersion"] and a["fixtureVersion"] == f["fixtureVersion"], "API versions")
    require(a["status"] == "FROZEN_DESIGN" and a["sourceCompatibility"] == "NONE_IMPLEMENTATION_REQUIRED", "API design only")
    require(a["enums"]["paymentMode"] == p["collection"]["modes"], "API/policy mode enum")
    # Exercise every declared DTO type and every required field, not only the
    # convenient example Selection/Quote. Samples are structural, not operations.
    def sample(kind):
        if kind.endswith("?"):
            return sample(kind[:-1])
        if kind.endswith("[]"):
            return [sample(kind[:-2])]
        if kind in a["dtos"]:
            return {k:sample(t) for k,t in a["dtos"][kind].items()}
        if kind in a["enums"]:
            return a["enums"][kind][0]
        if kind in {"money","version","positiveInteger","nonnegativeInteger","childAge","rating"}:
            return 1
        return {"VND":"VND","HCM":"Asia/Ho_Chi_Minh","false":False,"null":None,"hash":"a"*64,"date":"2030-12-10","instant":"2030-12-10T00:00:00Z","positiveDecimalString":"1.5","string":"fixture","errorCode":"ERR_INVALID_REQUEST"}[kind]
    for name,fields in a["dtos"].items():
        good = sample(name)
        require(dto(good,name,a), "declared DTO type " + name)
        require(not dto({**good,"injectedUnknownField":1},name,a), "unknown DTO field " + name)
        optional = set(a["requestRules"]["optionalFields"].get(name,[]))
        for key in set(fields)-optional:
            bad = {k:v for k,v in good.items() if k != key}
            require(not dto(bad,name,a), "missing DTO field " + name + "." + key)
    for error,status in {"ERR_SELF_BOOKING":400,"ERR_NONPOSITIVE_BOOKING_AMOUNT":400,"ERR_INVALID_OCCUPANCY":400,"ERR_PAYMENT_CONFIG":409,"ERR_DEPOSIT_COVERAGE":409,"ERR_HOLD_EXPIRED":409,"ERR_REVIEW_WINDOW_CLOSED":409,"ERR_REAL_MONEY_BLOCKED":409}.items():
        require(a["errors"].get(error) == status, "approved error/status " + error)
    selection = a["exampleSelection"]
    require(dto(selection, "Selection", a), "selection DTO including declared currency")
    require(sum(i["quantity"] for i in selection["items"]) == selection["rooms"] == 3, "rooms grain")
    allocations = [r for i in selection["items"] for r in i["roomAllocations"]]
    require(all(len(i["roomAllocations"]) == i["quantity"] for i in selection["items"]), "allocation grain")
    require(sum(r["adults"] for r in allocations) == selection["adults"] == 6, "adult count")
    require(sorted(c for r in allocations for c in r["childrenAges"]) == selection["childrenAges"] == [7], "children allocation")
    qi = next(c for c in f["quotes"] if c["id"] == "hybrid-6m8")
    amounts = a["exampleQuoteAmounts"]
    computed = quote(qi["input"], p)
    require(amounts == {"roomCharges":7000000,"cleaningFee":0,"taxes":0,**{k:computed[k] for k in ("subtotal","discount") if k in computed},"discount":200000,"totalStay":computed["totalStay"],"dueOnline":computed["dueOnline"],"balanceDueHost":computed["balanceDueHost"]}, "API amount projection")
    binding = {k:v for k,v in a["exampleBinding"].items() if not k.endswith("Ref")}
    binding.update(selection=selection, quoteAmounts=amounts)
    require(set(binding) == set(p["consent"]["binding"]), "immutable quote binding fields")
    canonical = lambda x: json.dumps(x, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode()
    digest = hashlib.sha256(canonical(binding)).hexdigest()
    quote_items = copy.deepcopy(selection["items"])
    for item in quote_items:
        price = 1000000 if item["roomTypeId"] == 7 else 1500000
        item.update(availableQuantity=5 if item["roomTypeId"] == 7 else 2,
                    nightlyPerRoom=[dict(date=d,roomCharge=price) for d in ("2030-12-10","2030-12-11")],
                    roomCharges=price*2*item["quantity"])
    require(sum(i["roomCharges"] for i in quote_items) == amounts["roomCharges"], "nightly/item quote sum")
    quote_dto = dict(quoteId="q42-hybrid",policyVersion=p["policyVersion"],fixtureVersion=f["fixtureVersion"],quoteHash=digest,quoteExpiresAt=binding["quoteExpiresAt"],paymentMode="HYBRID_DEPOSIT",allowedPaymentModes=p["collection"]["modes"],currency="VND",items=quote_items,**amounts)
    require(dto(quote_dto, "Quote", a), "Quote DTO")
    consent = dict(quoteId=quote_dto["quoteId"],acceptedPolicyVersion=p["policyVersion"],acceptedFixtureVersion=f["fixtureVersion"],acceptedQuoteHash=digest)
    require(dto(consent, "Consent", a), "consent DTO")
    changed = copy.deepcopy(binding)
    changed["quoteAmounts"]["dueOnline"] += 1
    require(hashlib.sha256(canonical(changed)).hexdigest() != digest, "changed amount must invalidate consent")
    require(not dto({**selection,"dueOnline":1}, "Selection", a), "untrusted client amount rejected, declared currency allowed")
    for e, status in a["errors"].items():
        require(e.startswith("ERR_") and type(status) is int and status in {400,401,403,404,409,500}, "error schema")
        require(dto(dict(success=False,message="safe",data=None,errorCode=e), "Error", a), "error envelope")
    for c in f["quotes"]+f["refunds"]:
        if "errorCode" in c["expected"]:
            require(c["expected"]["errorCode"] in a["errors"], "fixture error is defined")
    require(set(a["providerVerification"]["required"]) == {"signature","merchant","reference","currency","amount","responseCode","transactionStatus"}, "provider verification gates")
    require(a["providerVerification"]["vnpAmountScale"] == 100, "provider wire scaling not Money unit")
    require(not set(a["dtos"]["Quote"]) & set(a["projections"]["neverGuestPublic"]), "no guest settlement leak")
    return len(a["errors"]), len(a["dtos"]), digest


def docs_checks(f, a):
    texts = {n:(ROOT/"docs"/n).read_text() for n in DOCS}
    tracker = texts["tasklist/TaskList.md"]
    tasks = re.findall(r"^- \[([ x])\] \*\*TSK-(\d{3})\*\* (.*)$", tracker, re.M)
    ids = [int(n) for _,n,_ in tasks]
    require(len(ids) == len(set(ids)) == 114 and set(ids) == set(range(1,115)), "114 unique task IDs")
    unchecked = {int(n) for mark,n,_ in tasks if mark == " "}
    planned = set(range(49,53)) | set(range(76,115))
    require(unchecked == planned-S0, "only eight S0 closed, 35 unchecked")
    require(sum(mark == "x" for mark,_,_ in tasks) == 79, "79 checked")
    rows = {}
    for n,sprint,agent,rank,raw in re.findall(r"^\| TSK-(\d{3}) \| (S\d+|POST) \| ([AB]) \| (\d+) \| ([^|]+) \|", tracker, re.M):
        n = int(n)
        require(n not in rows, "duplicate sprint task")
        deps = [] if raw.strip() == "none" else [int(d.strip()[4:]) for d in raw.split(",")]
        rows[n] = (99 if sprint == "POST" else int(sprint[1:]), int(rank), agent, deps)
    require(set(rows) == planned and {n for n,r in rows.items() if r[0] == 0} == S0, "sprint coverage includes completed S0 history")
    for n,(s,r,agent,deps) in rows.items():
        require(len(deps) == len(set(deps)) and n not in deps, "unique deps")
        for d in deps:
            require(d in rows and rows[d][:2] < (s,r), "DAG topological dependency rank")
    for s in range(9):
        ranks = sorted(r[1] for r in rows.values() if r[0] == s)
        require(ranks == list(range(1,len(ranks)+1)), "contiguous sprint ranks")
        summary = re.search(rf"^\| S{s} [^|]+\| (\d+) \|", tracker, re.M)
        require(summary and int(summary[1]) == len(ranks), "sprint summary")
    def ancestors(n):
        return set(rows[n][3]) | set().union(*(ancestors(d) for d in rows[n][3]))
    require(unchecked-{95,96} <= ancestors(95), "final release covers all active tasks")
    remediation = [(m,d) for m,n,d in tasks if int(n)>=76]
    require(Counter(re.search(r"\bP[012]\b",d)[0] for _,d in remediation) == {"P0":22,"P1":16,"P2":1}, "priorities")
    require(sum(m == "x" for m,_ in remediation) == 8 and len(remediation) == 39, "remediation done8/pending31")
    for phrase in ("active done8/42", "S0 done8/8, pending0/8", "| 22 | 16 | 1 | 0 | 31 | 8 |"):
        require(phrase in tracker, "summary " + phrase)
    links = jsons = anchors = 0
    code_json = {}
    for name,text in texts.items():
        fence = None
        body = []
        prose = []
        for line in text.splitlines():
            match = re.match(r"^\s*(`{3,}|~{3,})(.*)$", line)
            if match:
                marker,language = match.groups()
                if fence is None:
                    fence,body = (marker,language.strip()),[]
                else:
                    require(marker[0] == fence[0][0] and len(marker)>=len(fence[0]), "fence " + name)
                    if fence[1] in {"json","jsonc"}:
                        raw = "\n".join(body)
                        if fence[1] == "jsonc":
                            raw = re.sub(r"//[^\n]*", "", raw)
                        parsed = json.loads(raw, object_pairs_hook=unique_object)
                        code_json.setdefault(name,[]).append(parsed)
                        jsons += 1
                    fence = None
            elif fence:
                body.append(line)
            else:
                prose.append(line)
        require(fence is None, "unclosed fence " + name)
        for target in re.findall(r"\[[^\]]*\]\(([^)]+)\)", "\n".join(prose)):
            if re.match(r"[a-zA-Z]+:",target):
                continue
            path,_,fragment = target.partition("#")
            destination = (ROOT/"docs"/name).parent.joinpath(path).resolve() if path else ROOT/"docs"/name
            require(destination.exists(), "link " + name + " " + target)
            if fragment:
                # Anchor validation only inside explicit authorized Markdown manifest.
                relative = str(destination.relative_to(ROOT/"docs"))
                if relative in texts:
                    headings = re.findall(r"^#{1,6} (.+)$", texts[relative], re.M)
                    slugs = {re.sub(r"[^\w\- ]", "", h.lower()).replace(" ", "-") for h in headings}
                    require(fragment in slugs, "anchor " + name + " " + target)
                    anchors += 1
            links += 1
    # Active projections must not silently retain the obsolete service-fee quote.
    for name,end in {"architecture/3_API_Design.md":"## CURRENT/LEGACY", "ui/UXUI_DesignSystem.md":"## BASELINE", "ui/ApplicationDesigned.md":"[Stitch", "database/8_Payment.md":"## CURRENT/LEGACY", "business/PropertyInventoryBusinessRules.md":"## 9."}.items():
        active = texts[name].split(end)[0]
        require(not re.search(r"6900000|6\.900\.000|1\.380\.000|5\.520\.000|1\.035\.000|345\.000|5\.865\.000",active), "stale active fixture " + name)
    for name in ("business/PaymentBusinessRules.md","business/PropertyInventoryBusinessRules.md","business/OverviewBusiness.md","architecture/3_API_Design.md","ui/UXUI_DesignSystem.md","database/8_Payment.md"):
        active = texts[name].split("## CURRENT/LEGACY")[0].split("## BASELINE")[0]
        for stale in ("freeze109 remains pending", "109 freeze not completed", "config109 not complete", "TTL TBD", "all-arrived vs per-room boundary phải PO duyệt", "zero-online exception phải duyệt"):
            require(stale not in active, "stale frozen decision " + name + " " + stale)
    # Check copied Markdown examples against the JSON authority, not only fences.
    worked = code_json["business/PaymentBusinessRules.md"][0]
    require(len(worked) == 4, "four Markdown worked quotes")
    for md,c in zip(worked,f["quotes"][:4]):
        require(md["paymentMode"] == c["input"]["paymentMode"], "Markdown mode projection")
        expected = {**c["expected"], "discount":c["input"]["discount"]}
        require(all(k == "paymentMode" or same_typed(v,expected.get(k)) for k,v in md.items()), "Markdown financial projection")
    api_json = code_json["architecture/3_API_Design.md"]
    require(dto(api_json[0],"Selection",a), "Markdown selection DTO")
    full = next(c for c in f["quotes"] if c["id"] == "full-6m8")["expected"]
    hybrid = next(c for c in f["quotes"] if c["id"] == "hybrid-6m8")["expected"]
    for mode,expected in (("FULL_PREPAY",full),("HYBRID_DEPOSIT",hybrid)):
        excerpt = next(b for b in api_json if type(b) is dict and b.get("quoteId") == ("q42" if mode == "FULL_PREPAY" else "q42-hybrid"))
        require(excerpt["policyVersion"] == "s0-policy-v1" and excerpt["fixtureVersion"] == "s0-fixtures-v1", "Markdown quote versions")
        require(all(excerpt[k] == expected[k] for k in ("totalStay","dueOnline","balanceDueHost")), "Markdown quote amount")
    return len(tasks), links, jsons, anchors


def self_test(p, f):
    caught = 0
    # Mutate every expected case outcome individually; every altered oracle must fail.
    for group in GROUP_IDS:
        for i,c in enumerate(f[group]):
            bad = copy.deepcopy(f)
            expected = bad[group][i]["expected"]
            if type(expected) is bool:
                bad[group][i]["expected"] = not expected
            elif type(expected) is str:
                bad[group][i]["expected"] = "MUTATED"
            elif type(expected) is list:
                expected[0] += 1
            else:
                k = next(iter(expected))
                expected[k] = expected[k]+1 if type(expected[k]) is int else "MUTATED"
            try:
                validate_fixtures(bad,p)
            except ValueError:
                caught += 1
            else:
                raise ValueError("mutation escaped " + c["id"])
    # Mutating critical inputs without updating their expected outcomes must fail
    # as well: exercises arithmetic/branch truth rather than labels alone.
    for group,case_id,key,value in [
        ("attempts","wrong-total-not-O","amount",1360000),
        ("attempts","exact-expiry","elapsedSeconds",899),
        ("occupancy","fraction","childrenAges",[1]),
        ("dates","cutoff","createdAt","2030-12-10T19:59:00+07:00"),
        ("reviews","review-exact-end","elapsedSeconds",1209599),
        ("arrivals","host-tap","acknowledged",True),
        ("completions","early-checkout","secondsAfterScheduledCheckout",0),
        ("caps","race-loser","reservedGlobal",0),
        ("pools","cash-over","request",130000),
    ]:
        bad = copy.deepcopy(f)
        next(c for c in bad[group] if c["id"] == case_id)[key] = value
        try:
            validate_fixtures(bad,p)
        except ValueError:
            caught += 1
        else:
            raise ValueError("input mutation escaped " + case_id)
    for section,key,value in [("money","depositBps",1500),("collection","holdSeconds",901),("clearance","legalApproved",True)]:
        bad = copy.deepcopy(p)
        bad[section][key] = value
        try:
            policy_checks(bad)
        except ValueError:
            caught += 1
        else:
            raise ValueError("policy mutation escaped")
    return caught


def wire_self_test(a):
    """Isolated scalar and nested DTO regression cases; no external services."""
    cases = []
    for kind, good, bad in [
        ("date", ["2030-12-10", "2032-02-29"],
         ["20301210", "2030-W50-2", "2030-02-29", "2032-02-30", "2030-13-01", "0000-01-01", "2030-12-10\n"]),
        ("instant", ["2032-02-29T00:00:00Z", "2030-12-10T23:59:59.123456789+07:00", "2030-12-10T00:00:00-05:30"],
         ["20301210T000000Z", "2030-W50-2T00:00:00Z", "2030-12-10 00:00:00Z", "2030-12-10T00:00:00", "2030-12-10T00:00Z", "2030-02-29T00:00:00Z", "2030-12-10T24:00:00Z", "2030-12-10T00:00:60Z", "2030-12-10T00:00:00+24:00", "2030-12-10T00:00:00+07:60", "2030-12-10t00:00:00z", "2030-12-10T00:00:00Z\n"]),
    ]:
        cases.extend((kind, value, True) for value in good)
        cases.extend((kind, value, False) for value in bad + [None, True, False, 1, 1.5, [], {}])
    for kind, value, expected in cases:
        require(dto(value, kind, a) is expected, f"wire {kind} {value!r}")
    nested = 0
    for value in ["2032-02-29", "20301210", None, True, [], {}]:
        payload = {"date": value, "roomCharge": 1}
        require(dto(payload, "NightlyPrice[]", a) is False, "nested array shape")
        require(dto([payload], "NightlyPrice[]", a) is (value == "2032-02-29"), "nested date scalar")
        nested += 2
    for value in ["2032-02-29T00:00:00Z", "2030-12-10 00:00:00Z", None, True, [], {}]:
        payload = dict(amount=1, currency="VND", method="CASH", paidAt=value,
                       reference="test", evidenceHandle="test", expectedVersion=1)
        require(dto(payload, "DirectReceiptRequest", a) is (value == "2032-02-29T00:00:00Z"), "DTO instant scalar")
        nested += 1
    return len(cases), nested


def main():
    start = time.perf_counter()
    require(sys.argv[1:] in ([],["--self-test"]), "usage: verify_s0_contracts.py [--self-test]")
    p,f,a = [load(n) for n in ("s0-policy-v1.json","s0-fixtures-v1.json","s0-api-v1.json")]
    pins = policy_checks(p)
    cases = validate_fixtures(f,p)
    errors,dtos,digest = api_checks(a,p,f)
    wire, nested = wire_self_test(a) if "--self-test" in sys.argv else (0, 0)
    tasks,links,jsons,anchors = docs_checks(f,a)
    mutations = self_test(p,f) if "--self-test" in sys.argv else 0
    print(f"PASS FROZEN_DESIGN policyPins={pins} fixtureCases={cases} groups={len(GROUP_IDS)} DTOs={dtos} errors={errors} mutationRejections={mutations}")
    print(f"PASS wireScalarCases={wire} nestedDTOCases={nested}")
    print(f"PASS docs={len(DOCS)} JSON/JSONC={jsons} relativeLinks={links} scopedAnchors={anchors}; IDs={tasks} checked79 unchecked35 activeDone8/42 remaining34 optional096; DAG/ranks/S0history")
    print(f"PASS immutable example quoteHash={digest}; exact O not total, funded D, source pools; NOT application/DB/concurrency/legal proof")
    print(f"Duration {time.perf_counter()-start:.3f}s")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, TypeError, IndexError) as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        sys.exit(1)
