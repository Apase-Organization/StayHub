# Property inventory — canonical TARGET v1

Ngày rà soát: **2026-10-23**. Đây là **thiết kế mục tiêu, chưa triển khai**; không phải chứng nhận schema/source/runtime. Multi-room và **cả FULL_PREPAY/HYBRID_DEPOSIT** là core MVP. [PaymentBusinessRules](PaymentBusinessRules.md) v2 giữ quyết định instant/collection/actual-check-in+24h và financial invariants; không tự duyệt các inventory boundaries còn mở tại đây. TSK-097–114 và remediation TSK-076–095 là gates liên quan.

## 1. Cơ sở nghiên cứu và giới hạn

Các nguồn chính thức đã truy cập thành công (ngày trên là ngày review, không phải ngày website cập nhật):

- https://developers.booking.com/connectivity/docs/ari — inventory là số lượng phòng; availability phụ thuộc ngày/occupancy; room, rate plan, restriction và pricing là các khái niệm riêng. Có nhiều mô hình giá (standard/derived/occupancy/LOS), không phải yêu cầu StayHub triển khai tất cả.
- https://www.airbnb.com/help/article/5 — entire place, private room, hotel room, shared room; nhiều bedroom trong entire place không có nghĩa mỗi bedroom là một inventory bán riêng.
- https://www.airbnb.com/help/article/289 — các phòng bán riêng tại cùng địa điểm có listing/calendar riêng; calendar linking có thể tránh trùng giữa nguyên căn và phòng bên trong. Đề xuất mặc định StayHub MVP chưa triển khai mô hình bán liên kết này, chờ PO duyệt.

Không suy diễn Booking và Airbnb có cùng schema hoặc policy. Screenshot các dòng là room type/offer, không chứng minh có số phòng vật lý công khai hay tồn kho trực tiếp trước khi chọn ngày. Các quy tắc bên dưới là thiết kế đề xuất cho StayHub, không phải chính sách phổ quát của thị trường hay quyết định đã được user/PO duyệt.

## 2. Từ điển và ranh giới aggregate

**Định vị user đã xác nhận:** giao diện lấy cảm hứng Airbnb (ảnh lớn, khoảng trắng, card/gallery, selection sidebar), nghiệp vụ inventory theo hướng Booking.com (property → types → offers, quantity theo ngày). Đây là hai trục độc lập; không sao chép thương hiệu và không suy diễn schema nội bộ của nền tảng nào. Room type là hạng phòng (Deluxe), room allocation là một phòng được mua trong item; không phải số phòng vật lý (101).

**User đã xác nhận:** multi-room là core; nhóm các phòng/types thuộc cùng property và liệt kê phòng/types khả dụng. **Các ranh giới triển khai dưới đây là đề xuất mặc định MVP, cần PO phê duyệt tại TSK-097**, không suy ra từ việc user chọn multi-room: exclusive sale mode, pooled inventory (thay vì bắt buộc quản lý từng physical room), whole-place quantity1, một property/date range/currency mỗi booking và hủy toàn booking. Các mục 3–8 dùng nhất quán bộ mặc định này để thiết kế có thể review; chúng không tự tạo approval. Nếu PO chọn phương án khác, phải đồng bộ invariants, flows, contracts, AC và backlog trước triển khai.

| Khái niệm | TARGET |
|---|---|
| Property | Cơ sở: host, địa chỉ, nội dung, ảnh chung, đánh giá và thống kê tổng hợp; một kết quả tìm kiếm duy nhất |
| Classification | Loại cơ sở (villa/hotel/homestay...), độc lập với sale mode |
| Sale mode | Đề xuất: `WHOLE_PLACE` hoặc `ROOM_BASED`, loại trừ nhau trong MVP; không đồng thời bán nguyên căn và phòng con; chờ PO duyệt |
| RoomType / AccommodationType | Hạng bán được: ảnh, giường, capacity, tiện nghi, quantity; whole place có một type quantity=1 |
| Bedrooms | Thông tin mô tả, không tự sinh số đơn vị bán |
| PhysicalRoom | Số phòng/assignment vận hành là mở rộng tương lai, không bắt buộc công khai |
| RatePlan | Giá và điều khoản ăn sáng/hủy/thanh toán; mọi plan của cùng type **chia sẻ inventory**; MVP được dùng một plan/type |
| Booking / Item | Đề xuất: header một property, currency, khoảng ngày; items type + plan + quantity, allocation từng phòng và snapshots; chờ PO duyệt |

Đề xuất mặc định dùng pooled inventory, chưa được PO duyệt và không phải yêu cầu pooled-only từ user. Nếu dùng pool, chỉ gồm phòng thực sự thay thế được, bảo đảm khách ở xuyên suốt không bị ép đổi phòng. Phòng khác biệt phải tách type; nếu muốn gộp cần continuity-aware allocation trước khi bán, không chỉ cộng số lượng mỗi đêm. Phương án quản lý inventory từng physical room vẫn cần được PO cân nhắc tại TSK-097.

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

