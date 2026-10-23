# UI/UX DESIGN SYSTEM

## TARGET screen/component contract — chưa triển khai

**Sprint 2 selection scope (controller review accepted):** the property-detail screen
now supports per-room adult/child allocations, date/payment-mode offer search,
eligible/sold-out plan cards and a local selection summary. It clears results and
selection when inputs change. Offers are advisory and always revalidated; without
dates, the UI/API shows indicative rates and explicitly does not claim live stock.
Checkout/quote consent remains intentionally unavailable until TSK-103 is wired.

**S0-109-v1 FROZEN_DESIGN** [typed DTO/errors](../business/contracts/s0-api-v1.json)/[fixtures](../business/contracts/s0-fixtures-v1.json) are field authority. Whole integer VND, policyVersion/fixtureVersion/quoteHash and immutable consent; explicit quoteExpiresAt separate original booking createdAt+900s/max3hold. Servererrors fieldlabels, no trusted clientamount.090 HCM14/11/1..30/sameday<20 creation/childinteger0..17/everyroomadult; actualevidence/checkoutcompletion/review14days endexclusive frozen. No Hosttap/earlycompletion/calendaronly/zeroamount exception;095activation blocked.

### Payment v2 — FULL_PREPAY và HYBRID_DEPOSIT đều core MVP

[Payment](../business/PaymentBusinessRules.md) bothmodes instant core. Serverallowed cards show totalStay/dueOnline/balanceDueHost Hostcash-bankATCHECKIN, approved20% postdiscount HALF_UP VND, terms/refund/timezone beforeconsent. FullB0; modechange invalidates quote/reconsent. CoverageO>=Cnet, no silentmode/depositincrease/Guestcommissionfee.

Active fixture6.800.000: fullO6.800.000/B0; hybridO1.360.000/B5.440.000. No Guestfee; room7.000.000/platformD200.000, Cgross1.050.000/Cnet850.000 **Host/Admin only**, held510.000hybrid/5.950.000full, Host5.950.000both. Tax/provider0 fixture not actual reviewed contracts; Guest no commission/internalledger. Grossdue/fundedexpense/reserved/earned distinct.

Guest Trips/payment return: badges booking/online/direct/refund riêng. Hybrid CONFIRMED sau đủ online, label “Đã nhận deposit online; còn trả trực tiếp Host”; không “fully paid” vì attempt SUCCESS. Direct receipts ghi “Host xác nhận đã nhận”, partial/outstanding/contested và acknowledgment/dispute controls, không badge bankverified. Refund view tách totalPolicyRefund/platformRefundable/Host direct responsibility/paid/pending và signed SLA business-days/timezone/holidays cấu hình; không hứa3–5 ngày. Pending/late/extra attempts hiển thị support/reconcile, redirect chỉ poll.

Hostops perroom actualAt/ack/private evidence/contested, directreceipt amount/currency/method/time/ref/version/reversal, no bankverified/taptopayout. Balance refusal not occupiedrelease. ALLARRIVED maxverifiedactual+24h/allgates frozen, not acktime/calendar/perroom earlypayout; list platformheld/reservevs earned/directpaid and blockedreasons, no bypass Admincase.

Admin ops: private evidence/reconciliation/refund split, maker-checker/proof/reference before PAID, dispute holds và post-payout recovery cases, audit/version/idempotency conflict. Manual payout/refund không giả API bank automation; no credentials/sensitive PII. A11y keyboard/mobile labels cho mọi amount/mode/status, terms không giấu tooltip. Acceptance TSK-113/114: cả hai mode happy, stale mode/coverage failure, duplicate IPN, partial receipt/correction/contested arrival, refund pool guard và blocked payout; chưa có runtime evidence.

Visual **Airbnb-like**, inventory **Booking-like** là hai trục độc lập, không copy logo/branding. Giữ tokens StayHub màu/font/radius/grid bên dưới. CPN03/04 single-unit legacy chỉ là nền hình thức; target tại đây có ưu tiên cùng [canonical](../business/PropertyInventoryBusinessRules.md) và [API](../architecture/3_API_Design.md).

