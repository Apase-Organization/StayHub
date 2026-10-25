# System Overview: StayHub

## 1. Mục tiêu & Tầm nhìn

> **Phân lớp tài liệu:** [inventory TARGET](../business/PropertyInventoryBusinessRules.md) và [Payment v2](../business/PaymentBusinessRules.md) → API/ERD/UI → TaskList/evidence. User đã duyệt instant và **cả FULL_PREPAY/HYBRID_DEPOSIT core MVP**, không Host accept/reject. Phần flow/module single-unit bên dưới là baseline/design lịch sử chưa tái xác minh runtime, không override quyết định mới.

**S0-109-v1 FROZEN_DESIGN** [policy](../business/contracts/s0-policy-v1.json)/[typed API](../business/contracts/s0-api-v1.json): full100%T/hybrid20%postdiscountT HALF_UPwholeVND, Cgross15%prediscountroom+clean/platformD<=Cgross/O>=Cnet, noGuestfee. Active6.800.000 fixture, immutable consent/hash/versions, quoteexpiry distinct original900s/max3hold. Exact verifiedattemptO+allholds instant, directHostB separate. ALLARRIVEDactual+24/allgates, reserve not earned, no monthlyinvoice/onsiteQR/autotransfer. S0designcomplete not sourcecompatible/runtimeclearance; implementation110–114/evidence/cutover/095legal/contracts/signedops/calendar/tax/liquidity stillblocked.

TARGET giữ monolith nhưng tách Property(content/ownership/classification), RoomType(capacity/beds/photos/amenities), RatePlan(price/policy/restrictions), type/date stock, Booking(items/allocation/nightly snapshots). Search/detail/quote/checkout dùng cùng engine availability. Visual Airbnb-like không quyết định inventory Booking-like; physical-room assignment không bắt buộc MVP. Financial state, booking state và refund state độc lập; outbox sau transaction, callback không phụ thuộc session guest.

StayHub là một nền tảng trung gian (marketplace) hai chiều, kết nối:

- **Host** (chủ nhà cho thuê chỗ ở)
- **Guest** (khách du lịch, người đi công tác cần đặt phòng)

Nền tảng **không sở hữu** bất kỳ bất động sản nào. Vai trò của StayHub là:

1. Cho Host đăng tin chỗ ở của mình lên hệ thống.
2. Cho Guest tìm kiếm, so sánh và đặt chỗ ở phù hợp.
3. Đứng giữa xử lý thanh toán, xác nhận đặt phòng, và thu phí dịch vụ trên mỗi giao dịch thành công.

**Tầm nhìn:** Cung cấp một giải pháp đặt phòng trực tuyến đơn giản, tập trung vào trải nghiệm người dùng và cho phép Host quản lý chỗ ở của họ một cách dễ dàng.

## 2. Các bên liên quan (Stakeholders)

| Vai trò | Mô tả | Quyền hạn chính |
| :--- | :--- | :--- |
| **Guest** | Khách thuê, người dùng phổ thông nhất | - Tìm kiếm, lọc, xem chi tiết chỗ ở<br>- Đặt phòng, thanh toán<br>- Xem và quản lý các booking của mình<br>- Viết đánh giá sau khi hoàn thành chuyến đi |
| **Host** | Chủ nhà, người cho thuê chỗ ở | - Đăng tin, quản lý chỗ ở<br>- Xem và xử lý yêu cầu đặt phòng (Accept/Reject)<br>- Theo dõi doanh thu, booking |
| **Admin** | Quản trị hệ thống | - Xem dashboard tổng quan (số users, hosts, bookings, revenue)<br>- Quản lý booking toàn hệ thống<br>- Quản lý tài khoản người dùng (khoá/mở khoá, đổi vai trò) |

## 3. Ngữ cảnh hệ thống (System Context)

```mermaid
flowchart LR
    subgraph "StayHub System"
        App[StayHub Application]
    end

    Guest((Guest)) -->|Truy cập web| App
    Host((Host)) -->|Quản lý, xử lý booking| App
    Admin((Admin)) -->|Giám sát, quản lý| App

    App -->|Lưu thông tin| DB[(PostgreSQL)]
    App -->|Gửi OTP và booking email| Mail[Email Service]
    App -->|OAuth2 login| Google[Google OAuth2]
    App -->|Tạo checkout, nhận IPN| VNPay[VNPay Payment Gateway]
    App -->|Lưu ảnh| Storage[Cloudinary / Local Storage]
```

- **PostgreSQL**: Cơ sở dữ liệu chính lưu trữ tất cả dữ liệu nghiệp vụ (users, properties, bookings, payments, reviews...).
- **Email Service**: Gửi OTP đăng ký và thông báo HTML cho Guest khi trạng thái booking thay đổi (hiện tại dùng SMTP/Gmail, sau có thể tích hợp các dịch vụ như SendGrid).
- **Google OAuth2**: Cho phép đăng nhập/đăng ký nhanh bằng tài khoản Google, tự provision user nội bộ với role `GUEST`.
- **VNPay Payment Gateway**: Xử lý thanh toán VND qua checkout/QR, redirect user về StayHub và gửi IPN để backend xác nhận thanh toán.
- **Storage Service**: Lưu trữ ảnh đại diện, ảnh property (dùng Cloudinary cho production, LocalStorage cho development).

