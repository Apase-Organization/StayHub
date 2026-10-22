# Sprint 0 decision and delivery log

## Provenance and status

Approval source: user answered all seven policy questionnaire groups approving the proposals on 2026-10-23. User acts as product owner, not legal counsel or a named Finance/Operations signatory. No signatures or real-money permission are inferred.

Decision approval, completed design deliverable and runtime clearance are separate statuses. TSK-079/097/081/084/086/088/090 delivered;109 approval is provenance, not completed config/DTO freeze.086 is completed PRODUCT CONTRACT, NOT legal clearance.

| Task | Decision approval | Deliverable status / version |
|---|---|---|
| TSK-079 | Approved instant, both required modes, required-online authority and exclusions | Completed policy design, S0-079-v1, 2026-10-23 |
| TSK-097 | Approved inventory boundaries | Completed policy design, S0-097-v1, 2026-10-23 |
| TSK-081 | Approved hold/retry/late-payment parameters | Completed policy design, S0-081-v1, 2026-10-23 |
| TSK-084 | Approved cancellation/refund parameters | Completed policy design, S0-084-v1, 2026-10-23 |
| TSK-086 | Approved financial/operations product contract, not Finance/Legal approval | Completed PRODUCT CONTRACT S0-086-v1, 2026-10-23; real-money095 BLOCKED |
| TSK-088 | Approved discount/abuse parameters | Completed policy design S0-088-v1, 2026-10-23 |
| TSK-090 | Approved time/arrival/completion/review parameters | Completed policy design S0-090-v1, 2026-10-23 |
| TSK-109 | Approved numerical choices, not completed config/DTO freeze | Pending dedicated deliverable |

## TSK-090 acceptance evidence

User PO approval frozen **S0-090-v1,2026-10-23** at Inventory3a with Payment/Overview/API/Review projections. HCM UTC+7/14:00checkin/11:00checkout/1..30nights, same-day originalcreation<20 only/retry081 deadline; adults18+,eachroom>=1adult, childinteger0..17/alloccupancy/noextra pricing. Host+Guestack or Admin verified audited case actualevent notfuture/notprebooking with chronology checks, no invented24hbackdate cap. ALLARRIVED maxactual+24h/allgates, no Hosttap/calendar bypass. Completion at/after scheduledcheckout11 plus actualstay/no unresolvedcase; earlycheckout no exception. ActualbookingGuest one review within [completedAt,completedAt+14days), no-show denied, duplicate/idempotency boundaries explicit. Same-day/future/cutoff/nights/ages/adjacency/actual-versus-ack/completion/review boundary examples documented, not runtime proof.

Only090 newly closed:78 checked/36 unchecked, active done7/42, S0 done7/8;109 unchecked. Real-money095 legal/tax/calendar/ops/runtime clearance BLOCKED. Checks `python3 .opencode/verify-dual-payment-docs.py` and `git diff --check`, results in execution handoff. No runtime/source tests; integration/e2e/full skipped docs-only.

## TSK-088 acceptance evidence

User PO questionnaire approval frozen **S0-088-v1, 2026-10-23**, [Payment2b](PaymentBusinessRules.md), [Discount TARGET](../database/10_Discounts.md), [API preview/error projection](../architecture/3_API_Design.md). Self-owner blocked, one code/no stacking, per-user/code1 online consumption, mandatory finite positive campaignquota publication input (no invented unlimited/default value). Atomic global/user reservation with15min original hold; attempt retry failure retains, guarded final-fail/expiry releases, accepted online consumes once, consumed cancellation/refund never restores. DirectB cannot bypass. HALF_UP wholeVND/stable largest remainder, no zeroT/O exception; platformD<=Cgross/Cnet>=0/O>=Cnet, no Guest fee/tax invention. Old15%/zero-exception wording removed or explicitly invalid legacy.

Design examples include last campaign slot/user reservation conflict, duplicate/retry/expiry/consumed cancellation and rounding/zero/funding rejection. Runtime DB concurrency not claimed;089/082/102/110 implementation and109 config/DTO freeze pending. Only088 newly closed:77 checked/37 unchecked, active done6/42, S0 done6/8.090/109 unchecked; real-money095 remains BLOCKED. Validation `python3 .opencode/verify-dual-payment-docs.py`, `git diff --check`; exact results/duration in execution handoff. Full/integration/e2e skipped docs-only, no source changes or fabricated clearance.