### Guest / desktop

- Search pill: destination, dates, guests **và rooms**. Guest picker thu adults, children ages và allocation từng phòng; tổng picker khớp selection, không suy capacity từ tổng khách. Mỗi **cơ sở một card**, ảnh4:3, tên/rating/location/classification, không card riêng mỗi type/plan. From-price phải là eligible combination đáp ứng cả stay/occupancy, ghi room count/nights, total stay và inclusion phí/thuế. Không dates: CTA chọn ngày, giá tham khảo có basis, không badge “còn X phòng”.
- Detail gallery ảnh chung + title/host/location/reviews; 7/12 nội dung và 5/12 sticky selection sidebar desktop theo grid hiện có. “Chọn phòng” dùng **room-type cards/accordion**, không bắt buộc bảng Booking dày đặc. Card có ảnh type, tên hạng, beds/capacity, tiện nghi riêng tách tiện nghi chung, quantity selector và plan options. Plan ghi breakfast/payment/cancellation/cutoff/timezone và tổng stay, không ngụ ý mỗi plan có pool riêng; không bắt buộc số phòng vật lý.
- Sidebar items/quantity/allocations/dates/nights/charges/cleaning/taxdisclosure/discount/currency/terms. Deluxe2+Family1/6adults-child7/2nights →7.000.000charges−200.000platformD=**6.800.000 VNDstay**, noGuestbookingfee. Wholehome4bedrooms quantity1 not4rooms. Terms/refund platform5–10businessdays fromeligiblerequest, Hostdirect separate/no promisedsameSLA, REQUESTEDnotPAID/taxcalendarclearance blocked.
- Checkout hiển thị snapshot terms/version, quote expiry và revalidate tất cả items. Đổi dates/guests/allocation/type/plan/quantity/code invalid quote, clear availability cũ và fetch lại; không âm thầm bỏ item/tăng giá. 409 hiển thị khác biệt, alternatives và cần consent quote mới; không partial booking. Chỉ backend booking CONFIRMED mới báo xác nhận; return/poll pending hiển thị “đang xác minh”, late paid có hướng support/refund.

### Mobile / accessibility

Mobile gallery carousel; type cards xếp dọc, controls dễ chạm. Bottom bar ghi total stay + rooms/nights, CTA mở selection/summary sheet; không chỉ giá/đêm. Terms thiết yếu, fee/tax inclusion và cancellation/payment conditions luôn có ở selection/checkout, không chỉ tooltip hay accordion đóng. Sheet có heading/labels, focus trap, Esc/close, trả focus, keyboard thao tác selector/date, aria-live cho result/error; lỗi gắn field, không chỉ màu. Sticky bar có safe-area/padding không che nội dung; gallery có alt và next/prev, không chỉ swipe.

### State matrix nghiệm thu

| State | UI và hành vi |
|---|---|
| No dates | Types giới thiệu, quantity=null, CTA chọn ngày; không stock live |
| Loading | Skeleton/busy label, disable submit; bỏ response cũ khác search key |
| Available | Quantity min mọi đêm, “còn X cho kỳ ở này”, advisory không guarantee |
| Sold out / restriction | Disable offer/type đúng phạm vi, lý do an toàn/đổi ngày; không khóa property vì một type hết |
| Network/server error | Giữ input/retry; không biến error thành soldout/confirmed |
| Stale quote / stock409 | Revalidate mọi items, diff giá/terms, consent lại; không silently charge |
| Payment pending/late paid | Booking/payment/refund badges riêng, polling read-only, support/reconcile |

### Host một facility dashboard

Property content/gallery chung; tabs Types & Plans / Calendar / Bookings / Stats, không đăng lại property cho từng phòng. Type CRUD photos/beds/capacity/base quantity; plans giá/policy và shared-stock hint. Calendar theo type/date có total, OOO, held, confirmed, remaining; stop-sale riêng chặn bán mới, OOO giảm usable capacity. Edit absolute/versioned batch, preview/commitments warning; reject giảm dưới holds+confirmed, không cancel âm thầm. Archive giữ future stays/history/support. Stats distinct booking header, rooms/roomnights và ledger, không duplicate join items, không gọi gross là net.