Quantity là số nguyên dương; item/type/plan phải cùng property, plan active và hợp lệ toàn stay. Restriction min/max stay, closed arrival/departure, occupancy áp dụng theo plan nhưng không tạo stock riêng. Validate mỗi phòng với adults, children và tuổi từng trẻ theo policy; tổng sức chứa toàn booking không thay thế kiểm tra từng phòng. Số phòng và tổng guests phải khớp allocation, không bỏ sót/trùng khách. Age thresholds/extra bed/infant policy còn chờ PO.

`salesEligible` yêu cầu không có type/day stop-sale trên mọi đêm và plan hợp lệ theo restrictions. Type stop-sale chặn bán mới mọi plan của type; plan stop-sale chỉ chặn offer của plan đó, không đóng các plan khác hay tạo pool riêng. Cả hai không đổi total/OOO/remaining, không release holds/confirmed; hold còn hiệu lực vẫn được convert theo lifecycle đã duyệt dù sau đó đóng bán. Luôn giữ `held + confirmed <= total - outOfOrder`, kể cả khi đóng bán. Ví dụ total5/OOO1/held1/confirmed2: remaining1; type stop-sale làm availableQuantity0 nhưng remaining vẫn1, không âm. Chỉ đóng plan70 thì plan71 vẫn có thể bán1 từ cùng pool nếu đủ điều kiện.

## 4. Giá, tài chính và snapshots

Booking item lưu type/plan ID và tên/version lịch sử, quantity, guest allocation từng phòng; lưu từng đêm, từng phòng: giá, phí, thuế nếu áp dụng và policy snapshot. Header lưu tổng booking và booking-level fees/discount; không nhân phí booking một lần nữa theo items. Không đọc giá hiện hành để tính lịch sử.

`subtotal = Σ nightly room charges + Σ item fees/taxes + booking fees/taxes`; `total = subtotal - discount >= 0`. Mỗi khoản có scope (per-room/per-stay/per-booking/per-night) rõ ràng. Giá trị thuế/phí, tax-inclusive hay exclusive, funding và refundability cần duyệt; không mặc định có thuế hoặc tỷ lệ nào.

Tính bằng decimal/minor currency units, không float. Phân bổ discount/refund theo eligible base snapshot; thuật toán largest remainder, tie-break bằng item/room/night ordinal ổn định; không phân bổ quá eligible charge. Tổng phân bổ phải bằng khoản header chính xác sau rounding. Base bằng 0 không chia 0: discount=0 nếu không có eligible amount, zero-total dùng settlement nội bộ idempotent theo policy được duyệt, không gửi payment sai amount. Caps reserve/consume/release atomic, một booking không consume theo số items/phòng. Refund tài chính một phần không đồng nghĩa hủy một item.

Payment settlement là booking-level một currency, không phải mỗi type. Snapshot paymentMode/allowed modes/config/version/deposit base/rate/commission basis và allocations; `dueOnline + balanceDueHost = total`. Full dueOnline=total; hybrid deposit bắt buộc online, balance cash/bank trực tiếp Host lúc check-in. Ledger tách online provider-verified, direct Host-attested, refund, funding, commission due/earned/reserved và platform-held host payable; không gọi deposit/gross là revenue. Coverage thiếu phải reject/requote offer, không monthly commission invoice. Payout chỉ eligible actual check-in+24h, subject dispute/reconcile/refund/legal, không mặc định escrow hay automatic transfer.

## 5. Flows, states và atomicity

Host tạo property một lần → chọn mode → tạo types/quantity/ảnh/capacity → plan/giá/restrictions → calendar theo type/ngày → publish khi đủ điều kiện. Sửa type không làm thay đổi snapshots.

Guest tìm property duy nhất → nhập dates/adults/children ages/rooms → xem types có thể đáp ứng, chọn quantity/plan → allocation từng phòng → tổng tiền **cả stay** → checkout revalidate. Trước dates chỉ giới thiệu types và giá tham khảo có basis, không tuyên bố live stock. Hết stock hiển thị sold out/alternative, không gắn available. Search from-price phải là eligible offer đáp ứng yêu cầu; ghi room count, occupancy, nights, fees/taxes inclusion và stay total, không dùng giá một phòng/đêm để ngụ ý tổng booking.