## TSK-086 acceptance evidence — completed PRODUCT CONTRACT, NOT legal clearance

User PO questionnaire approval frozen **S0-086-v1, 2026-10-23**: [Payment](PaymentBusinessRules.md) equations/active1m and6.8m fixtures, [Overview](OverviewBusiness.md) financial projection.15% prediscount room+clean excludingtax, no Guest service fee; D platformexpense<=Cgross, Cnet=Cgross−D; T=S−D, hybrid20%T HALF_UP VND/O>=Cnet, HostS−Cgross, heldO−Cnet (fullT−Cnet). Provider fee actual contract platformexpense, not assumed amount. Gross due/funded expense/netretention/reserve/earned distinct; conservative liabilities until completed/resolved obligations and reviewed earning basis, no deposit revenue/legal-accounting invention. ALLARRIVED max(actualAt)+24h/all gates, no per-room early payout.

OperationsFinance daily reconciliation/reserve/liquidity/recovery; different-human Admin maker/checker/bank proof, Host/Guest/Admin case evidence and post-payout funded recovery responsibilities documented as roles, not fictitious people/signatures. **095 real money LOCKED/BLOCKED**: actual contracts/legal permission to collect/hold/transfer, signedops/makerchecker/calendar/holiday/tax/liquidity/runtime evidence absent. PO approval is not Finance/Legal approval; tax unset/disabled. Config/DTO/fixture-wide109 not completed; old6.9m API/DB/UI examples explicitly legacy pending109, not active offers.

Only086 newly closed:76 checked/38 unchecked; active done5/42, S0 done5/8.088/090/109 unchecked. Validation: `python3 .opencode/verify-dual-payment-docs.py` (explicit docs, active gross/net/funded fixtures plus legacy arithmetic distinguished) and `git diff --check`; results in execution handoff. Runtime/integration/e2e skipped docs-only; no source changes or legal clearance claimed.

## TSK-079 acceptance evidence