### Verification contract (TSK-101/107, chưa chạy UI/runtime)

Calendar giữ `remaining = total - OOO - held - confirmed >= 0` ngay khi đóng bán, không đổi remaining thành0 để biểu diễn restriction. Type/day stop-sale disable bán mới mọi plan của type; plan/day stop-sale chỉ disable plan đó, các plan khác vẫn chia sẻ remaining. Offer availableQuantity=0 nếu bị restriction, không đồng nghĩa capacity bằng0. Không release holds/confirmed hoặc chặn conversion hợp lệ của hold cũ chỉ vì stop-sale mới; reject capacity update nếu `total - OOO < held + confirmed`.

Desktop/mobile/keyboard: fixture3rooms6.800.000, Familysecondnight0→conflict whole selection/no partial. No-date stock/staleresponse/planssharepool/wholehome1/noGuestfee/pendingredirectnotconfirmed, consumedcapnotrestored/zerorejected. Docs don't close implementation tasks.

## BASELINE visual tokens / legacy single-unit components

## 1. Color Palette
| Tên Style | Alias | Hex Code | Ứng dụng |
| :--- | :--- | :--- | :--- |
| **Primary Coral** | `color-primary-500` | `#FF6B6B` | Logo StayHub, Nút bấm Primary (Reserve, Confirm & Pay), Sidebar Active State, Badge trạng thái Confirmed |
| **Primary Coral Dark** | `color-primary-600` | `#E85555` | Trạng thái Hover/Pressed của nút Primary |
| **Text Heading** | `color-text-900` | `#111827` | Tiêu đề chính (Tên property, Heading trang), luôn đen đậm để tương phản mạnh |
| **Text Muted** | `color-text-gray` | `#6B7280` | Mô tả phụ, địa chỉ, số đêm, sub-text dưới Heading |
| **Surface White** | `color-bg-white` | `#FFFFFF` | Nền Card property, Nền Form Booking, Nền Navbar |
| **Surface Light Gray** | `color-bg-light` | `#F9FAFB` | Nền tổng thể trang Body Background |
| **Border Subtle** | `color-border` | `#F1E4E4` / `#E5E7EB` | Viền Card property, viền input Search box |
| **Success Green** | `color-success-500` | `#16A34A` | Badge trạng thái Confirmed (bản phụ), Admin Dashboard (`+12%`) |
| **Warning Amber** | `color-warning-500` | `#D97706` | Badge trạng thái Pending |
| **Rating Gold** | `color-rating-500` | `#F59E0B` | Icon sao đánh giá (★) |

### 2. Typography
*   **Font Family:** `Inter` hoặc `Helvetica Neue`
*   **Scale (Hệ tỷ lệ):**
    *   `Display/H1` (Bold 800): **48px** → Dùng cho Tiêu đề Hero Homepage, Tên property trên trang Property Detail.
    *   `H2` (Bold 700): **32px** → Dùng cho Heading section ("Platform Overview", "Trips", "120 stays in Da Nang").
    *   `H3` (SemiBold 600): **20px** → Tên property trên Card danh sách, tên các mục trong Sidebar Dashboard.
    *   `Body Text` (Regular 400): **16px** → Mô tả, nội dung "About this place".
    *   `Caption/Meta` (Regular 400): **14px** → Địa chỉ, số phòng ngủ/giường/khách, ngày Check-in/Check-out.
    *   `Price Big Number` (Bold 700): **28px** → Giá booking trong Price Box, số liệu Dashboard (Total Users, Platform Revenue).

### 1.3. Spacing & Grid (Khoảng cách & Lưới)
*   **Grid System:** 12 Columns, `max-width: 1280px`, Gutter `24px`.
*   **Section Padding:** Main block `48px` (Homepage, Search Results), Admin/Host Dashboard block `32px`.
*   **Border Radius:** `Radius: 12px` cho Card property và Input; `Radius: 8px` cho Button; `Radius: 999px` cho Search Box và Badge trạng thái.
*   **Shadow:** (`border: 1px solid color-border`) cho Cards, Khối nổi (Price Box sticky, Modal): `Y: 8px`, `Blur: 24px`, `Color: rgba(0,0,0,0.08)`.