| Event / actor | Booking / payment | Inventory và side effects |
|---|---|---|
| Guest submit hợp lệ | PENDING_PAYMENT / PENDING | Transaction khóa type/date theo thứ tự ổn định; revalidate mọi item/giá/policy/caps; giữ **tất cả** nights/items hoặc rollback toàn bộ, idempotency key chống double submit |
| IPN verified đủ dueOnline theo attempt/mode, hold còn hiệu lực | CONFIRMED / online attempt SUCCESS, instant đã duyệt | Atomic collection + convert toàn bộ hold + consume discount; hybrid không chờ balance trực tiếp hay giữ hold tới stay; outbox/email sau commit, dedupe |
| Expiry/payment fail | expired/cancelled theo state matrix đã duyệt | Release mọi hold/cap đúng một lần; race với IPN được serialize |
| IPN duplicate/out-of-order | Không lặp side effect | Verify signature, amount, currency, merchant/reference/envelope; audit/reconcile |
| Late paid sau release | Không tự hồi sinh confirmation | Ghi nhận tiền đã nhận, luồng refund/reconciliation được duyệt; không chiếm stock đã bán |
| Guest/host/admin cancellation hợp lệ | CANCELLED; refund lifecycle độc lập | MVP hủy **toàn booking** atomically; release đúng commitments, refund theo snapshot |
| Actual arrival / sau stay đủ điều kiện | Per-room check-in evidence; COMPLETED/no-show riêng | Guard ngày/actor/acknowledgment/contested fallback theo policy; partial arrival lưu từng room, một review cho booking; không tự suy check-in từ calendar completion |

Return URL/poll chỉ đọc; instant sau đủ required online amount và cả hai modes đã duyệt tại payment canonical, TSK-079 ghi state matrix chứ không hỏi lại approval. Direct balance unpaid/refused/disputed không tự release occupied rooms, Host receipt không thành provider verification. Theo mặc định đề xuất chờ PO duyệt, partial item/quantity cancellation, mixed dates/currencies/properties và linked whole+room sale được để ngoài MVP; đây không phải các giới hạn user đã phê duyệt.

## 6. Cross-business review matrix

Tất cả hàng dưới là **TARGET pending delivery**, không phải kiểm chứng runtime. BA/PO duyệt boundaries tại097;079 ghi instant/hai modes đã duyệt; thời gian/children/check-in evidence tại090/112, Finance/Legal084/086/088/109. Owner/sprint theo TaskList; không tự đóng task khi viết docs.

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

**Fixture xuyên API/DB/UI:** property 42 có Deluxe(type 7) quantity5 và Family(type 8) quantity2. Stay 2030-12-10 → 2030-12-12, Deluxe quantity2/plan70 (mỗi phòng 2 adults), Family quantity1/plan80 (2 adults + child age7): 3 phòng, 6 adults, 1 child, 6 roomnights. Deluxe mỗi phòng/đêm 1.000.000 VND, Family 1.500.000 VND → room charges 7.000.000; booking fee một lần 100.000; item fees/taxes 0 trong fixture; discount 200.000 → subtotal 7.100.000, total 6.900.000 VND. Đây là fixture arithmetic, không duyệt fee/tax/child policy. Trước commitments khác, mỗi đêm còn Deluxe3/Family1; plan71 cùng Deluxe dùng đúng pool này, không có 5 phòng riêng. Nếu Family đêm hai còn0 thì cả request rollback. Whole-home có 4 bedrooms vẫn chỉ một type quantity1; đặt nguyên căn chiếm1 mỗi đêm, không4.

Payment counterpart: full O6.900.000/B0; hybrid **fixture20% không phải config duyệt** O1.380.000/B5.520.000. Fixture fee100.000 thuộc Host, discount200.000 Host-funded; commission fixture15% trên adjusted entitlement6.900.000 =1.035.000; held Host345.000 hybrid hoặc5.865.000 full. Host total5.865.000 cả hai, tax/provider cost0. Cả hai confirm cùng inventory sau đủ O; direct receipt/check-in/refund evidence theo payment canonical.

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

## 9. Decision log / còn cần duyệt

**Yêu cầu user đã xác nhận:** multi-room là core MVP; các phòng/types được nhóm theo property và guest xem danh sách phòng/types khả dụng. Không chờ quyết định có làm inventory hay không, không đưa yêu cầu này sang P2.

**Đề xuất mặc định MVP — chưa được user/PO phê duyệt:** whole-place một type quantity1; exclusive WHOLE_PLACE/ROOM_BASED thay vì linked sale; pooled interchangeable types thay vì inventory bắt buộc từng physical room; shared-plan stock; một property/date range/currency mỗi booking; all-or-nothing cancellation thay vì partial item/quantity cancellation. Giữ các mặc định này làm target review nhất quán, không gọi là scope đã chọn. TSK-097 phải ghi quyết định PO cho từng ranh giới và đồng bộ tài liệu/API/schema/AC nếu thay đổi; TSK-084 phải duyệt cancellation granularity cùng refund policy trước triển khai liên quan.

Instant/hai modes/online collection và actual-check-in+24h eligibility đã duyệt; không mở lại. PO/BA/Finance/Legal còn ký: hold TTL/late-paid compensation; timezone/check-in/out/lead/min-max stay; child ages/extra beds; fee/tax/commission bases/rates/funding; exact deposit config trong hướng15–20%; coverage/rounding/zero exception; cancellation/no-show/refund; caps/stacking; completion/review; check-in evidence/contested fallback và all-arrived vs per-room entitlement boundary; legal/liquidity/dispute recovery. Core scope không được lùi P2 vì config chưa duyệt; block dependent work/release thay vì bịa config.
