# Payment — canonical TARGET v2 (2026-10-23)

**Approved direction, pending delivery.** Cả `FULL_PREPAY` và `HYBRID_DEPOSIT` là **core MVP bắt buộc**, cùng multi-room. Đây là thiết kế, không chứng nhận source/schema/runtime đã đạt. Không dùng sandbox, screenshot hay checkbox lịch sử làm evidence tiền thật. Inventory theo [PropertyInventoryBusinessRules](PropertyInventoryBusinessRules.md); contracts tại [API](../architecture/3_API_Design.md), [Payment data](../database/8_Payment.md); tiến độ/evidence tại [TaskList](../tasklist/TaskList.md).

## 1. Quyết định đã duyệt và thứ tự ưu tiên

| Decision | Đã duyệt | Chưa chốt / gate |
|---|---|---|
| PAY-01 | FULL_PREPAY: thu 100% online qua StayHub/VNPay | Fee/tax basis, funding và commercial config: TSK-086/088/109 |
| PAY-02 | HYBRID_DEPOSIT: deposit online bắt buộc, phần còn lại trả **trực tiếp Host cash/bank tại check-in**; hướng tỷ lệ cấu hình trong khoảng 15–20% | Exact selected rate, base, rounding và coverage theo offer: TSK-109; không mặc định 20% |
| PAY-03 | Cả hai instant CONFIRMED sau verified required online amount và active all-item hold; không Host accept/reject | Ghi state matrix/config tại TSK-079; không hỏi lại instant hay lùi hybrid sang Post-MVP |
| PAY-04 | Host payable chỉ **ELIGIBLE actual check-in +24 giờ** cho entitlement áp dụng, còn subject dispute/reconcile/refund availability/legal | Check-in evidence và all-arrived vs per-room boundary cần PO duyệt tại TSK-090/112; legal/funding tại TSK-086 |
| PAY-05 | Không free unpaid reservation, không thu commission hằng tháng, không StayHub QR pass-through tại chỗ | Không tạo hidden monthly commission receivable; coverage không đủ phải reject/requote/disable offer |

Quyết định user đã duyệt → decision log và inventory → API/ERD/UI projections → task AC/evidence. Legacy chỉ để trace; khi lệch phải sửa cùng contract version trước triển khai. Inventory boundaries có explicit questionnaire approval riêng, frozen **S0-097-v1** tại inventory canonical; không suy từ payment approval và không chứng nhận implementation. Không mặc định escrow hợp pháp/bắt buộc, zero fraud hoặc tỷ lệ rủi ro giả định.

## 1a. Approved contract matrix — S0-079-v1

Approved 2026-10-23 by user as product owner through the seven-group questionnaire, recorded in [S0DecisionLog](S0DecisionLog.md). This is a versioned policy approval, not an invented signature, legal clearance or runtime certification. This section supersedes earlier pending wording for the state/actor contract only; numerical configuration and legacy fixtures below remain pending replacement at TSK-109/086, not approved rollout inputs.

| Mode | Server quote / required online O | Direct balance B | Confirmation |
|---|---|---|---|
| FULL_PREPAY | O = contractual total T | B = 0 | Instant only after verified exact O and active all-item hold |
| HYBRID_DEPOSIT | Mandatory O from server-computed versioned config; O + B = T | B = T − O, paid directly to Host cash/bank at check-in | Same instant guard; does not wait for B |

Both modes are required MVP. Guest selects only server-allowed plan/mode; Host may configure owned offers only within approved platform config, never waive O or accept/reject a booking. Snapshot contract/config version, mode, T/O/B, currency, allocations and consent. Each attempt binds expected O server-side to booking/reference/currency/version, not a client amount or T substituted for hybrid O. Exact deposit/rounding/commission/funding/tax/DTO freeze belongs to TSK-109/086; approval of parameters is not their completed freeze. No free unpaid reservation, monthly commission receivable or onsite StayHub QR pass-through.