---

## 3. HỆ THỐNG LƯỚI RESPONSIVE (BREAKPOINTS & GRID SYSTEM)

### 3.1. Desktop & Large Screens (`lg`: 1024px trở lên)
*   **Grid:** Hệ thống lưới 12 cột. Container căn giữa `max-width: 1280px`.
*   **Layout Ứng Xử:**
    *   **Trang chủ (Homepage):** Search box dạng pill nằm ngang 4 ô (Where / Check-in / Check-out / Guests) chồng lên Hero Banner.
    *   **Search Results:** Layout 2 cột cố định — Filter Sidebar bên trái (`3/12` cột) + Property List bên phải (`9/12` cột).
    *   **Property Detail:** Layout 2 cột — Nội dung mô tả bên trái (`7/12`), Price Box **sticky** bên phải (`5/12`), luôn hiển thị khi cuộn trang.
    *   **Admin/Host Dashboard:** Layout cố định Sidebar trái (`240px`) + Content chính co giãn, khối Stats Card xếp ngang 4 cột.

### 3.2. Tablet & iPad (`md`: 768px - 1023px)
*   **Grid:** Hệ thống lưới 8 cột. Margin hai mép viền tối thiểu `24px`.
*   **Layout Ứng Xử:**
    *   Font Size ở `Display/H1`, `H2` thu nhỏ khoảng 25% để không tràn màn hình.
    *   **Search Results:** Filter Sidebar chuyển thành thanh Filter ngang dạng Dropdown/Chip nằm phía trên danh sách (thay vì cột trái cố định).
    *   **Property Detail:** Price Box không còn sticky bên cạnh mà đẩy xuống dưới phần mô tả, giữ nguyên dạng Card bo góc.
    *   **Admin/Host Dashboard:** Sidebar có thể thu gọn lại chỉ hiện Icon (Icon-only Sidebar), khối Stats Card xếp lưới 2x2.

### 3.3. Mobile Devices (`sm`: 320px - 767px)
*   **Grid:** Hệ thống lưới 4 cột. Padding-X ốp viền `16px`.
*   **Layout Ứng Xử:**
    *   **Navbar:** Thu gọn còn Logo + Icon Search + Icon Menu (Hamburger); menu đầy đủ (Explore/Trips/Wishlists/Host your home) trượt ra dạng Bottom Sheet hoặc Full-screen Drawer.
    *   **Search box Homepage:** 4 ô Where/Check-in/Check-out/Guests xếp chồng dọc thành 1 cột, mỗi ô cao `56px`, nút Search full-width ở cuối.
    *   **Property Card (Search Results):** Ảnh full-width phía trên, thông tin (tên, rating, giá) xếp dọc bên dưới thay vì nằm ngang cạnh ảnh.
    *   **Image Gallery (Property Detail):** Lưới ảnh 2x2+1 chuyển thành Carousel vuốt ngang (Swipeable), chấm chỉ số trang (dots indicator) phía dưới ảnh.
    *   **Price Box / Booking Summary:** Chuyển thành thanh cố định (Sticky Bottom Bar) hiển thị Giá + nút "Reserve"/"Confirm & Pay", bấm vào mới mở Bottom Sheet chi tiết giá.
    *   **Admin/Host Dashboard:** Sidebar ẩn hoàn toàn, thay bằng Bottom Navigation Bar hoặc Hamburger Menu; bảng dữ liệu (Recent Bookings, Booking Requests) chuyển từ Table sang danh sách Card xếp dọc, mỗi Card gồm Property + Guest + Amount + Status.

---

## 4. COMPONENT SPECS (CẤU TRÚC COMPONENT CỐT LÕI)

Vẽ các Component (Frame) sau trên Stitch/Figma để dễ dàng nhân bản và tái sử dụng cho các trang khác:

