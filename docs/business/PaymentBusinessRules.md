# Payment — canonical TARGET v2 (2026-10-23)

**S0-109-v1 / s0-policy-v1 — FROZEN_DESIGN, sourceCompatibility NONE_IMPLEMENTATION_REQUIRED.** [Policy](contracts/s0-policy-v1.json), [fixtures](contracts/s0-fixtures-v1.json), [typed API/errors](contracts/s0-api-v1.json), [stdlib verification](contracts/verify_s0_contracts.py) are the frozen design inputs; [S0 handoff](S0DecisionLog.md#s0-final-handoff--tsk-109) records evidence/owners/limits. All eight S0 product decisions are frozen; implementation and real-money095 remain BLOCKED. Whole-VND integer Money,20% postdiscount deposit/15% prediscount room+clean commission, platform-funded D and O>=Cnet are not proposals. Quote expiry is explicit server quoteExpiresAt, distinct from original booking createdAt+900s hold; no quote TTL commercial value is invented.

**Approved direction, pending delivery.** Cả `FULL_PREPAY` và `HYBRID_DEPOSIT` là **core MVP bắt buộc**, cùng multi-room. Đây là thiết kế, không chứng nhận source/schema/runtime đã đạt. Không dùng sandbox, screenshot hay checkbox lịch sử làm evidence tiền thật. Inventory theo [PropertyInventoryBusinessRules](PropertyInventoryBusinessRules.md); contracts tại [API](../architecture/3_API_Design.md), [Payment data](../database/8_Payment.md); tiến độ/evidence tại [TaskList](../tasklist/TaskList.md).

## 1. Quyết định đã duyệt và thứ tự ưu tiên

| Decision | Đã duyệt | Chưa chốt / gate |
|---|---|---|
| PAY-01 | FULL_PREPAY:100% T online |109 frozen design; reviewed tax/provider/earning permissions and runtime095 blocked |
| PAY-02 | HYBRID_DEPOSIT:20% postdiscountT HALF_UP VND; B Host cash/bank at check-in, O>=Cnet |109 config/DTO freeze complete, not runtime activation |
| PAY-03 | Instant after verified exact attempt O +active all-item hold, no Host accept/reject |079/081/109 frozen;082 runtime pending |
| PAY-04 | ALLARRIVED max(actualAt)+24h earliest eligibility/all gates |086/090 design frozen;112 runtime/095 legal and ops clearance blocked |
| PAY-05 | Không free unpaid reservation, không thu commission hằng tháng, không StayHub QR pass-through tại chỗ | Không tạo hidden monthly commission receivable; coverage không đủ phải reject/requote/disable offer |

Quyết định user đã duyệt → decision log và inventory → API/ERD/UI projections → task AC/evidence. Legacy chỉ để trace; khi lệch phải sửa cùng contract version trước triển khai. Inventory boundaries có explicit questionnaire approval riêng, frozen **S0-097-v1** tại inventory canonical; không suy từ payment approval và không chứng nhận implementation. Không mặc định escrow hợp pháp/bắt buộc, zero fraud hoặc tỷ lệ rủi ro giả định.

## 1a. Approved contract matrix — S0-079-v1

Approved2026-10-23 by user as PO through questionnaire, recorded in [S0DecisionLog](S0DecisionLog.md). Versioned product approval, not signature/legal/runtime certification.109 now consolidates all approved design parameters and active fixtures; historical proposal wording is superseded, not permission to activate funds.

| Mode | Server quote / required online O | Direct balance B | Confirmation |
|---|---|---|---|
| FULL_PREPAY | O = contractual total T | B = 0 | Instant only after verified exact O and active all-item hold |
| HYBRID_DEPOSIT | Mandatory O from server-computed versioned config; O + B = T | B = T − O, paid directly to Host cash/bank at check-in | Same instant guard; does not wait for B |

Both modes required MVP; Guest selects only server-allowed plan/mode, Host owned offers only within frozen config, never waive O/accept/reject. Snapshot policyVersion/fixtureVersion/quoteHash, mode/T/O/B/currency/allocations/consent. Attempt expected O server-bound to booking/ref/currency/config, not client amount or hybrid T.109 frozen design complete; tax/provider/legal operational evidence still blocked095. No free unpaid reservation/monthly commission/onsite StayHub QR.

| Booking state / event | Authorized actor and guard | Collection / resulting state |
|---|---|---|
| New → PENDING_PAYMENT | Authenticated Guest for own booking; valid server quote and atomic all-item hold | ONLINE_DUE; no confirmation from submit |
| PENDING_PAYMENT → CONFIRMED | Verified provider IPN service only: signature, merchant, reference, currency, envelope/result and exact attempt O; active hold under serialized expiry/credit transaction | ONLINE_PAID_VERIFIED; convert all holds once; full B=0, hybrid DIRECT_OUTSTANDING |
| Return / polling, any state | Authorized Guest/Host/support read scope; browser signature is not collection authority | Read-only; no booking/ledger mutation |
| PENDING_PAYMENT → CANCELLED on expiry/final failure | Expiry worker under approved S0-081-v1 guards; failure callback updates attempt only | Release once; never kill another pending attempt |
| Duplicate / late / extra success | Verified IPN service, unique event/attempt and booking credit guard | Reconcile/refund real received money; no second credit or resurrection of CANCELLED/COMPLETED |
| CONFIRMED, direct receipt / arrival | Owning Host attests receipt/evidence; Guest independently acknowledges or contests; Admin resolves evidenced case | DIRECT_PARTIAL_ATTESTED / DIRECT_PAID_ATTESTED remain distinct from provider verification; no automatic completion/payout |
| PENDING_PAYMENT / CONFIRMED → CANCELLED | Guest owns booking within policy; Host inability is an evidenced support/Admin case, not Host reject; Admin scoped authority and audit | Cancellation does not imply refund PAID; responsibility follows actual collection source; detailed matrix TSK-084 |
| CONFIRMED → COMPLETED | Guarded service with actual-stay/date/evidence and unresolved-case checks; actor/evidence details TSK-090/092 | Neither online SUCCESS, direct receipt nor calendar alone completes stay |
| Refund REQUESTED → PAID / FAILED | Scoped Admin maker proposes; different human checker authorizes; proof/reference and pool/liquidity guards | Independent refund state; never inferred from CANCELLED |
| Payout BLOCKED → ELIGIBLE → PAID / FAILED | Eligibility service evaluates actual check-in +24h and all gates; OperationsFinance reconciles; Admin maker/checker are different humans, proof required | Independent payout state; no automatic transfer, no direct B paid twice; detailed design TSK-086/090 |

All mutations require authentication/ownership or dedicated service authority, expected version/idempotency and audit; roles are not interchangeable. Host receipt is not IPN, Guest acknowledgment is not bank proof, and maker cannot check their own transfer. Runtime and real-money release remain blocked pending implementation evidence and TSK-095 contractual/legal/operations clearance.

## 2. Amounts, funding và invariants

Tất cả số tiền exact decimal/minor currency units, VND rounding theo policy snapshot; không float. Mỗi fee/tax có tên, scope, inclusion, beneficiary, commission eligibility và refundability. Commission là khoản khấu trừ từ entitlement theo hợp đồng, **không tự cộng thêm lên tổng Guest** hay đếm thêm legacy guest service fee.

```text
S = room charges + cleaning + named reviewed taxes   # NO guest service fee
Cgross = HALF_UP_VND(0.15 * (room charges + cleaning)) # prediscount, tax excluded
0 <= D <= Cgross                  # platform-funded discount expense, allocated once
Cnet = Cgross - D >= 0            # net retention before provider expense, not earned revenue
T = S - D                         # Guest total
O = dueOnline; B = balanceDueHost; O + B = T
FULL_PREPAY: O = T; B = 0
HYBRID_DEPOSIT: O = HALF_UP_VND(0.20 * T); B = T - O; require O >= Cnet
H = S - Cgross                    # Host entitlement including separately identified tax liability
platformHeldHostPayable = O - Cnet # full T-Cnet; hybrid O-Cnet
H = platformHeldHostPayable + B
T = H + Cnet; S = H + Cgross       # before separate tax disposition, provider expense and adjustments
```

**S0-086-v1 product contract /109 frozen design**, not Finance/Legal approval. D platform-funded: gross commission due Cgross/funded expenseD/netretentionCnet separately; never Cnet as gross revenue or D as Guest cash. Provider fees actual contractual platform expense, not deducted again from Host, no assumed profit. Tax not commission base/platform revenue; segregate named tax liability, config unset/disabled and paid activation blocked095. JSON109 freeze complete; evidence to enable money absent.

At quote/reservation and adjustments, use immutable allocations and **hybrid coverage O>=Cnet, not O>=Cgross**. Reject/requote invalid funding/coverage with consent; never silently increase20%, create debt/monthly commission or remove hybrid from MVP. Append adjustment/reversal for refunds, never rewrite history; post-payout deficit becomes funded recovery case. `commissionDue`, reserved cash, earned revenue, Host held payable and direct attestation remain separate. Until stay COMPLETED and refund/dispute/reconcile obligations resolved, retain liabilities/reserves; any earning recognition still requires documented accounting/contract/legal review. Deposit/IPN/+24h eligibility alone never earns revenue; this is a conservative product reserve guard, not invented legal accounting clearance.

### Active S0-086-v1 worked fixtures — approved numeric product choices, not runtime config

Tax/provider cost0 illustration, room charges1.000.000, cleaning0/D0: Cgross=Cnet150.000, T1.000.000, Host850.000. Both modes below follow approved20%/15%, not permission to activate funds:

```json
[
  {"paymentMode":"FULL_PREPAY","totalStay":1000000,"dueOnline":1000000,"balanceDueHost":0,"grossCommission":150000,"netRetention":150000,"platformHeldHostPayable":850000,"hostEntitlement":850000},
  {"paymentMode":"HYBRID_DEPOSIT","totalStay":1000000,"dueOnline":200000,"balanceDueHost":800000,"grossCommission":150000,"netRetention":150000,"platformHeldHostPayable":50000,"hostEntitlement":850000},
  {"paymentMode":"FULL_PREPAY","subtotal":7000000,"discount":200000,"totalStay":6800000,"dueOnline":6800000,"balanceDueHost":0,"grossCommission":1050000,"netRetention":850000,"platformHeldHostPayable":5950000,"hostEntitlement":5950000},
  {"paymentMode":"HYBRID_DEPOSIT","subtotal":7000000,"discount":200000,"totalStay":6800000,"dueOnline":1360000,"balanceDueHost":5440000,"grossCommission":1050000,"netRetention":850000,"platformHeldHostPayable":510000,"hostEntitlement":5950000}
]
```

Hybrid: Guest 200.000 online +800.000 trực tiếp; deposit phân bổ 150.000 commission reserve +50.000 host payable. Host net 800.000+50.000=850.000; platform commission 150.000 **trước refund/provider costs**, không phải toàn deposit. Full: Host850.000/platform150.000, không thu thêm800.000 trực tiếp.

Allocation-only edge (not bookable offer): charges `[1,1,1]`, D1 → stable largest remainder `[1,0,0]`, net `[0,1,1]`. Zero base never divides by0, coded checkout rejects. **LEGACY INVALID config:** old15% deposit/160k commission-on-1m illustration is not active policy. Approved20%/15%, D<=Cgross and O>=Cnet apply; invalid coverage/funding rejects without debt/rate increase.

**Active multi-room fixture:** S7.000.000/D200.000/Cgross1.050.000/Cnet850.000/T6.800.000. HybridO1.360.000/B5.440.000/held510.000; fullO6.800.000/B0/held5.950.000; Host5.950.000 both. Cgross−fundedD=retained850.000 before providerexpense, not earned. **LEGACY INVALID for active offers:** old6.9m service-fee/Host-funded/adjusted-commission fixture retained only in historical decision evidence; all active API/DB/UI projections now s0-fixtures-v1.

### 2a. Operations and clearance — completed PRODUCT CONTRACT, legal clearance BLOCKED

Verified arrival for **every booked room**, earliest `max(actualAt)+24h`, not per-room early payout; Host evidence+Guestack or Admin audited verification, actual time not ack/Hosttap.090 design frozen,112 implementation pending. No-show/partial/contested require case review; dispute/refund/reconcile/liquidity/legal gates remain, never guaranteed transfer.

| Role (not named person) | Approved design responsibility / required evidence |
|---|---|
| OperationsFinance | Daily provider/bank/ledger/direct-memo reconciliation; exception owner/amount/source/reference/status; liquidity and refund reserve review, failed-transfer escalation and post-payout recovery |
| Admin maker | Prepare entitlement-scoped refund/payout with version/key, reconciled balances, reserve and evidence; cannot authorize own payment |
| Admin checker, different human | Independently recheck gates/beneficiary/amount/proof; authorize manual transfer, record bank reference/proof before PAID; pending/failed is not paid |
| Host / Guest / Admin case reviewer | Host attests direct cash and actual events, Guest acknowledges/contests; reviewer resolves evidence/disputes, never treats direct as platform cash |
| Finance/Legal authorized reviewers | Supply actual contracts/permission to collect, hold and transfer; earning/tax/provider terms and reserve/liquidity signoff, not supplied by PO or these docs |
| Release authority095 | Verify signed human operations assignments, maker/checker separation, authoritative calendar/holiday source, tax/legal/contracts/liquidity and runtime evidence; otherwise NO-GO |

Before payout freeze disputed entitlement/reserves. After payout open immutable recovery case linking original payment/refund, affected source/amount, responsible role, reserve/funding, owner/status/deadline/escalation and resolution evidence. Fund required refund liquidity or escalate manually; no negative pool, automatic bank clawback or monthly commission invoice. Keep recovery unresolved/block further affected payouts until reviewed disposition; do not invent contractual right to debit Host. Maker/checker proof applies to recovery transfers too. **Real money remains LOCKED / TSK-095 BLOCKED** until documented permissions, signed operations, tax/calendar/liquidity and implementation evidence exist; no Finance/Legal signatures or escrow legality inferred.

### 2b. Approved abuse / discount — S0-088-v1

User PO questionnaire approval frozen **2026-10-23**. Server blocks `guestId == property.hostId` regardless of role/mode/code/direct receipt. Zero or one code per booking, **no stacking**. Per authenticated user per code **one accepted online consumption**, not per room/item/attempt or direct B. Campaign publication requires explicit positive finite integer quota Q and valid numeric type/value/base/window configuration; no invented quota or NULL=unlimited. Tax config remains unset/disabled/release-blocked, no invented tax eligibility/value.

Under booking/campaign/user locks and unique reservation/consumption constraints enforce `consumedGlobal + activeReservedGlobal <= Q` and `consumedUser + activeReservedUser <= 1`. Preview is read-only, reserves nothing. Checkout revalidates then reserves cap with all-item hold or rolls back all. Reservation booking+code+user expires at original createdAt+15min, retries reuse/no extension; same key/body replay no new reservation, changed body conflicts. Expired cap reusable only after serialized RELEASED transition, not read-clock subtraction. Concurrent last-slot requests cannot both win; same user cannot reserve same code on competing bookings.

RESERVED→CONSUMED only with first accepted exact online collection +active hold/CONFIRMED transaction, decrement reserved/increment consumed once. Single attempt failure retains reservation for081 retry; guarded booking final failure/expiry/pre-consumption cancellation releases once. Duplicate/late/extra IPN never consumes again/revives release. CONSUMED quota/user eligibility **not restored on booking cancellation/refund**. Direct B receipt never consumes or unlocks code; usage reversals preserve audit, not silent counter reset.

Compute eligible booking-wide D once in exact decimal, HALF_UP to whole VND, apply configured percent campaign cap before funding validation. Require D<=eligible base and D<=Cgross, Cnet=Cgross−D>=0; platform-funded only, no Guest fee. Reject misfit, never silently clamp to commission or shift funder to Host. T=S−D, hybridO=HALF_UP(20%T); reject **T<=0 or O<=0**, no free/internal-settlement exception or gateway0. Require O>=Cnet. Allocate rounded D by largest remainder with stable item/room/night/charge ordinal ties, exact sumD/no negative charge; base0 rejects coded checkout, never division0. Snapshot amount/base/funder/algorithm/config version and immutable allocations.

Validation semantics preview/submit:400 `ERR_SELF_BOOKING`, `ERR_DISCOUNT_STACKING`, `ERR_DISCOUNT_INVALID` (unknown/inactive/window/minimum/base/value), `ERR_NONPOSITIVE_BOOKING_AMOUNT` (T/O<=0).409 `ERR_DISCOUNT_QUOTA_EXHAUSTED`, `ERR_DISCOUNT_USER_LIMIT`, `ERR_DISCOUNT_FUNDING` (D>base/Cgross or Cnet<0), `ERR_DISCOUNT_CONFIG` (missing/nonfinite quota/config), `ERR_DEPOSIT_COVERAGE` (O<Cnet), `ERR_QUOTE_STALE`/version/idempotency. Rejection no stock/cap/charge; race loser conflicts. Typed109 design frozen; runtime089/082/102/110 pending.

Design cases, not runtime evidence: illustrative campaignQ1 → one of two simultaneous reservations wins; same user's competing booking cannot bypass held/consumed slot. Attempt1 fail retains; worker expiry releases; online accepted consumes once and cancellation does not restore. HALF_UP0.5→1VND; equal remainder ties lowest ordinal. T1VND hybridO0 rejects before gateway; D>Cgross rejects with positiveT; O<Cnet independently rejects.

## 3. Lifecycle và nguồn xác nhận

| Event | Booking / collection / inventory |
|---|---|
| Submit valid server quote | Snapshot mode/allowed modes/contract/rate/base/rounding/policy/funding; PENDING_PAYMENT, hold tất cả items/nights/caps atomically |
| Verified online đủ O, active hold | CONFIRMED instant; convert mọi hold, consume cap một lần, outbox sau commit. Hybrid vẫn còn B trực tiếp, **không chờ B để confirm**, không để hold đến ngày stay |
| Return/poll | Read-only kể cả browser query có signature; SUCCESS của attempt chỉ nói online đã nhận, không nói cả booking fully paid |
| Failed/expired/duplicate/late attempt | Expiry và IPN serialize. Unique attempt/ref/event, no double charge/consume/email. Late/extra money ghi reconciliation/refund, không hồi sinh stock đã release |
| Actual arrival / direct payment | Per booked-room allocation evidence, timestamps/actors; ghi direct receipt riêng, không provider verify. Không tự đổi finance/completion từ ngày lịch |
| Balance partial/refused/disputed | Outstanding và case/support rõ; không auto-release occupied rooms, không host button đánh dấu cả booking fully verified |
| Completion/no-show/cancel | Guard actor/date/evidence theo policy riêng. Không suy no-show deposit nonrefundable; cancellation không đồng nghĩa refund paid |

Online attempts bind reference/expected O/currency/config; serialize one accepted booking collection, extra real money reconcile/refund, no cancelled-booking overwrite. **S0-088-v1 rejects zero-total/zero-online**, no free/internal-settlement exception or gateway amount0; mandatory hybrid deposit cannot be bypassed.

### 3a. Approved hold / retry / expiry — S0-081-v1

User PO questionnaire approval frozen **2026-10-23**, provenance [S0DecisionLog](S0DecisionLog.md). Numeric policy is approved, not TBD: `expiresAt = original booking createdAt + 15 minutes`; active only `t < expiresAt`. Evaluate authoritative transaction time after lifecycle lock acquisition, not browser/provider paidAt or earlier callback arrival. At `t >= expiresAt`, confirmation is forbidden even before worker runs. One deadline covers all items/nights/cap reservations; refresh/retry/provider timeout never extends it.

**Maximum 3 checkout-creation attempts inclusive of the first.** Allocate ordinal1..3 atomically before provider request; allocated creation failure/timeout still counts. Same operation/key/body retry returns same attempt/outcome, no new ordinal/deadline; concurrent new keys cannot allocate ordinal4. Bind immutable exact server expected O/currency/merchant/reference/config/deadline. No partial-online installment aggregation; wrong/short amount is reconciliation-only.

| Event / guard | Attempt / booking / hold disposition |
|---|---|
| Attempt FAILED/CANCELLED, count<3, t<expiresAt | Callback updates its attempt only; preserve PENDING_PAYMENT and all holds/caps; Guest may create next attempt within original window |
| count=3, any allocated attempt pending, t<expiresAt | No new creation; existing pending attempt may settle before original expiry; count limit is not cancellation |
| count=3, all allocated attempts terminal unsuccessful, no accepted collection | Expiry worker alone may finalize FINAL_PAYMENT_FAILURE early after rechecking all attempts under lock; CANCELLED, release every hold/cap once. Failure callback never kills another pending attempt |
| t>=expiresAt, no accepted collection | Worker finalizes HOLD_EXPIRED/CANCELLED and atomic release once regardless of count; mutating IPN enforces same expiry guard before credit/stock reuse |
| Verified exact O, t<expiresAt, PENDING_PAYMENT, all holds active, no prior booking credit | First qualifying success wins atomic booking credit +all-item conversion +cap consume +outbox, CONFIRMED in both modes |
| Duplicate/out-of-order failure after winner | Audit idempotently; never downgrade SUCCESS/CONFIRMED or release converted stock |
| Late success after expiry/final failure/cancellation, distinct extra success after winner | Persist unique real-money receipt and reconcile/refund case; no required-collection credit, cap consume, confirmation email or resurrection |

Serialize allocation, IPN, worker finalization and cancellation on same booking/lifecycle lock then stable inventory/cap locks. Failure callbacks update only their attempt, never finalize booking failure; this overrides the broader callback wording in section1a. Success winning before deadline makes expiry a no-op for confirmed stock; finalization winning makes later success reconciliation-only. Unique provider transaction/event prevents duplicate receipt posting; one accepted booking collection prevents alternate retry references double-crediting O. Conflicting terminal provider events are retained/audited and actual money reconciled, not silently dropped or success overwritten.

OperationsFinance owns daily late/extra/mismatched receipt reconciliation and refund-case routing with amount/source/reference/owner/status/audit; Admin maker/checker handles execution under084/085 pool/proof guards. No automatic reversal or refund PAID from request. Resold stock is never reclaimed; new booking needs new quote/consent, not resurrection/extension. Implementation/race evidence remains082/083/102/110/114.

Contract cases (not runtime tests): created10:00 → expiry10:15; attempt1 fails10:02 → attempt2 allowed with unchanged deadline; exact O at10:14:59 can confirm, at10:15:00 reconciles only. Three creations with attempt3 pending cannot final-fail; all three terminal unsuccessful permit worker finalization once. Duplicate winner posts once; distinct second success records extra money/case, not second credit.

## 4. Direct receipt và actual check-in evidence

Host có ownership chỉ **attest** tiền cash/bank họ nhận: amount/currency/method, paidAt, recordedAt, actor, reference, evidence handle, idempotency key/body hash, expectedVersion. Không lưu/expose bank credentials hay PII nhạy cảm trong API/logs; evidence private, audited, quyền support tối thiểu. Amount dương, partial receipts cộng dồn tới B; overpayment/conflicting evidence vào case, không xác minh tự động. Correction bằng reversal tham chiếu receipt gốc, không xóa audit. Guest acknowledgment/dispute độc lập; không biến attestation thành VNPay event.

**S0-090-v1/109 frozen evidence design:** Inventory3a per-room actualAt notfuture/notprebooking, chronology/private audit/version/key, no invented maxbackdate. Host+Guestack or Admin audited verification, no Hosttap; ALLARRIVED maxactual+24/allgates. Completion at/after checkout11+actualstay/no unresolvedcase, notearlycheckout; actualGuest review once [completedAt,+14days).112/092 runtime/095 clearance pending;109 freeze complete.

## 5. Refund, dispute và payout

- Cancellation policy snapshot xác định **totalPolicyRefund** sau fee/tax/discount allocation và adjustment commission; không mặc định deposit mất khi no-show. Tách `platformRefundable`, `hostDirectRefundResponsibility` và từng bên đã trả. Host direct được attested/refunded bằng evidence riêng, không giả StayHub hoàn tiền chưa thu.
- Platform refund guard đồng thời: `newRefund <= totalPolicyRefundRemaining` và `newRefund <= onlineCollectedVerified - onlineRefundedPaid - onlineRefundReserved`. Đối soát available held liquidity sau payouts/reserves; đủ quyền hoàn không đồng nghĩa đủ tiền có sẵn. Nếu đã payout phải fund recovery trước hoặc case/manual escalation, không trả số âm hay báo paid khi chỉ requested.
- Manual platform refund qua email/admin: approved **5–10 business days from eligible request**, theo S0-084-v1; calendar/holiday source và operational clearance là release gates, không bịa đã ký. REQUESTED → APPROVED/PROCESSING → PAID hoặc FAILED, maker/checker khác người, reference/proof/audit mới confirm PAID. Retry giữ reserve idempotently; failed release có kiểm soát; không claim automatic gateway refund.
- Payout states BLOCKED → ELIGIBLE → APPROVED/PROCESSING → PAID/FAILED. +24h chỉ eligibility earliest theo actual evidence; phải reconcile verified online collection, entitlement, commission adjustment, refund/dispute reserves, liquidity và legal approval. Manual payout proof/audit/maker-checker, không guaranteed automatic transfer và không trả cả T trong hybrid (B đã trả Host).
- Dispute trước payout freeze applicable entitlement; sau payout append reserve/clawback/manual recovery case với owner, amount, evidence, status và liability disposition; không che mismatch bằng doanh thu âm hay mở commission invoice hằng tháng.

Refund pool fixture (hybrid trên, giả sử policy full refund và Host đã nhận B): totalPolicyRefund1.000.000, platform share200.000, Host responsibility800.000. Sau platform paid50.000 và reserved20.000, platform pool còn130.000: refund130.000 hợp lệ,130.001 bị reject; Host800.000 vẫn không trở thành platform cash. Mọi policy refund và availability sau payout phải đối soát riêng.

### 5a. Approved cancellation / refund — S0-084-v1

User PO questionnaire approval frozen **2026-10-23**, provenance [S0DecisionLog](S0DecisionLog.md). Both modes use the same policy, whole-booking cancellation only (S0-097-v1). Compute hours as elapsed instants from immutable scheduled check-in snapshot to server-recorded cancellation request; equality48h is free window, not a date-only comparison. Property timezone/scheduled-time contract belongs to090; no new cutoff/holiday convention is invented here.

| Actor / circumstance | Approved money outcome / authority |
|---|---|
| Owning Guest cancels >=48 hours before scheduled check-in | Full refund of **all Guest money actually collected**, online and actual Host direct sources; never uncollected B, no commission/provider-cost deduction from this full refund |
| Guest late cancellation (<48h) / no-show without actual check-in | Room charges nonrefundable; refund unused cleaning **after allocated discount**, only to extent Guest funded/collected that charge. No-show proves neither arrival nor payout eligibility |
| Host cannot provide stay before actual check-in | Full collected-money refund by source; Host reports inability, Admin records case/cancellation. No Host reject button. Legacy host-rejection cases route here, not retroactive invented penalty terms |
| Incident after actual check-in, partial arrival or contested service/evidence | Admin case with Guest/Host evidence; no automatic full refund or unused-cleaning assumption. Decision/amount/source/audit required; freeze disputed entitlement pending resolution |
| Admin | Applies snapshot/evidenced case, cannot invent Guest cash or mark refund PAID from booking CANCELLED |

**Charge/funding conservation:** snapshot named gross room/cleaning, allocated platformD, net Guest charge and actual source. Allocate roundedD once largest remainder/stable item-room-night-charge ordinal; each actual receipt allocated across remaining netcharges with same algorithm, never exceed line remaining. Preserve platform-funded expense/reversal, not Guest cash/refund; never grosscleaning+discount return/uncollected refund. Append obligation/commission/funding adjustments, no automatic earnings/history mutation.086/109 product config frozen; earning/legal evidence still blocked095.

**Retained / outstanding disclosure:** late/no-show retained room component and refundable unused-cleaning component must be shown separately, with already-collected retained room money and uncollected room balance identified. The room component is nonrefundable under this policy, but uncollected B is not a refund pool or permission to debit Guest. No automatic B charge, new monthly commission receivable or invented collection/enforcement terms; unresolved amounts/claims route to case review. Full-refund branches return all actual collected money, not a fictional total T.

Let P be verified platform collection and H evidenced actual Host direct collection (attested, never provider-verified). Derive source-specific policy entitlements Rp/Rh from collected eligible-charge allocations; full-refund branches Rp=P, Rh=H. `totalPolicyRefund = Rp + Rh`. Under booking/refund locks, a new platform reserve x must satisfy **x>0**, `x <= Rp - platformRefundPaid - platformRefundReserved` AND `x <= P - platformRefundPaid - platformRefundReserved`, plus available liquidity/recovery gates. For direct responsibility y independently require `y>0`, `y <= Rh - hostRefundPaid - hostRefundReserved` AND `y <= H - hostRefundPaid - hostRefundReserved`. Paid/reserved belong to same source and charge entitlement; never move Host cash into platform pool or compensate one pool's excess with the other. Reserve atomically/idempotently; transition reserved→paid once with proof and different maker/checker; retry cannot double reserve/pay, failed release is audited. Host refunds need separate evidence and acknowledgment/case disposition. Payout already made requires funded recovery/manual escalation, not negative pool or false PAID.

**SLA:** platform portion **5–10 business days from eligible request**; preserve requestReceivedAt/eligibleRequestAt and explain missing eligibility evidence, not reset clock on retry/approval. Emails state platform amount/source, REQUESTED versus PAID, range, blocked reasons/escalation and Host direct responsibility separately. This does not promise Host direct same SLA without contract. OperationsFinance owns daily reconciliation/reserve/recovery and Admin maker/checker proof; authoritative holiday-calendar source/version, business-day timezone/cutoff and signed operational/legal permission must be documented at release095. None is fabricated here; calendar clearance blocks real-money SLA execution, not this approved numeric policy.

**Tax gate:** cancellation tax refund treatment has no legal approval. MVP tax config remains unset/disabled; paid publishing/activation is blocked until reviewed tax treatment and config are documented (even if illustrative fixtures have zero tax). Do not infer late-cancel tax retention/refund law or invent other fee refund schedules. Config086/109 and legal095 remain separate gates.

Worked **tax-free illustrative** cases (not commercial config or runtime evidence):

1. Free-window boundary: T1,000,000; hybrid P200,000/H0/uncollected B800,000 → refund200,000, not1,000,000. If H300,000 actually received → Rp200,000/Rh300,000/total500,000. Full-prepay P1,000,000/H0 → refund1,000,000. Host inability uses the same collected-source rule.
2. Late/no-show cleaning: room900,000 +cleaning100,000, platform-funded D100,000 allocated90,000/10,000 → Guest net room810,000/cleaning90,000, T900,000. P180,000 allocates162,000 room/18,000 cleaning; H0 → Rp18,000/Rh0, retained collected room162,000. Uncollected720,000 comprises room648,000 +cleaning72,000: neither is refunded or autocharged. With H360,000 allocated324,000/36,000, Rh36,000 and total unused-cleaning refund54,000; retained collected room486,000. Full P900,000 → unused-cleaning refund90,000, never gross100,000 plus discount. No actual stay is required for this unused-cleaning branch; actual check-in instead routes incident case.
3. Full-refund pool P200,000 with paid50,000/reserved20,000 → new130,000 allowed,130,001 rejected; if late-case Rp18,000 with paid5,000/reserved2,000, new11,000 allowed/11,001 rejected despite larger P. Host direct pool is independently bounded. D100,000 is funded expense, never extra Guest refund; all source/charge sums conserved.

## 6. Coverage và rollout evidence

| Requirement | Tasks / evidence bắt buộc |
|---|---|
| Approved instant + both modes/config + quote validation | TSK-079/109/103/082/080; TSK-077/114 |
| Exact balanced accounting, funding/coverage, no monthly invoice | TSK-110/089/085/087/105; TSK-077/114 |
| Direct partial receipt, correction, privacy/dispute | TSK-111/093/113; TSK-114 |
| Per-room actual check-in +24h, contested/post-payout recovery | TSK-090/086/112/087; TSK-114 |
| Dual-mode guest/host/admin screens + runbook/email | TSK-113/076/114; TSK-078 |
| Atomic inventory and additive migration | TSK-099/102/083/106/107/108 |
| Both modes release, no deferred hybrid | TSK-049/050/051/052/077/078/095; all P0 + core gates |

Evidence phải ghi exact command/cases, environment, duration, result, artifact và skipped/blocked. Mock provider/SMTP/bank external, không mock production ledger/inventory để tuyên bố concurrency đã an toàn. Business approval không đóng implementation tasks. Chưa có runtime evidence trong lần soạn docs này.
