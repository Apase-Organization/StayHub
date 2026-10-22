# TỔNG QUAN NGHIỆP VỤ DỰ ÁN STAYHUB

**S0-109-v1 FROZEN_DESIGN:** [policy](contracts/s0-policy-v1.json)/[fixtures](contracts/s0-fixtures-v1.json)/[typed API](contracts/s0-api-v1.json) and [finalhandoff](S0DecisionLog.md#s0-final-handoff--tsk-109) supersede historical pending109 wording below. All eight S0 design deliverables complete; runtime/sourceCompatibility none and095actual legal/contracts/tax/calendar/provider/signedops/liquidity clearance BLOCKED. Active6.800.000 fixture and approved20%/15%/platformD/Cnetcoverage/noGuestfee; wholeVND/immutable versions+hash+consent. No moneyactivation config or productionproof.

> **TARGET MVP, chưa triển khai (2026-10-23):** [PropertyInventoryBusinessRules.md](PropertyInventoryBusinessRules.md) là canonical inventory; [PaymentBusinessRules.md](PaymentBusinessRules.md) là canonical tài chính. User đã duyệt instant và **cả FULL_PREPAY + HYBRID_DEPOSIT trong core MVP**. Approval nghiệp vụ không phải evidence source/runtime; lịch sử paid→PENDING/host approval vẫn phải tái xác minh. Return/poll chỉ đọc.

## 1. StayHub là gì?

**Định vị TARGET:** phong cách thị giác Airbnb (gallery/card, khoảng trắng, sidebar chọn phòng) + nghiệp vụ inventory Booking-like (một cơ sở có nhiều hạng/quantity, plans chia sẻ stock). Không đồng nhất giao diện với schema hay chính sách nền tảng khác. Inventory boundaries đã được PO duyệt tại canonical **S0-097-v1**: exclusive WHOLE_PLACE/ROOM_BASED, interchangeable pool/shared plans, whole-place quantity1, một property/date range/VND và hủy toàn booking; không linked sale/partial cancellation. Onboarding Host không được suy ra từ việc user đăng ký.

StayHub là một **nền tảng trung gian (marketplace) hai chiều**, kết nối:

- **Người có chỗ ở cho thuê** (gọi là **Host**) — có thể là chủ căn hộ, villa, homestay, khách sạn nhỏ...
- **Người cần thuê chỗ ở** (gọi là **Guest**) — khách du lịch, người đi công tác, cần đặt phòng theo ngày.

StayHub **không sở hữu** bất kỳ bất động sản nào. Vai trò của nền tảng là:
1. Cho Host đăng tin chỗ ở của mình lên hệ thống.
2. Cho Guest tìm kiếm, so sánh, và đặt chỗ ở phù hợp.
3. Đứng giữa xử lý thanh toán, xác nhận đặt phòng, và thu phí dịch vụ trên mỗi giao dịch thành công.

## 2. Ba vai trò chính trong hệ thống

### 2.1. Guest (Khách thuê)
Là người dùng phổ thông nhất, có thể:
- Duyệt trang chủ, tìm kiếm chỗ ở theo địa điểm, ngày nhận/trả phòng, số khách.
- Lọc và sắp xếp kết quả tìm kiếm (theo giá, loại hình, số phòng ngủ, tiện nghi, đánh giá).
- Xem chi tiết một chỗ ở (ảnh, mô tả, tiện nghi, vị trí, đánh giá của khách trước, giá).
- Đặt phòng: chọn ngày, số khách, xem tổng tiền, xác nhận và thanh toán.
- Theo dõi các chuyến đi của mình: đang chờ, đã xác nhận, đã hoàn thành, đã huỷ.
- Huỷ một đặt phòng (nếu còn ở trạng thái cho phép huỷ).
- Viết đánh giá sau khi chuyến đi hoàn thành.

### 2.2. Host (Chủ nhà)
Là Guest đã đăng ký thêm vai trò cho thuê, có thể:
- Tạo một property/cơ sở; chọn WHOLE_PLACE hoặc ROOM_BASED; quản lý room types, quantity, capacity/ảnh/tiện nghi, plan/giá và calendar theo type. Whole-place là một type quantity1; bedrooms chỉ mô tả.
- Quản lý danh sách chỗ ở của mình.
- Theo dõi doanh thu, booking đã thanh toán/đã xác nhận.
- Host approval là lịch sử cần tái xác minh; TARGET đã duyệt instant, không có accept/reject sau đủ thanh toán online. Host vận hành check-in/evidence, direct receipts và disputes đúng quyền.

### 2.3. Admin (Quản trị hệ thống)
Là đội ngũ vận hành nền tảng, có thể:
- Xem tổng quan toàn hệ thống: tổng số người dùng, số Host đang hoạt động, tổng doanh thu nền tảng, số booking phát sinh mỗi ngày.
- Xem và quản lý toàn bộ booking trên hệ thống (tìm kiếm theo mã booking/tên khách, lọc theo trạng thái).
- Quản lý người dùng: khoá tài khoản vi phạm, đổi vai trò nếu cần.
- (Mở rộng) xuất báo cáo, theo dõi các chỉ số tăng trưởng.

---

## 3. Vòng đời nghiệp vụ tổng thể (Guest POV)

Đây là hành trình chính mà một Guest trải qua khi sử dụng StayHub, từ lúc vào web đến lúc để lại đánh giá:

1. **Truy cập trang chủ** — thấy ô tìm kiếm, các điểm đến phổ biến (Hồ Chí Minh, Đà Nẵng, Đà Lạt, Nha Trang, Hà Nội), các chỗ ở nổi bật, các loại hình phổ biến.
2. **Tìm kiếm** — nhập địa điểm, ngày nhận phòng, ngày trả phòng, số khách.
3. **Xem kết quả tìm kiếm** — danh sách các chỗ ở phù hợp, có thể lọc (giá, loại hình, số phòng ngủ, tiện nghi, đánh giá) và sắp xếp (giá tăng/giảm, đánh giá cao nhất), có phân trang.
4. **Xem chi tiết chỗ ở** — chọn dates/adults/children ages/rooms để xem available types/plans/quantity và tổng stay; trước dates chỉ giới thiệu indicative types, không claim live stock. Hết phòng hiển thị sold out/alternatives.
5. **Bấm "Đặt phòng"** → chuyển sang trang đặt phòng, điền/xác nhận thông tin khách, nhập mã giảm giá nếu có, xem bảng tổng hợp giá.
6. **Chọn payment mode hợp lệ của plan/quote** — full thu T online; hybrid thu deposit bắt buộc online, B còn lại trực tiếp Host cash/bank tại check-in. Backend snapshot mode/config/policy, tạo `PENDING_PAYMENT` và atomic all-item hold, checkout VNPay chỉ `dueOnline`.
7. **VNPay xác nhận qua IPN** — verify signature/merchant/reference/currency/result và amount theo **dueOnline/attempt**, không mặc định booking total; expiry/duplicates serialize.
8. **CONFIRMED instant** sau verified required online amount và hold hợp lệ, cả hai mode. Hybrid không chờ B để confirm; receipt trực tiếp là Host attested, không bank/provider verified. Email phân biệt online paid và direct outstanding.
9. **Guest có thể huỷ booking** ở trạng thái CONFIRMED theo rule hệ thống → booking chuyển sang **CANCELLED**.
10.090actual evidence perroom Host+Guestack/auditedAdmin frozen; ALLARRIVED maxactual+24h/allgates, completioncheckout11+actualstay/resolvedcase, calendar alone insufficient; runtime pending.
11. Khi booking đã COMPLETED, Guest có thể **viết đánh giá (Review)** cho chỗ ở đó — đánh giá này sẽ hiển thị công khai ở trang chi tiết chỗ ở, góp phần vào điểm rating trung bình.

Toàn bộ vòng đời trạng thái của một booking có thể tóm tắt như sau:

```
PENDING_PAYMENT ──(verified dueOnline + active hold)──► CONFIRMED ──(guarded stay evidence)──► COMPLETED ──► REVIEW
       │                                                 │
       └──(worker guarded final-failure/expiry)──► CANCELLED │
                                                         └──(Guest Cancel)──► CANCELLED
```

## 4. Mô hình kinh doanh & dòng tiền

StayHub vận hành theo **commission hợp đồng trên giao dịch**; không thu commission hằng tháng. Bảng sau là **legacy minh họa**, không phải target cộng thêm guest service fee bên cạnh host commission:

| Thành phần | Ý nghĩa | Ai hưởng |
|---|---|---|
| **Giá phòng × số đêm** | Doanh thu chính của Host | Host |
| **Phí dọn dẹp (Cleaning fee)** | Chi phí cố định Host đặt ra cho mỗi lượt khách | Host |
| **Phí dịch vụ StayHub (Service fee)** | Phí nền tảng thu trên mỗi giao dịch thành công | StayHub |
| **Mã giảm giá (Discount)** | Khoản giảm trừ theo campaign/promo code | StayHub/marketing budget |

→ **S0-086/109 FROZEN PRODUCT DESIGN, not legal clearance:** Sroom+clean+reviewedtax/noGuestfee; Cgross15%prediscountroom+clean excludingtax/platformD<=Cgross/Cnet=Cgross−D/T=S−D. HybridO20%THALF_UP/B=T−O/O>=Cnet, HostS−Cgross=heldO−Cnet+B; fullheldT−Cnet. Actualproviderfee platformexpense.1m/D0→C150k/hybridO200k/B800k/held50k/Host850k.7m/D200k→T6.8m/Cgross1.05m/Cnet850k/O1.36m/B5.44m/held510k/fullheld5.95m/Host5.95mboth. All active109projections replaced, no oldfee. No depositrevenue, reserve untilcompleted/resolvedobligations/reviewedearning. ALLARRIVEDactual+24/allgates/noautotransfer, OperationsFinance dailyreconcile/reserve/recovery/differenthumanAdminmakerchecker/bankproof.095contracts/legal/signedops/calendar/tax/liquidity/runtime BLOCKED, taxunsetdisabled, no fabricatedsignatures. [Payment](PaymentBusinessRules.md).

## 5. Ba mảng nghiệp vụ cốt lõi

### 5.1. Property & Search (Chỗ ở & Tìm kiếm)
**TARGET:** Property là cơ sở/content/host/address/review aggregation, không phải một đơn vị inventory. Classification độc lập sale mode. RoomType là hạng bán được có quantity; RatePlans chia sẻ stock của type. Pool phải bảo đảm ở xuyên stay không đổi phòng; PhysicalRoom assignment là mở rộng.

Search trả property duy nhất và eligible offer đáp ứng mọi đêm/room allocation, không loại cả property chỉ vì một type có booking. From-price ghi rõ basis occupancy/rooms/nights/fees và stay total; không dùng tổng capacity thay kiểm tra từng phòng.

### 5.2. Booking & Payment (Đặt phòng & Thanh toán)
Hai quy tắc quan trọng nhất:

- **Inventory TARGET:** theo type/date `[check-in, check-out)`, remaining = total - blocked - held - confirmed không âm; dùng min remaining mọi đêm. Nhiều bookings cùng property hợp lệ nếu còn stock. Atomic hold tất cả items hoặc rollback, không dựa riêng property overlap legacy.
- **Tính giá TARGET:** header một property/currency/date range, items type/plan/quantity + allocation từng phòng và nightly price/fee/tax/policy snapshots; sums/rounding exact. MVP hủy toàn booking, partial item cancellation là tương lai.
- **Discount:** Mã giảm giá chỉ được dùng để preview trên FE. Khi Guest submit booking, backend phải tính lại subtotal, validate lại code, snapshot `discount_amount`, và chỉ tăng `used_count` sau khi VNPay xác nhận payment success.

Payment ghi online attempts và provider-verified collection riêng direct Host-attested receipts. `SUCCESS` của attempt không có nghĩa cả booking fully paid. `returnUrl` chỉ đọc; verified IPN đúng dueOnline/active hold mới atomic confirm. Unpaid/refused/disputed direct balance tại check-in không tự release occupied rooms. Refund state độc lập cancellation; platform không hoàn quá online collected trừ paid/refund reserves, Host chịu phần direct theo policy. Manual refund/payout cần proof/audit/maker-checker; platform refund SLA5–10businessdays đã duyệt tại S0-084-v1, calendar/holiday clearance vẫn chặn release.

### 5.3. Review & Notification (Đánh giá & Thông báo)

**Approved refund S0-084-v1:** >=48h before scheduled check-in or Host inability → full actually collected money refund by online/Host-direct source, not uncollected B. Late/no-show room money nonrefundable, unused net-after-discount cleaning refundable only absent actual check-in; post-arrival incident is Admin case. Platform portion SLA **5–10 business days from eligible request**, Host direct SLA not promised without contract; REQUESTED is not PAID. No automatic B debit/monthly invoice, no mixed refund pools/double discount return. Authoritative calendar/holiday and legal/tax clearance block paid activation; tax config unset/disabled pending review. Canonical detail/examples: Payment section5a, not runtime certification or109 config completion. This approved range supersedes prior unspecified-SLA wording above.
- **S0-090-v1 approved:** HCM UTC+7/check-in14:00/checkout11:00,1–30nights; same-day original creation<20:00, >=20 rejects same-day only, retry keeps original15min expiry. Adults18+/eachroom>=1adult; child integer0–17/all count occupancy, no child pricing/extra beds. Actual per-room Host+Guestack or audited Admin evidence, no future/prebooking actualAt/Host-tap-only bypass. ALLARRIVED maxactual+24h/all gates; completion only at/after scheduled checkout11:00+actual stay/no unresolvedcase, not early checkout. Actual Guest review one/booking at completedAt<=now<completedAt+14days, no-show denied. Adjacent checkout11→checkin14 valid without turnover night. Canonical Inventory3a, implementation pending.
- **Notification** được gửi tự động mỗi khi trạng thái booking thay đổi quan trọng: thanh toán thành công và booking được xác nhận (CONFIRMED), hoặc booking bị huỷ (CANCELLED) — giúp Guest nắm được tình trạng đặt phòng mà không cần chủ động vào lại hệ thống kiểm tra.


## 6. Vai trò của Host trong vận hành (instant đã duyệt, pending delivery)

Trong TARGET đã duyệt, Host vận hành property/types/plans/calendar, theo dõi booking, attest direct receipts và actual arrivals với evidence. Inventory boundaries frozen S0-097-v1, không khẳng định flow/source đã chạy; các implementation và financial-policy gates vẫn độc lập.

- Booking mới bắt đầu ở trạng thái **PENDING_PAYMENT** trong lúc chờ VNPay xác nhận.
- Sau IPN verified đủ dueOnline và active hold, booking chuyển thẳng **CONFIRMED** ở cả hai mode, không phụ thuộc direct balance.
- Host Dashboard vẫn hiển thị booking theo property để Host theo dõi lịch đặt và doanh thu.
- Không Host accept/reject trong TARGET. Payout eligible actual check-in +24h, không Host tap hay scheduled date, không guaranteed automatic transfer; disputed/post-payout recovery theo canonical.

## 7. Vai trò của Admin trong vận hành

Admin đóng vai trò giám sát toàn hệ thống chứ không tham gia trực tiếp vào từng giao dịch. Admin Dashboard cho thấy bức tranh toàn cảnh: tổng người dùng, tổng Host đang hoạt động, tổng doanh thu nền tảng, số booking phát sinh trong ngày, và một bảng theo dõi các booking gần nhất kèm trạng thái (Confirmed/Pending/Cancelled) để phát hiện sớm bất thường (ví dụ tỷ lệ huỷ tăng cao ở một khu vực). Admin cũng có quyền quản lý tài khoản người dùng vi phạm chính sách nền tảng.

## 8. Phân quyền truy cập (business rule tổng quát)


| Khu vực | Ai được truy cập |
|:-:|:-:|
| Trang chủ, tìm kiếm, xem chi tiết property | Công khai (không cần đăng nhập) |
| Đặt phòng, xem "My Bookings", viết review | Bắt buộc đăng nhập, vai trò Guest (hoặc bất kỳ user nào đã đăng nhập) |
| Đăng property, xem booking request, dashboard host | Bắt buộc đăng nhập, vai trò Host |
| Dashboard quản trị, quản lý user/booking toàn hệ thống | Bắt buộc đăng nhập, vai trò Admin |


## 9. Vì sao thiết kế theo hướng này? (Lý do nghiệp vụ)

- **VNPay trước cho thanh toán VND:** giúp flow demo sát thực tế người dùng Việt Nam hơn, hỗ trợ bank/card/QR và có IPN để xác minh giao dịch.
- **Tự xác nhận sau payment success:** giảm friction cho Guest; booking chỉ được CONFIRMED khi backend đã verify checksum và amount từ VNPay.
- **Discount snapshot:** giúp audit rõ ràng số tiền trước/sau giảm giá, đồng thời tránh lệ thuộc vào campaign có thể thay đổi sau này.
- **Payment split:** Guest totalStay/dueOnline/balanceDueHost/refund; HostAdmin Settlement scoped, exact109productconfig frozen, actualprovider/tax/legal permissions absent; no depositrevenue/Guestfee.
- **Review TARGET:** tối đa một review/property cho mỗi completed booking bất kể quantity, có thể kèm type context snapshot; đây là policy StayHub, không khẳng định chính sách các nền tảng giống nhau.

## 10. Định hướng mở rộng

Sau khi project hoạt động ổn định, một số hướng phát triển thêm về mặt nghiệp vụ:

- **Core release gates, không optional:** multi-room TSK-097–108 và hai payment modes TSK-109–114 cùng remediation/P0 phải đạt TSK-095; không feature flag hybrid sang Post-MVP, chưa có production-readiness evidence.
- **MoMo/ZaloPay:** thêm provider khác sau khi abstraction VNPay ổn định.
- **Wishlist:** cho Guest lưu lại chỗ ở yêu thích để quay lại đặt sau, tăng tỷ lệ chuyển đổi (conversion).
- **Hold expiry core:** originalcreated+900s/max3creations/noextension/active t<expiry, worker guardedfinalization/allitems-caps once; latepaid reconcile/norevive, refundstate separate.
- **Rating trung bình được cache** thay vì tính lại mỗi lần load trang, phục vụ khi lượng review lớn.
- **Báo cáo xuất file (CSV/PDF) cho Admin:** phục vụ nhu cầu báo cáo định kỳ, đối soát doanh thu với Host.
- **Thông báo real-time (WebSocket):** thay vì chỉ gửi email, giúp Host/Guest nhận cập nhật trạng thái tức thời ngay trên giao diện.
- **Rate limiting cho đăng nhập:** chống tấn công dò mật khẩu bằng brute-force để bảo vệ tài khoản người dùng.