### CPN 01: The Search Navbar (Thanh điều hướng có ô tìm kiếm)
*   **Nền (Fill):** `#FFFFFF`, viền dưới `1px solid color-border`.
*   **Height:** `72px`.
*   **Phân bổ:** Trái là Logo StayHub (icon nhà + ghim định vị, màu `color-primary-500`). Giữa là Search Pill thu gọn (`Anywhere | Any week | Add guests` + icon kính lúp đỏ). Phải là nhóm: `Explore / Trips / Wishlists / Host your home` (Text Link) + Icon Menu (hamburger) + Avatar tròn trong khung viền bo tròn (pill).
*   **Trạng thái đã đăng nhập vs chưa:** Khi chưa login, cụm phải chỉ còn `Host your home` + Icon Globe + Icon Account mặc định (silhouette).

### CPN 02: Stats Big Number Card (Thẻ số liệu lớn — dùng cho Admin/Host Dashboard)
*   Tham chiếu: khối 4 Card ngang hàng trên Platform Overview / Host Dashboard.
*   **Cấu trúc (Auto Layout dọc trong 1 Card bo góc `12px`, nền trắng, viền `color-border`):**
    1. Icon tròn nền nhạt (`color-bg-light`) ở góc trái, kèm Badge tăng trưởng nhỏ góc phải (`+12%`, nền `color-success-500` nhạt, chữ xanh — hoặc nền `color-primary-500` nhạt nếu số liệu trung tính).
    2. Số liệu lớn (`Price Big Number`, 28–32px, Bold, màu `color-text-900`) — VD: `45.2k`, `$2.4M`, `1,245`.
    3. Label mô tả phía dưới (`Caption/Meta`, màu `color-text-gray`) — VD: "Total Users", "Platform Revenue".

### CPN 03: The Property Card (Thẻ chỗ ở — dùng cho Search Results & Featured Properties)
*   Tham chiếu: Card ảnh trong danh sách "120 stays in Da Nang".
*   Tạo Frame, `Radius 12px`, ảnh bo góc trên full-width tỷ lệ `4:3`.
*   **Cấu trúc (Vertical Auto Layout bên dưới ảnh):**
    1. Badge nổi trên góc trái ảnh nếu có (`Superhost` — nền trắng, chữ đen, bo pill).
    2. Icon trái tim (Wishlist/Save) nổi góc phải trên ảnh, nền trắng bo tròn.
    3. Dòng loại hình + khu vực (`Caption`, màu xám) — VD: "Entire villa in Son Tra".
    4. Heading tên property (`H3`, đen đậm) + Icon sao vàng + số rating + số lượng review nằm cùng hàng bên phải.
    5. Dòng tiện ích ngắn gọn (Caption, xám) — "8 guests · 4 bedrooms · 5 beds · 4.5 baths".
    6. Giá tiền (`Price Big Number` cỡ nhỏ 18px, Bold, đen) + đơn vị "/ night" (Caption xám) căn phải; dòng phụ tổng giá gạch chân "₫9,600,000 total".

### CPN 04: Availability Calendar Grid (Lưới lịch kiểm tra ngày trống)
*   Trên Component Booking Page (Check-in/Check-out) và Property Detail (Price Box).
*   Dùng công cụ vẽ Grid của Stitch tạo lưới ô ngày dạng `7 cột x n hàng` (theo tuần), mỗi ô hình vuông `Radius 8px`, kích thước `40x40px`.
*   **Trạng thái ô ngày (3 biến thể Variant):**
    1. `Available` — nền trắng, chữ đen, hover viền `color-primary-500`.
    2. `Selected Range` — nền `color-primary-500` nhạt (`#FFE5E5`), 2 đầu mút (check-in/check-out) tô đậm `color-primary-500` nền, chữ trắng.
    3. `Booked/Disabled` — chữ xám nhạt, có gạch chéo mờ (strike-through), không thể click, tooltip hiển thị "Booked" khi hover.
*   Đây là Component tái sử dụng cho cả luồng Guest chọn ngày và luồng Backend hiển thị trực quan kết quả availability check (theo `flow.md`).