## 4. Các ràng buộc & yêu cầu chất lượng (Quality Goals)

### 4.1. Phi chức năng (Non-functional Requirements)

| Yêu cầu | Mức độ | Mô tả |
| :--- | :--- | :--- |
| **Hiệu năng** | Trung bình | Trang chủ, tìm kiếm cần tải trong < 2 giây cho 100 người dùng đồng thời. |
| **Bảo mật** | Cao | Mật khẩu được mã hóa BCrypt. Session-based authentication. Phân quyền rõ ràng (GUEST, HOST, ADMIN). |
| **Khả dụng** | Cao (MVP) | Ứng dụng được đóng gói Docker, dễ dàng khôi phục. |
| **Khả năng mở rộng** | Trung bình | Thiết kế module theo tính năng (feature-based) để dễ dàng thêm tính năng mới sau. |
| **Dễ bảo trì** | Cao | Code tuân thủ quy chuẩn (rules.md), sử dụng DTO tách biệt Entity. |

### 4.2. Ràng buộc kỹ thuật

- Sử dụng **Java 21** và **Spring Boot 3.3.2**.
- Cơ sở dữ liệu **PostgreSQL 16**.
- Frontend sử dụng **Thymeleaf** + **Tailwind CSS** + **Alpine.js/htmx**.
- Quản lý schema bằng **Flyway**.
- Đóng gói và triển khai bằng **Docker**.
- Ứng dụng là **Monolith**, không dùng microservices ở giai đoạn MVP.

## 5. Tổng quan luồng nghiệp vụ (Business Flow)

Luồng chính của Guest từ lúc truy cập đến khi hoàn thành đánh giá:

```mermaid
flowchart TD
    A[Truy cập Homepage] --> B[Tìm kiếm chỗ ở]
    B --> C[Xem kết quả tìm kiếm]
    C --> D[Xem chi tiết property]
    D --> E[Chọn ngày, số khách]
    E --> F[Nhập mã giảm giá nếu có]
    F --> G[Backend tính lại giá]
    G --> H[Redirect VNPay]
    H --> I[VNPay thanh toán thành công]
    I --> J[IPN xác minh payment]
    J --> K[Booking CONFIRMED]
    K --> L[My Bookings]
    L --> M[Hoàn thành chuyến đi]
    M --> N[Booking COMPLETED]
    N --> O[Guest viết Review]
```

Sơ đồ trên là instant proposal lịch sử, không phải target triển khai. Chi tiết target tại [canonical](../business/PropertyInventoryBusinessRules.md); màn hình demo tại [DemoSystem](0_DemoSystem.md).

## 6. Các module chính (High-level Modules)

```mermaid
flowchart TB
    subgraph "StayHub Modules"
        Auth[Authentication & Authorization]
        User[User Management]
        Property[Property Management]
        Search[Search & Filter]
        Booking[Booking Management]
        Payment[Payment Processing]
        Discount[Discount Management]
        Review[Review Management]
        Host[Host Dashboard]
        Admin[Admin Dashboard]
        Notification[Notification Service]
        Storage[File Storage]
    end

    Auth --> User
    User --> Property
    Property --> Search
    Property --> Booking
    Discount --> Booking
    Booking --> Payment
    Booking --> Review
    Booking --> Notification
    Host --> Property
    Host --> Booking
    Admin --> User
    Admin --> Booking
    Property --> Storage
```

- **Auth**: Xác thực, phân quyền, đăng ký bằng OTP email, đăng nhập Google OAuth2.
- **User**: Quản lý thông tin người dùng.
- **Property**: Quản lý cơ sở lưu trú, hạng phòng (`room_types`), gói giá (`rate_plans`), ảnh, tiện nghi và tồn kho mở bán theo ngày (`type_day_inventory`).
- **Search**: Tìm kiếm theo ngày, số phòng, số khách; kiểm tra availability trên từng hạng phòng, lọc, sắp xếp, phân trang.
- **Booking**: Quản lý đặt phòng đa phòng (`booking_items`), phân bổ khách (`room_allocations`), snapshot giá từng đêm (`nightly_snapshots`), Dual-mode payment (Full/Hybrid), kiểm tra trùng lịch và giữ chỗ 15 phút.
- **Payment**: Xử lý thanh toán VNPay theo mode, verify IPN, polling trạng thái sau redirect, sổ cái tài chính (`ledger_journals`), biên lai thu tiền mặt trực tiếp (`direct_receipts`).
- **Discount**: Validate promo code, tính discount snapshot, chỉ tăng usage sau payment success.
- **Review**: Đánh giá của Guest.
- **Host**: Dashboard cho Host.
- **Admin**: Dashboard cho Admin.
- **Notification**: Gửi email OTP và email booking status dạng HTML có plain-text fallback.
- **Storage**: Lưu trữ ảnh.

## 7. Công nghệ sử dụng

Xem chi tiết [TechStack](2_TechStack.md)

## 8. Tài liệu liên quan

- [Luồng nghiệp vụ chi tiết](../architecture/0_DemoSystem.md)
- [Quy chuẩn làm việc](../contributors/Rules.md)
- [Thiết kế cơ sở dữ liệu](../database/1_DomainOverview.md)
- [Thiết kế API](3_API_Design.md)
- [Hướng dẫn bắt đầu](../contributors/FirstStep.md)