Canonical [S0-079-v1 matrix](PaymentBusinessRules.md#1a-approved-contract-matrix--s0-079-v1) records mode × required-online/direct balance, state × authorized actor × collection, immutable version/consent expectations, read-only return and exact server attempt O with active-hold guard. No Host accept/reject, monthly commission collection or onsite StayHub QR. Full and hybrid confirmation are equally mandatory; attested direct receipts/refund/payout states are not conflated with provider SUCCESS.

TaskList closes only this policy deliverable; TSK-043/044/071–073 remain historical and require runtime revalidation. Config/earning/legal detail stays with TSK-109/086, lifecycle with TSK-081, refund with TSK-084 and arrival/completion with TSK-090. Earlier numerical fixtures/proposal wording in other sections are not approved config and remain staged for those tasks; no downstream implementation claim.

Validation command: `python3 .opencode/verify-dual-payment-docs.py` (stdlib explicit document manifest; IDs/counts, DAG/ranks, fences/JSON/links and illustrative amount boundaries). PASS on 2026-10-23 in 0.350s: 114 IDs, 72 checked/42 unchecked, active done1/42, S0 retained8 with only079 completed, 21 documents, 11 JSON +1 JSONC blocks, 76 relative links; no production runtime proof. `git diff --check` PASS. Validator incrementally updated for completed S0 history and honest counts; `.opencode` validator is ignored/local, not a tracked deliverable. No dummy RED/GREEN for docs. Integration/e2e/full suites skipped: docs-only scope. Real-money use blocked on legal/contracts, signed human operations evidence, source implementation and TSK-095 release approval.

## TSK-097 acceptance evidence

User PO questionnaire approval frozen on 2026-10-23 as **S0-097-v1** in [canonical Inventory](PropertyInventoryBusinessRules.md), with targeted [Overview](OverviewBusiness.md), [API](../architecture/3_API_Design.md) and [ERD](../database/3_CoreERD.md) projections. Approved boundaries: exclusive WHOLE_PLACE/ROOM_BASED without linked sale; truly interchangeable pooled RoomTypes and shared-plan stock; PhysicalRoom assignment future; whole-place one type quantity1 with descriptive bedrooms; one property/date range/VND per booking; whole-booking cancellation only, not partial items/quantities. Financial partial refund is distinct and remains TSK-084's deliverable.

Only097 newly completed; cumulative policy designs079/097, S0 done2/8, active done2/42, 73 checked/41 unchecked across114 IDs. No schema/source/runtime/capacity or migration correctness claimed. Existing financial fixture replacement remains staged for109, not changed or commercially approved by097. Remaining downstream protections require099/102/106/107 and release095.

Checks: `python3 .opencode/verify-dual-payment-docs.py` PASS (2026-10-23, 0.341s): 114 IDs/73 checked/41 unchecked, policy done2, DAG/ranks/retained S0 history, S0-097-v1 projections, 21 documents/11 JSON/1 JSONC/84 links and illustrative financial boundaries. `git diff --check` PASS. Validator uses an explicit document manifest, no suite-wide runner; ignored local validator incrementally updated. Payment precedence wording also updated to reference the independent inventory approval, without changing financial policy. Runtime/integration/e2e skipped for docs-only scope; no dummy RED/GREEN. No business approval blocker for097; implementation and real-money gates remain blocked independently.

## TSK-081 acceptance evidence

User PO questionnaire approval frozen **S0-081-v1**, 2026-10-23, in canonical Payment section3a and Inventory/API projections. Approved15min original-created deadline, max3 creations inclusivefirst, no extension, active strictly before expiry. Failure callback updates attempt only; retry within window/count, count3 pending can settle; worker alone early-finalizes all3 terminal unsuccessful/no credit or finalizes expiry. Allocation/IPN/worker serialize, single required booking credit and atomic all-item/cap convert/release. Late/extra real money retained in ledger/reconcile/refund case owned OperationsFinance, Admin maker/checker proof execution, no stock resurrection/automatic refund PAID.

Contract review examples cover retry success, exact expiry rejection, pending third attempt, all-terminal final failure and duplicate/extra receipts. These are design examples, not runtime tests. Only081 newly closed: 74 checked/40 unchecked, active done3/42, S0 done3/8. Numeric081 policy is approved, no outstanding PO blocker. Runtime082/083/102/110/114 and release095 remain blocked independently. Checks: `python3 .opencode/verify-dual-payment-docs.py`, `git diff --check`; execution result/duration in handoff. Full/integration/e2e skipped for docs-only scope; no dummy RED/GREEN.

## TSK-084 acceptance evidence

User PO questionnaire approval frozen **S0-084-v1**, 2026-10-23, canonical Payment section5a with Inventory/Overview/API projections. Numeric >=48h boundary/full actual collected refund; late/no-show room nonrefundable but collected net unused cleaning refundable absent actual check-in; Host inability full collected; after-arrival incidents Admin case. Whole-booking cancellation remains097. Online/direct source pools and policy/paid/reserved guards, immutable discount/funding allocations and retained/uncollected disclosure prevent double return or fictional cash. No automatic B debit/monthly invoice. Platform SLA5–10businessdays from eligible request, no Host same-SLA promise without contract.

Worked examples cover full/hybrid/free-window, partial direct receipts, late-cleaning proportional discount/source allocations, policy-limited versus cash-limited refund boundaries. Commercial rate/fee/config freeze109 not completed here. Tax treatment has no legal approval; unset/disabled config and reviewed tax/calendar/holiday/operations clearance block paid activation095. Only084 newly closed:75 checked/39 unchecked, active done4/42, S0 done4/8; remaining four S0 tasks unchecked. `python3 .opencode/verify-dual-payment-docs.py` PASS 2026-10-23 in0.432s:21 docs,11 JSON/1 JSONC,90 relative links, IDs/counts/DAG/ranks/version projections and illustrative refund/source conservation. `git diff --check` PASS. Ignored local validator incrementally adapted. No runtime/source changes, full/integration/e2e skipped for docs-only scope; policy approval not legal or implementation evidence.
