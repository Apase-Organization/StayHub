# Property inventory — canonical TARGET S0-097-v1

**S0-109-v1 FROZEN_DESIGN:** [policy](contracts/s0-policy-v1.json), [fixtures](contracts/s0-fixtures-v1.json), [typed API](contracts/s0-api-v1.json) consolidate079/097/081/084/086/088/090/109 approvals; [handoff](S0DecisionLog.md#s0-final-handoff--tsk-109). No source compatibility/runtime/legal clearance. Whole integer VND; active fixture T6.800.000, no Guest service fee, platformD/Cnet coverage. All policy decisions frozen;095 paid activation BLOCKED.

Ngày phê duyệt thiết kế: **2026-10-23**, version **S0-097-v1**, user as PO qua questionnaire, provenance tại [S0DecisionLog](S0DecisionLog.md). Đây là **thiết kế mục tiêu đã duyệt, chưa triển khai**; không chứng nhận schema/source/runtime. Multi-room và **cả FULL_PREPAY/HYBRID_DEPOSIT** là core MVP. [PaymentBusinessRules](PaymentBusinessRules.md) giữ contract tài chính riêng; TSK-097 không đóng implementation hay release gates.

## 1. Cơ sở nghiên cứu và giới hạn

Các nguồn chính thức đã truy cập thành công (ngày trên là ngày review, không phải ngày website cập nhật):

- https://developers.booking.com/connectivity/docs/ari — inventory là số lượng phòng; availability phụ thuộc ngày/occupancy; room, rate plan, restriction và pricing là các khái niệm riêng. Có nhiều mô hình giá (standard/derived/occupancy/LOS), không phải yêu cầu StayHub triển khai tất cả.
- https://www.airbnb.com/help/article/5 — entire place, private room, hotel room, shared room; nhiều bedroom trong entire place không có nghĩa mỗi bedroom là một inventory bán riêng.
- https://www.airbnb.com/help/article/289 — các phòng bán riêng tại cùng địa điểm có listing/calendar riêng; calendar linking có thể tránh trùng giữa nguyên căn và phòng bên trong. StayHub MVP đã chọn không hỗ trợ linked whole+room sale.

Không suy diễn Booking và Airbnb có cùng schema hoặc policy. Screenshot các dòng là room type/offer, không chứng minh có số phòng vật lý công khai hay tồn kho trực tiếp trước khi chọn ngày. Inventory boundaries dưới đây là quyết định StayHub đã được user/PO duyệt, không phải chính sách phổ quát của thị trường; chi tiết triển khai vẫn cần validation riêng.

## 2. Từ điển và ranh giới aggregate

**Định vị user đã xác nhận:** giao diện lấy cảm hứng Airbnb (ảnh lớn, khoảng trắng, card/gallery, selection sidebar), nghiệp vụ inventory theo hướng Booking.com (property → types → offers, quantity theo ngày). Đây là hai trục độc lập; không sao chép thương hiệu và không suy diễn schema nội bộ của nền tảng nào. Room type là hạng phòng (Deluxe), room allocation là một phòng được mua trong item; không phải số phòng vật lý (101).

**User/PO đã duyệt từng boundary tại S0-097-v1:** multi-room core; nhóm types theo property; exclusive sale mode; pooled truly interchangeable inventory; shared-plan stock; whole-place quantity1; một property/date range/**VND** mỗi booking; hủy toàn booking, không partial item/quantity cancellation. Đây là explicit questionnaire approval, không suy ra từ yêu cầu multi-room. Thay đổi boundary sau freeze cần version mới và đồng bộ contracts/AC trước triển khai.

| Khái niệm | TARGET |
|---|---|
| Property | Cơ sở: host, địa chỉ, nội dung, ảnh chung, đánh giá và thống kê tổng hợp; một kết quả tìm kiếm duy nhất |
| Classification | Loại cơ sở (villa/hotel/homestay...), độc lập với sale mode |
| Sale mode | Đã duyệt: `WHOLE_PLACE` hoặc `ROOM_BASED`, loại trừ nhau trong MVP; không linked whole+room sale |
| RoomType / AccommodationType | Hạng bán được: ảnh, giường, capacity, tiện nghi, quantity; whole place có một type quantity=1 |
| Bedrooms | Thông tin mô tả, không tự sinh số đơn vị bán |
| PhysicalRoom | Số phòng/assignment vận hành là mở rộng tương lai, không bắt buộc công khai |
| RatePlan | Giá và điều khoản ăn sáng/hủy/thanh toán; mọi plan của cùng type **chia sẻ inventory**; MVP được dùng một plan/type |
| Booking / Item | Đã duyệt: header một property, một khoảng ngày, currency VND; items type + plan + quantity, allocation từng phòng và snapshots |

MVP đã chọn pooled inventory: chỉ gồm phòng thực sự thay thế được, bảo đảm khách ở xuyên suốt không bị ép đổi phòng. Phòng khác biệt phải tách type; không cộng số lượng mỗi đêm để che thiếu continuity. PhysicalRoom/assignment là mở rộng tương lai, không phải prerequisite hay endpoint bắt buộc của MVP. Mọi RatePlan cùng type dùng một pool; plan không sở hữu stock riêng.

## 3. Invariant inventory và khách

Theo timezone cơ sở, mỗi đêm `d ∈ [checkIn, checkOut)` và type `t`:

```text
total(t,d) >= 0
0 <= blocked(t,d) <= total(t,d)
sellable = total - blocked
held >= 0; confirmed >= 0
remaining = sellable - held - confirmed >= 0
stockQuantity(stay) = min(remaining(t,d) for every night d)
availableQuantity(type,plan,stay) = stockQuantity(stay) if salesEligible(type,plan,stay) else 0
```

`blocked` là OOO (capacity không bán được), không chứa held/confirmed; giảm total điều chỉnh capacity gốc, không trừ hai lần. Stop-sale là restriction chặn bán mới (availableQuantity=0), không biến commitments thành blocked và không hủy chúng. `sellable` là trước commitments. Chỉ hold còn hiệu lực và confirmation còn hiệu lực chiếm stock; expired phải transition RELEASED serialized trước tái bán, không chỉ dựa read clock. Conversion hold→confirmed không đổi tổng commitments. Giảm total/OOO không được phá commitments hiện hữu, kể cả hold đang hiệu lực; reject thay đổi hoặc xử lý relocation/compensation được duyệt trước. Không tính availability bằng tổng phòng-đêm.

Quantity là số nguyên dương; item/type/plan cùng property, plan active toàn stay; restrictions không tạo stock riêng. **S0-090-v1 approved:** adults declared18+, each room>=1adult; childrenAges integers0..17 (no fraction/negative/18/null), every child including age0 counts occupancy. Validate each room against capacity/maxAdults/maxOccupants plus exact header totals/multisets, not pooled booking capacity. No child pricing/extra beds in MVP; collect adult counts/declared age rule, not unnecessary DOB/identity documents.

### 3a. Approved time / stay / evidence / review — S0-090-v1

User PO questionnaire approval frozen2026-10-23, [decision log](S0DecisionLog.md). All MVP property dates use **Asia/Ho_Chi_Minh (UTC+7)**; scheduled check-in14:00, checkout11:00. Nights are local-date difference, **1..30 inclusive**, checkIn<checkOut, no past check-in dates. Same-day minimum advance allowed only if original booking creation local time **<20:00**; **>=20:00 rejects same-day**, not future-date booking. Preview advisory, revalidate at creation; retry of existing19:59 booking keeps original081 expiry (20:14), may settle after20:00 if hold active. Never reset deadline or reclassify retry as fresh eligibility. Type/plan stop-sale/blackouts use existing scoped calendar rules, no invented calendar dates. Adjacent checkout dayD11:00 → next check-in dayD14:00 is valid; half-open night inventory needs no extra turnover night.

Host owner records per item+roomOrdinal actualAt/timezone/recordedAt/actor/private evidence/version/key. Validate actualAt not future relative authoritative server recording time and not before booking creation; verify plausible chronology against stay/evidence, flag contradictions/backdating for case review, no invented fixed24h backdate limit or claim perfect proof. Host tap alone is insufficient: Guest independently acknowledges, or Admin verifies evidence through audited case with reason/actor/disposition. Correction appends audit/reversal, never rewrites event history; duplicate key/body no second event, conflicting body/version rejects. Use **actual event time**, not later acknowledgment time, for ALLARRIVED `max(verified actualAt of every booked room)+24h`. Partial/no-show/contested events block automatic eligibility; Admin cannot bypass dispute/refund/reconcile/liquidity/legal gates without satisfying audited evidence and all other gates.095 real-money clearance still BLOCKED.

Guarded service may set COMPLETED only **at/after scheduled checkout date11:00** AND actual-stay evidence with no unresolved incident/case; neither calendar alone, Host tap, paid receipt nor early checkout permits early completion. Partial/no-show cases require resolution; no-show alone never actual stay. Completion transition records immutable completedAt/actor/version/evidence under lock; duplicate transition no new timestamp/window.

Review author must be booking's authenticated actual-stay Guest, booking COMPLETED with actual-stay evidence, no no-show-only entitlement; **one review per booking**, not item/room/type/receipt. Window **completedAt <= now < completedAt+14days**, exact end exclusive, elapsed instants (14×24h); late completion does not backdate window to checkout. Same-key replay returns same review; second distinct creation409 duplicate, window end409 closed, no-show/not-completed/evidence-missing409 ineligible, nonowner404/anonymous401/role-CSRF403. Existing moderation scope remains; no new two-way-review feature096 or invented auto-publication guarantee.

Boundary examples (design, not runtime): same-day19:59 allowed/20:00 denied, tomorrow20:00 allowed; nights1/30 allowed,0/31 denied; child0/17 valid,18/fraction invalid; one room adults0 rejected even if another room has2. Last verified actualAt16:00, Guest ack next day10:00 → earliest next day16:00 subject all gates, not ack+24h. Early-checkout evidence cannot complete at10:59; scheduled11:00 plus actual evidence/resolved cases can. Review at completedAt+14days−1instant allowed, exact+14days denied.

`salesEligible` yêu cầu không có type/day stop-sale trên mọi đêm và plan hợp lệ theo restrictions. Type stop-sale chặn bán mới mọi plan của type; plan stop-sale chỉ chặn offer của plan đó, không đóng các plan khác hay tạo pool riêng. Cả hai không đổi total/OOO/remaining, không release holds/confirmed; hold còn hiệu lực vẫn được convert theo lifecycle đã duyệt dù sau đó đóng bán. Luôn giữ `held + confirmed <= total - outOfOrder`, kể cả khi đóng bán. Ví dụ total5/OOO1/held1/confirmed2: remaining1; type stop-sale làm availableQuantity0 nhưng remaining vẫn1, không âm. Chỉ đóng plan70 thì plan71 vẫn có thể bán1 từ cùng pool nếu đủ điều kiện.

## 4. Giá, tài chính và snapshots

Booking item lưu type/plan ID và tên/version lịch sử, quantity, guest allocation từng phòng; lưu từng đêm, từng phòng: giá, phí, thuế nếu áp dụng và policy snapshot. Header lưu tổng booking và booking-level fees/discount; không nhân phí booking một lần nữa theo items. Không đọc giá hiện hành để tính lịch sử.

`subtotal = roomCharges + cleaningFee + reviewed named taxes`; `totalStay = subtotal - discount > 0`, no Guest service/booking fee. Cleaning scoped charge snapshot, no multiplication twice. Cgross15% prediscount room+clean excludingtax; platformD<=Cgross, Cnet=Cgross−D, hybridO20%T HALF_UP/coverageO>=Cnet. Tax unset/disabled/no rate inferred, paid activation blocked until legal/config review.

Whole-VND HALF_UP once per total, stable largest remainder item/room/night/charge ordinal, exact sum/no overcharge/float. Zero eligible base coded checkout rejects/no divide0; zero T/O always rejects, no internal free settlement. Caps reserve/consume/release atomic original900s/max3 attempts, one consumption/booking not perroom. Financial partial refund does not mean partial inventory cancellation.

Payment settlement là booking-level một currency, không phải mỗi type. Snapshot paymentMode/allowed modes/config/version/deposit base/rate/commission basis và allocations; `dueOnline + balanceDueHost = total`. Full dueOnline=total; hybrid deposit bắt buộc online, balance cash/bank trực tiếp Host lúc check-in. Ledger tách online provider-verified, direct Host-attested, refund, funding, commission due/earned/reserved và platform-held host payable; không gọi deposit/gross là revenue. Coverage thiếu phải reject/requote offer, không monthly commission invoice. Payout chỉ eligible actual check-in+24h, subject dispute/reconcile/refund/legal, không mặc định escrow hay automatic transfer.

## 5. Flows, states và atomicity

Host tạo property một lần → chọn mode → tạo types/quantity/ảnh/capacity → plan/giá/restrictions → calendar theo type/ngày → publish khi đủ điều kiện. Sửa type không làm thay đổi snapshots.

Guest tìm property duy nhất → nhập dates/adults/children ages/rooms → xem types có thể đáp ứng, chọn quantity/plan → allocation từng phòng → tổng tiền **cả stay** → checkout revalidate. Trước dates chỉ giới thiệu types và giá tham khảo có basis, không tuyên bố live stock. Hết stock hiển thị sold out/alternative, không gắn available. Search from-price phải là eligible offer đáp ứng yêu cầu; ghi room count, occupancy, nights, fees/taxes inclusion và stay total, không dùng giá một phòng/đêm để ngụ ý tổng booking.

| Event / actor | Booking / payment | Inventory và side effects |
|---|---|---|
| Guest submit hợp lệ | PENDING_PAYMENT / PENDING | Transaction khóa type/date theo thứ tự ổn định; revalidate mọi item/giá/policy/caps; giữ **tất cả** nights/items hoặc rollback toàn bộ, idempotency key chống double submit |
| IPN verified đủ dueOnline theo attempt/mode, hold còn hiệu lực | CONFIRMED / online attempt SUCCESS, instant đã duyệt | Atomic collection + convert toàn bộ hold + consume discount; hybrid không chờ balance trực tiếp hay giữ hold tới stay; outbox/email sau commit, dedupe |
| Attempt failure / worker finalization | S0-081-v1: callback updates its attempt only, no booking cancellation | Hold original createdAt+15min, max3 creations inclusivefirst, no extension; guarded worker final-failure/expiry releases all holds/caps once |
| IPN duplicate/out-of-order | Không lặp side effect | Verify signature, amount, currency, merchant/reference/envelope; audit/reconcile |
| Late paid sau release | Không tự hồi sinh confirmation | Ghi nhận tiền đã nhận, luồng refund/reconciliation được duyệt; không chiếm stock đã bán |
| Guest/host/admin cancellation hợp lệ | CANCELLED; refund lifecycle độc lập | MVP hủy **toàn booking** atomically; release đúng commitments, refund theo snapshot |
| Actual arrival / sau stay đủ điều kiện | Per-room check-in evidence; COMPLETED/no-show riêng | Guard ngày/actor/acknowledgment/contested fallback theo policy; partial arrival lưu từng room, một review cho booking; không tự suy check-in từ calendar completion |

Return URL/poll chỉ đọc; instant sau đủ required online amount và cả hai modes đã duyệt tại payment canonical. Direct balance unpaid/refused/disputed không tự release occupied rooms, Host receipt không thành provider verification. S0-097-v1 loại khỏi MVP partial item/quantity cancellation, mixed dates/currencies/properties và linked whole+room sale. **S0-084-v1** refund frozen tại Payment section5a: >=48h/Host inability refund full collected money by source; late/no-show retain room, refund collected net unused cleaning only without actual stay; after-check-in incident case. No automatic B charge or pool mixing; refund does not imply partial inventory cancellation or payout. Platform SLA5–10businessdays, calendar/legal/tax clearance gates remain release-blocked.

## 6. Cross-business review matrix

**S0-081-v1 approved:** active only `t < expiresAt`, at/after expiry no confirmation even before worker runs. Count3 with pending attempt can settle within original window; early worker final-failure requires all three terminal unsuccessful/no credit. Callback cannot kill another pending attempt. Serialized expiry/IPN converts or releases all items/caps once; late/extra money reconciles/refunds, never resurrects released/resold stock. Hold TTL/retry approval supersedes old pending wording in section9; other policy deliverables remain separate.

All rows **pending implementation**, not runtime proof. All eight S0 design deliverables frozen;112 and implementation tasks remain pending; actual Finance/Legal/operations evidence blocked095. Owners/sprints TaskList; docs do not close implementation tasks.

| Domain | Tác động bắt buộc / gate |
|---|---|
| Host/security | Ownership xuyên property/type/plan; chống self-booking theo policy; account lock không bỏ callback/refund; archive từng type không ảnh hưởng type khác hoặc future paid stays (TSK-093/094/098) |
| Search/detail/UI | Một property/card; cùng nguồn all-night availability; room allocation, eligible from-price, sold-out và revalidation (TSK-100/101) |
| Booking/inventory | Shared-plan stock, atomic multi-item holds, continuity, expiry/late IPN, adjacency (TSK-083/099/102) |
| Payment/discount/refund | Booking total, exact allocations, zero totals, idempotent reconciliation; financial partial refund khác partial cancellation (TSK-082/085/089/103) |
| Review/notification | Một review/completed booking bất kể quantity; optional booked-type context snapshot; email tổng hợp items sau commit (TSK-076/092/104) |
| Stats/admin | Booking count distinct header; rooms sold = Σ quantity; roomnights = Σ quantity×nights; occupancy denominator sellable roomnights nhất quán; net/refund/fees theo ledger không duplicate khi join items (TSK-087/105) |
| Operations | Lock/archive có support owner cho future bookings; không xóa references/snapshots; rollback không mở lại double-selling (TSK-106/108) |

## 7. Migration và rollout (kế hoạch, không thực thi)

1. Additive schema conceptual: room_types, rate_plans, type_date_inventory, booking_items, per-room allocation/nightly snapshots; constraints FK cùng property và nonnegative commitments. Thiết kế cụ thể/locking phải được DB review, không xem bảng này là migration đã có.
2. Mỗi property hiện tại → default type quantity1 + default plan; giữ property/booking/payment/review IDs. Backfill historic items bằng snapshots đã lưu, không lấy giá hôm nay; thiếu lịch sử phải gắn provenance/unknown và reconcile, không bịa nightly tax breakdown.
3. Không auto-merge cùng địa chỉ. Host xác nhận mapping listing trùng riêng, giữ links/history và chứng minh không double-sell.
4. Đối soát counts, totals, currency, dates, active commitments; tạo capacity protection type/date và chạy shadow read/so sánh trước khi thay exclusion property. **Chỉ bỏ exclusion property sau** backfill, reconciliation và protection mới có hiệu lực; không bỏ constraint trước rồi trông vào app check.
5. Cutover có feature flag/write coordination tránh hai nguồn stock; rollout nhỏ, monitoring oversell/mismatch/late IPN, runbook owner; rollback phải bảo toàn bookings mới, không quay lại single-unit schema mù quáng.

## 8. Ví dụ và acceptance

Ví dụ minh họa (không phải bảng giá/policy): property có Twin quantity3 và Family quantity1. Hai booking Twin/Family cùng ngày hợp lệ; booking Twin quantity2 trong 2 đêm dùng 4 roomnights nhưng chỉ một booking. Hai plan của Twin vẫn chỉ có 3 phòng. Nếu remaining từng đêm Twin=[2,1], không bán quantity2 cho cả stay. Check-out ngày D và check-in D liền kề không xung đột.

**s0-fixtures-v1:** property42 Deluxe(type7)5/Family(type8)2.2030-12-10→12, Deluxe2/plan70(each2adults), Family1/plan80(2adults+child7):3rooms/6adults/1child/6roomnights. Deluxe1m/room/night, Family1.5m → roomCharges7.000.000, cleaning0/taxes0 fixture, no Guest fee, platformD200.000 → subtotal7.000.000/totalStay6.800.000. Remaining eachnight Deluxe3/Family1, plan71 uses same pool; Family secondnight0 rolls back all. Wholehome4bedrooms stillone typequantity1, occupies1/night, not4. Tax0 fixture is not legal tax clearance.

Payment: Cgross1.050.000/Cnet850.000; fullO6.800.000/B0/held5.950.000; hybridO1.360.000/B5.440.000/held510.000; Host5.950.000 both. Approved20%/15% numeric choices, not activation. Both confirm same inventory after exactO/activehold. Providerfee fixture0 not actual contract value; earning/refund/source/legal gates Payment.

## 8a. Bản đồ tài liệu / precedence

Quyết định user + decision log tại đây → [API TARGET](../architecture/3_API_Design.md) / [ERD TARGET](../database/3_CoreERD.md) / [UI TARGET](../ui/UXUI_DesignSystem.md) → AC/dependencies/evidence tại [TaskList](../tasklist/TaskList.md). Khi lệch phải sửa cùng version trước triển khai, không dùng demo legacy để override canonical; source là hiện trạng cần tái xác minh, không phải bằng chứng target hoàn tất. Relational/class diagrams là projections của ERD, screenshots chỉ minh họa lịch sử. Overview tóm tắt, không tự duyệt chính sách.

QA phải chứng minh happy + critical edge bằng test nghiệp vụ/DB thật khi triển khai, mock provider ngoài chứ không mock invariant:

- Cùng property khác type đặt đồng thời được; hai request tranh last room chỉ một thành công, shared plans không oversell.
- Một item thiếu stock hoặc một đêm hết phòng → rollback toàn hold/items/caps; retry không tạo booking kép.
- Pool không bảo đảm continuity bị từ chối/tách type; giảm capacity dưới commitments bị từ chối.
- Guest allocation tổng đủ nhưng từng room vi phạm adults/children ages → reject; adjacent dates hợp lệ.
- Expiry/IPN race, late paid, duplicate callbacks và zero total; rounding nhiều item có tổng chính xác, base0 không lỗi chia0; refund không hoàn quá collected.
- Migration counts/totals/history và active future bookings khớp, không tự merge địa chỉ; archive/lock cô lập type, giữ support access.
- Search trước ngày không claim stock; sold out không available; join nhiều items không nhân booking/revenue/review.

## 9. Frozen inventory decisions / remaining delivery gates

**Yêu cầu user đã xác nhận:** multi-room là core MVP; các phòng/types được nhóm theo property và guest xem danh sách phòng/types khả dụng. Không chờ quyết định có làm inventory hay không, không đưa yêu cầu này sang P2.

**S0-097-v1** inventory boundaries approved: wholeplace1/descriptive bedrooms/exclusive mode/interchangeablepool/sharedplans/oneproperty-date-VND/wholebookingcancel.084 refund and109 aggregate design now frozen; API/ERD projections not evidence existing schema.

All eight S0 designs frozen; implementation/source evidence/DB races and095 contractual/legal/ops/calendar/tax/liquidity release clearance remain outstanding. No core deferment/reopening decisions.