| Booking state / event | Authorized actor and guard | Collection / resulting state |
|---|---|---|
| New → PENDING_PAYMENT | Authenticated Guest for own booking; valid server quote and atomic all-item hold | ONLINE_DUE; no confirmation from submit |
| PENDING_PAYMENT → CONFIRMED | Verified provider IPN service only: signature, merchant, reference, currency, envelope/result and exact attempt O; active hold under serialized expiry/credit transaction | ONLINE_PAID_VERIFIED; convert all holds once; full B=0, hybrid DIRECT_OUTSTANDING |
| Return / polling, any state | Authorized Guest/Host/support read scope; browser signature is not collection authority | Read-only; no booking/ledger mutation |
| PENDING_PAYMENT → CANCELLED on expiry/final failure | Expiry worker / verified callback under lifecycle guards; retry/final-failure details owned by TSK-081 | Release once; failed attempt alone is not unconditionally whole-booking failure |
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
S = sum(room charges + named scoped fees + named taxes)
T = S - D >= 0                    # Guest contractual total, discount D allocated once
Fh = approved funded credit to Host, distinct from Guest cash (may be 0)
Fp = other approved funded credit to platform (may be 0)
O = dueOnline; B = balanceDueHost; O + B = T
FULL_PREPAY: O = T; B = 0
HYBRID_DEPOSIT: O = round(approved deposit base * approved rate); B = T - O
G = gross Host entitlement after Host-funded adjustments, before commission
C = contract commission due (earned only per approved earning policy)
H = G - C                        # total Host net entitlement
T + Fh + Fp = H + C + named third-party/fee/tax entitlements
```

Funding split phải balanced theo từng beneficiary; nếu discount do Host chịu thì G giảm, nếu platform tài trợ thì ghi nguồn Fh và contra campaign expense, không bịa cash Guest đã trả. Provider costs là expense/liability riêng với payer được duyệt, không trừ hai lần. `commissionDue`, `commissionEarned`, cash **retained/reserved**, platform-held **hostPayable**, `onlinePaidVerified`, `directPaidAttested`, `directOutstanding` là các đại lượng khác nhau. Deposit là **part payment** áp dụng T, không tự earned revenue và không tự nonrefundable commission.

Tại quote/reservation và mọi adjustment/refund: tính lại allocations/entitlements/coverage bằng snapshot. Hybrid chỉ bán khi deposit phân bổ có thể giữ đủ commission hợp đồng sau eligible adjustments cùng liabilities khác, không dựa tiền thu hằng tháng. Nếu coverage không khả thi: reject/requote cần consent hoặc disable **offer/config không hợp lệ**, không disable hybrid khỏi MVP. `O>=0`, `B>=0`, không giữ commission vượt entitlement, không host payable âm. Recompute không mutate lịch sử: append versioned adjustments/reversal; deficit sau payout thành dispute/recovery case, không giả monthly collection.

### Fixture máy đọc được — minh họa, không duyệt rates

Ví dụ không fee/tax/discount/funding/provider cost, G=T=1.000.000 và C=150.000 chỉ để kiểm sổ; 20% ở đây là **fixture**, không config rollout:

```json
[
  {"paymentMode":"FULL_PREPAY","total":1000000,"dueOnline":1000000,"balanceDueHost":0,"commissionDue":150000,"platformHeldHostPayable":850000,"hostTotal":850000},
  {"paymentMode":"HYBRID_DEPOSIT","total":1000000,"dueOnline":200000,"balanceDueHost":800000,"commissionDue":150000,"platformHeldHostPayable":50000,"hostTotal":850000}
]
```

Hybrid: Guest 200.000 online +800.000 trực tiếp; deposit phân bổ 150.000 commission reserve +50.000 host payable. Host net 800.000+50.000=850.000; platform commission 150.000 **trước refund/provider costs**, không phải toàn deposit. Full: Host850.000/platform150.000, không thu thêm800.000 trực tiếp.

Rounding edge fixture: eligible charges `[1,1,1]` minor units, discount1; largest remainder/stable ordinals cho allocations `[1,0,0]`, net `[0,1,1]`, sum2 không âm. Base0 chỉ discount0, không chia0. Hybrid illustrative deposit15% củaT1.000.000 =150.000 **không đủ** commissionDue160.000: reject/requote offer, không giữ missing10.000 để thu tháng sau. Đây là boundary arithmetic, không commercial config.

Fixture multi-room xuyên API/DB/UI giữ T=6.900.000 (room7.000.000 +booking fee100.000 −discount200.000). **Chỉ fixture:** fee100.000 là Host entitlement, discount200.000 Host-funded, tax/provider cost0, commission basis adjusted Host entitlement6.900.000 ×15%=1.035.000. Full O6.900.000/B0/held Host5.865.000; hybrid deposit20% O1.380.000/B5.520.000/held Host345.000; Host total5.865.000. Không đồng thời coi fee100.000 là platform revenue. Các tỷ lệ ví dụ không thay approval config.

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

Mỗi online attempt có ID/reference/expected O/currency/config snapshot riêng, retry checkout không tạo nhiều live collectible attempts không kiểm soát. Serialize booking amount-credit: accept đúng một required collection; extra verified receipts vẫn ghi tiền thực và case hoàn/đối soát, không silently drop. Attempt success không overwrite cancelled booking. Zero-online/zero-total cần policy được duyệt tại TSK-088, không là bypass mandatory hybrid deposit hoặc tạo gateway amount0.

## 4. Direct receipt và actual check-in evidence

Host có ownership chỉ **attest** tiền cash/bank họ nhận: amount/currency/method, paidAt, recordedAt, actor, reference, evidence handle, idempotency key/body hash, expectedVersion. Không lưu/expose bank credentials hay PII nhạy cảm trong API/logs; evidence private, audited, quyền support tối thiểu. Amount dương, partial receipts cộng dồn tới B; overpayment/conflicting evidence vào case, không xác minh tự động. Correction bằng reversal tham chiếu receipt gốc, không xóa audit. Guest acknowledgment/dispute độc lập; không biến attestation thành VNPay event.

Check-in ghi booking item + roomOrdinal (không yêu cầu PhysicalRoom), actualAt/timezone, actor/evidence/version, guest acknowledgment và contested/admin disposition. **Khuyến nghị chờ PO:** hai bên acknowledge; khi contested cần evidence/admin fallback, không tự tin chỉ vì Host tap. Nhiều phòng đến khác giờ phải lưu riêng. **Decision còn mở:** payout baseline all-arrived (mốc muộn nhất của mọi room đã verified) hay phân entitlement từng room +24h; chưa chốt thì block payout eligibility implementation/rollout, không bịa mốc toàn booking.

## 5. Refund, dispute và payout

- Cancellation policy snapshot xác định **totalPolicyRefund** sau fee/tax/discount allocation và adjustment commission; không mặc định deposit mất khi no-show. Tách `platformRefundable`, `hostDirectRefundResponsibility` và từng bên đã trả. Host direct được attested/refunded bằng evidence riêng, không giả StayHub hoàn tiền chưa thu.
- Platform refund guard đồng thời: `newRefund <= totalPolicyRefundRemaining` và `newRefund <= onlineCollectedVerified - onlineRefundedPaid - onlineRefundReserved`. Đối soát available held liquidity sau payouts/reserves; đủ quyền hoàn không đồng nghĩa đủ tiền có sẵn. Nếu đã payout phải fund recovery trước hoặc case/manual escalation, không trả số âm hay báo paid khi chỉ requested.
- Manual refund fallback qua email/admin, signed SLA cấu hình **business days**, timezone/holiday calendar/cutoff được duyệt; không hứa 3–5 ngày khi chưa chọn. States REQUESTED → APPROVED/PROCESSING → PAID hoặc FAILED, maker/checker khác người, reference/proof/audit bắt buộc mới confirm PAID. Retry idempotent giữ reserve, failed release reserve có kiểm soát. Không claim automatic refund API mới.
- Payout states BLOCKED → ELIGIBLE → APPROVED/PROCESSING → PAID/FAILED. +24h chỉ eligibility earliest theo actual evidence; phải reconcile verified online collection, entitlement, commission adjustment, refund/dispute reserves, liquidity và legal approval. Manual payout proof/audit/maker-checker, không guaranteed automatic transfer và không trả cả T trong hybrid (B đã trả Host).
- Dispute trước payout freeze applicable entitlement; sau payout append reserve/clawback/manual recovery case với owner, amount, evidence, status và liability disposition; không che mismatch bằng doanh thu âm hay mở commission invoice hằng tháng.

Refund pool fixture (hybrid trên, giả sử policy full refund và Host đã nhận B): totalPolicyRefund1.000.000, platform share200.000, Host responsibility800.000. Sau platform paid50.000 và reserved20.000, platform pool còn130.000: refund130.000 hợp lệ,130.001 bị reject; Host800.000 vẫn không trở thành platform cash. Mọi policy refund và availability sau payout phải đối soát riêng.

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
