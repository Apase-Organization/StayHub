# StayHub — Task List

## Target Project Structure

```txt
StayHub/
├── .github/
├── docs/                        
├── src/main/
│   ├── java/com/stayhub/
│   │   ├── StayHubApplication.java
│   │   ├── common/          {exception, response, validation, util}
│   │   ├── config/          SecurityConfig, MailConfig, StorageConfig
│   │   ├── auth/            AuthController, AuthService, UserPrincipal, dto/
│   │   ├── user/            User, UserRepository, UserService, dto/
│   │   ├── property/        Property, PropertyController/Service/Repository/Mapper, dto/
│   │   ├── search/          SearchController, SearchService, SearchRepository, dto/
│   │   ├── booking/         Booking, BookingStatus, BookingController/Service/Repository, BookingPriceService, dto/
│   │   ├── payment/         Payment, PaymentStatus, PaymentMethod, PaymentService, MockPaymentService, dto/
│   │   ├── review/          Review, ReviewController, ReviewService, ReviewRepository
│   │   ├── host/            HostController, HostService, dto/
│   │   ├── admin/           AdminController, AdminService
│   │   ├── notification/    NotificationService, EmailNotificationService
│   │   └── storage/         StorageService, LocalStorageService, CloudinaryStorageService
│   └── resources/
│       ├── templates/
│       │   ├── fragments/   navbar.html, footer.html
│       │   ├── home/        index.html
│       │   ├── property/    search-results.html, property-detail.html
│       │   ├── booking/     booking.html, payment.html, booking-detail.html
│       │   ├── auth/        login.html, register.html
│       │   ├── host/        dashboard.html
│       │   └── admin/       bookings.html
│       ├── static/{css,js,images,favicon.ico}
│       ├── db/migration/    V1__create_users.sql, V2__normalize_user_emails.sql, V3__create_properties.sql, ...
│       ├── application.yml
│       ├── application-local.yml
│       └── application-docker.yml
├── .dockerignore / .gitignore / Dockerfile / docker-compose.yml / pom.xml / tailwind.config.js
```
---

# SPRINT 0 — NỀN TẢNG CHUNG

- [x] **TSK-001** `[PM/Setup]` Khởi tạo Git repo + branch `main`/`dev`, add Collaborator cho cả 3 người. *(Estimate: 0.5h · Priority: Urgent)*

- [x] **TSK-002** `[BE_Core]` Khởi tạo `pom.xml` (Spring Boot 3.3.2, Java 21) + `StayHubApplication.java`. *(Estimate: 1h · Priority: Urgent)*

- [x] **TSK-003** `[Infra]` `Dockerfile` (multi-stage Maven build → JRE runtime) + `docker-compose.yml` (Postgres 16). *(Estimate: 1.5h · Priority: Urgent · Blocking)*

- [x] **TSK-004** `[Infra]` `.gitignore`, `.dockerignore`, tách `application.yml` / `application-local.yml` / `application-docker.yml`. *(Estimate: 1h · Priority: Urgent · Blocking)*

- [x] **TSK-005** `[FE_Core]` Setup `tailwind.config.js` build pipeline (npm/CLI), output CSS vào `static/css/`. *(Estimate: 1h · Priority: High)*

- [x] **TSK-006** `[BE_Core]` Package `common/response`: tạo `ApiResponse<T>` chuẩn theo `docs/contributors/Rules.md` mục 4. *(Estimate: 0.5h · Priority: Urgent · Blocking)*

- [x] **TSK-007** `[BE_Core]` Package `common/exception`: tách `ApiExceptionHandler` (`@RestControllerAdvice`) và `MvcExceptionHandler` (`@ControllerAdvice`) + các exception dùng chung. *(Estimate: 1h · Priority: Urgent · Blocking)*

- [x] **TSK-008** `[BE_Core]` Package `common/validation` + `common/util`: custom validator (vd: check-in phải trước check-out), `DateUtil`, `PriceUtil`. *(Estimate: 1h · Priority: Medium)*

- [x] **TSK-009** `[BE_Core]` `common/entity/BaseEntity` (id, createdAt, updatedAt) — dùng chung cho mọi entity*(Estimate: 0.5h · Priority: Urgent · Blocking)*

- [x] **TSK-010** `[DB]` Migration `V1__create_users.sql`:  bảng users (id, email, password_hash, full_name, phone, role, status, created_at, updated_at) . *(Estimate: 1h · Priority: Urgent · Blocking)*

- [x] **TSK-011** `[FE_Core]` `templates/fragments/navbar.html`, `footer.html` (bản khung, chưa cần hoàn thiện logic login/logout). *(Estimate: 1h · Priority: Medium)*


---

# TRACK A — AUTH · USER · CORE CONFIG · ADMIN

- [x] **TSK-012** `[BE_Config]` `config/SecurityConfig.java`: session-based auth, `PasswordEncoder` (BCrypt), phân quyền theo path (`/host/**` → HOST, `/admin/**` → ADMIN). *(Estimate: 2.5h · Priority: Urgent · Blocking cho Dev B & Dev C)*

- [x] **TSK-013** `[BE_User]` `user/User.java` (entity extends BaseEntity), `user/dto/` (UserResponse, UpdateProfileRequest). *(Estimate: 1h · Priority: Urgent · Blocking)*

- [x] **TSK-014** `[BE_User]` `UserRepository`, `UserService` (đăng ký, tìm theo email, đổi mật khẩu). *(Estimate: 1.5h · Priority: Urgent)*

- [x] **TSK-015** `[BE_Auth]` `UserPrincipal` + `CustomUserDetailsService` tích hợp Spring Security; `AuthService` là boundary cho đăng ký tài khoản. *(Estimate: 1.5h · Priority: Urgent · Blocking)*

- [x] **TSK-016** `[BE_Auth]` `AuthController`: `GET/POST /register`, `GET /login`; Spring Security xử lý `POST /login`, `POST /logout`; có `RegisterRequest`. *(Estimate: 2h · Priority: Urgent)*

- [x] **TSK-017** `[FE_Auth]` `templates/auth/login.html`, `register.html`. *(Estimate: 2h · Priority: High)*

- [x] **TSK-018** `[FE_Core]` Hoàn thiện `fragments/navbar.html` với `sec:authorize` (hiện Profile/My Booking/Logout khi đã login, ẩn khi chưa). Hiện Account/Profile vẫn là placeholder. *(Estimate: 1h · Priority: Medium)*

- [x] **TSK-019** `[DB]` Xác nhận không cần migration quyền hạn riêng: role, status và constraints đã có trong `V1__create_users.sql`. *(Estimate: 0.5h · Priority: Low)*

- [x] **TSK-020** `[BE_Admin]` `admin/AdminController.java`, `AdminService.java`: dashboard tổng quan với dữ liệu thật (tổng users, active hosts, bookings, revenue). Hiện bookings và revenue vẫn là mock. *(Estimate: 3h · Priority: Medium)*

- [x] **TSK-021** `[FE_Admin]` `templates/admin/bookings.html`: Stats Cards + bảng booking toàn hệ thống với search, filter status và pagination. Hiện bảng booking vẫn là placeholder. *(Estimate: 2.5h · Priority: Medium)*

- [x] **TSK-022** `[BE_Admin]` Quản lý user/host từ admin (khoá tài khoản, đổi role). Lock/unlock đã có; role-change chưa triển khai. *(Estimate: 2h · Priority: Low)*

## FOUNDATION HARDENING

- [x] **TSK-053** `[Testing]` Thêm JUnit/Mockito/Spring Security Test/Testcontainers và PostgreSQL 16 integration-test foundation.
- [x] **TSK-054** `[Infra]` Chuẩn hóa config local/docker bằng environment variables, bỏ credential khỏi source và hoàn thiện full-stack Compose.
- [x] **TSK-055** `[Security]` Bật CSRF cho session API; chuẩn hóa JSON 401/403 và CSRF response cho `/api/**`.
- [x] **TSK-056** `[BE_User]` Chuẩn hóa email, xử lý duplicate race, thêm `V2__normalize_user_emails.sql` và DTO boundary cho Admin view.
- [x] **TSK-057** `[BE_Config]` Bootstrap Admin opt-in bằng environment variables, không tự nâng quyền account tồn tại.
- [x] **TSK-058** `[BE_Core]` Tách exception handling cho REST API và Thymeleaf MVC.
- [x] **TSK-059** `[BE_Auth]` Bổ sung đăng ký bằng OTP email, username unique, verify-registration page, và HTML email template có fallback text.
- [x] **TSK-060** `[BE_Auth]` Tích hợp Google OAuth2 login, tự provision user `GOOGLE`, sửa principal để booking flow dùng được OAuth account.
- [x] **TSK-061** `[BE_Notification]` Bật Gmail SMTP qua `.env`, gửi booking status email HTML khi Host accept/reject hoặc Guest cancel.
- [x] **TSK-062** `[Docs/API]` Rà soát endpoint backend với template/frontend; ghi chú endpoint alias/future/external API trong docs.

---

# TRACK B — PROPERTY · SEARCH · HOST · STORAGE

- [x] **TSK-023** `[DB]` Migration `V3__create_properties.sql`: bảng `properties` (bao gồm `cleaning_fee`, `rating_avg`) + `V4__create_property_images.sql` + `V5__create_amenities.sql` + `V6__create_property_amenities.sql`. `V2` đã dành cho chuẩn hóa email user. *(Estimate: 2h · Priority: Urgent)*

- [x] **TSK-024** `[BE_Property]` `Property`, `PropertyImage`, `Amenity`, enums và Property/Image/Amenity DTOs. *(Estimate: 2h · Priority: Urgent)*

- [x] **TSK-025** `[BE_Property]` `PropertyRepository`, `PropertyService`, `PropertyMapper` (entity ↔ dto, tránh trả Entity trực tiếp theo `docs/contributors/Rules.md`). *(Estimate: 2h · Priority: Urgent)*

- [x] **TSK-026** `[BE_Storage]` `storage/StorageService.java` (interface) + `LocalStorageService.java` (lưu đĩa cục bộ trước, dùng khi dev). *(Estimate: 2h · Priority: High)*

- [x] **TSK-027** `[BE_Storage]` `storage/CloudinaryStorageService.java` + `config/StorageConfig.java` (bean chọn implementation theo `app.upload.use-cloudinary`). *(Estimate: 2h · Priority: Medium)*

- [x] **TSK-028** `[BE_Property]` `HomeController` phục vụ `GET /`; `PropertyController` và REST controller phục vụ public detail, chỉ trả listing `ACTIVE`. *(Estimate: 1.5h · Priority: Urgent)*

- [x] **TSK-029** `[FE_Home]` `templates/home/index.html`: search box (Where/Check-in/Check-out/Guests theo UI Design System), popular destinations, featured properties, popular categories — responsive (search box chồng dọc trên mobile). *(Estimate: 2.5h · Priority: Urgent)*

- [x] **TSK-030** `[BE_Search]` `search/SearchController.java`, `SearchService.java`, `SearchRepository.java`, `search/dto/SearchCriteria`: `GET /properties?location=&checkIn=&checkOut=&guests=` + filter (price, type, bedrooms, amenities, rating) + sort + pagination. *(Estimate: 3.5h · Priority: Urgent)*

- [x] **TSK-031** `[FE_Property]` `templates/property/search-results.html` filter sidebar (Price Range slider, Property Type checkboxes, Bedrooms/Beds/Bathrooms, Amenities, Rating) + property list (Property Card component) + sort dropdown + pagination — responsive (filter chuyển thành dropdown trên mobile) *(Estimate: 3h · Priority: Urgent)*

- [x] **TSK-032** `[FE_Property]` `templates/property/property-detail.html`: gallery ảnh (main + thumbnail grid, responsive → carousel trên mobile), description, amenities, reviews (hiển thị rating + comment), availability calendar, price box (sticky trên desktop, bottom bar trên mobile) — theo UI Design System. *(Estimate: 3h · Priority: Urgent)*

- [x] **TSK-033** `[BE_Host]` Host MVC + REST CRUD có ownership, archive listing, quản lý ảnh (upload/delete/cover/order) qua `StorageService`; mutation ảnh được serialize và có rollback/after-commit cleanup. *(Estimate: 3h · Priority: High)*

- [x] **TSK-034** `[FE_Host]` `templates/host/dashboard.html`: phần danh sách property/Add/Edit/Archive đã hoàn thành; danh sách booking request và Stats Cards chờ dữ liệu từ Track C. *(Estimate: 3h · Priority: High · phụ thuộc TSK-041 của Dev C)*

---

# TRACK C — BOOKING · PAYMENT · REVIEW · NOTIFICATION

- [x] **TSK-035** `[DB]` Migration `V7__create_bookings.sql`: bảng `bookings` (id, property_id, guest_id, check_in_date, check_out_date, guests, nightly_price, cleaning_fee, service_fee, total_price, status, created_at, updated_at, cancelled_at) — theo thiết kế database (có snapshot price). *(Estimate: 1.5h · Priority: Urgent · phụ thuộc TSK-023 của Dev B)*

- [x] **TSK-036** `[BE_Booking]` `booking/Booking.java`, `booking/BookingStatus.java` (`PENDING/CONFIRMED/CANCELLED/REJECTED/COMPLETED`), `booking/dto/`. *(Estimate: 1.5h · Priority: Urgent)*

- [x] **TSK-037** `[BE_Booking]` `BookingRepository` (custom methods: findConflictingBookings, findByGuestId, findByPropertyIdAndStatus, findBookingRequestsByHost), `BookingService` (create booking, check overlap ngày), BookingPriceService (tính `price × nights + cleaning_fee + service_fee`). *(Estimate: 3h · Priority: Urgent)*

- [x] **TSK-038** `[BE_Booking]` AJAX `POST /api/v1/bookings/check-availability` — dùng `ApiResponse<T>`, `errorCode = "ERR_ROOM_NOT_AVAILABLE"` nếu trùng ngày. *(Estimate: 1.5h · Priority: Urgent)*

- [x] **TSK-039** `[BE_Booking]` `BookingController.java`: `GET /properties/{id}/book` → `booking.html`, `POST /bookings` (tạo booking + gọi Payment). *(Estimate: 2h · Priority: Urgent)*

- [x] **TSK-040** `[FE_Booking]` `templates/booking/booking.html` + `payment.html`: Your booking, Guest details, Price summary, nút "Confirm & Pay" theo `docs/business/OverviewBusiness.md`. *(Estimate: 3h · Priority: Urgent)*

- [x] **TSK-041** `[BE_Payment]` `payment/Payment.java`, `PaymentStatus.java`, `PaymentMethod.java` + Migration `V8__create_payments.sql`. *(Estimate: 1h · Priority: Urgent)*

- [x] **TSK-042** `[BE_Payment]` `payment/MockPaymentService.java` (implements `PaymentService`): set `payment_method = MOCK`, `status = SUCCESS` ngay lập tức. *(Estimate: 1.5h · Priority: Urgent)*

- [x] **TSK-043** `[BE_Booking]` Flow `Confirm & Pay → Payment Success → Create Booking (PENDING)` đúng sơ đồ `docs/business/OverviewBusiness.md`; expose API cho Dev B lấy "booking requests theo host" (phục vụ TSK-034). *(Estimate: 2h · Priority: Urgent · Blocking cho Dev B)*

- [x] **TSK-044** `[BE_Booking]` API accept/reject cho host (`PENDING → CONFIRMED/REJECTED`) + kiểm tra state transition hợp lệ, cancel cho guest (`PENDING/CONFIRMED → CANCELLED`). *(Estimate: 2h · Priority: Urgent)*

- [x] **TSK-045** `[FE_Booking]` `templates/booking/booking-detail.html` + trang "My Bookings" (tabs Upcoming/Pending/Completed/Cancelled) theo `docs/business/OverviewBusiness.md`. *(Estimate: 3h · Priority: High)*

- [x] **TSK-046** `[DB]` Migration `V9__create_reviews.sql`: bảng `reviews` (id, booking_id UNIQUE, property_id, guest_id, rating SMALLINT CHECK 1-5, comment, created_at, updated_at) — theo thiết kế database. *(Estimate: 0.5h · Priority: Medium)*

- [x] **TSK-047** `[BE_Review]` `review/Review.java`, `ReviewController.java`, `ReviewService.java`, `ReviewRepository.java`: cho phép review khi booking `COMPLETED`, cập nhật `rating_avg` của property sau khi review mới. *(Estimate: 2.5h · Priority: Medium)*

- [x] **TSK-048** `[BE_Notification]` `config/MailConfig.java` + `notification/NotificationService.java` (interface) + `EmailNotificationService.java`: gửi mail khi booking đổi trạng thái (CONFIRMED/REJECTED/CANCELLED) bao gồm booking info template. *(Estimate: 2.5h · Priority: Medium)*

---

# INTEGRATION & DEPLOY

- [ ] **TSK-049** `[Testing]` Nghiệm thu E2E scope mới: search/multi-room → all-item hold → FULL_PREPAY và HYBRID_DEPOSIT → verified dueOnline/instant CONFIRMED → direct receipt/actual check-in/refund/payout gates → guarded completion/review. Mock→PENDING→Host Accept chỉ baseline preflight lịch sử, không đóng task này. *(Legacy estimate: 2h · Priority: Urgent; không là cam kết capacity scope mới)*

- [ ] **TSK-050** `[Infra]` Verify `docker compose up -d --build` chạy full stack (app + db) không lỗi, dùng `application-docker.yml`. *(Estimate: 1.5h · Priority: High)*

- [ ] **TSK-051** `[Testing]` Review chéo scope mới giữa hai agent: DTO/no entity leak/no empty catch, ownership/CSRF/private evidence, shared transaction/financial guards và cả hai payment modes; review không đồng thời edit file của owner. *(Legacy estimate: 1.5h · Priority: High)*

- [ ] **TSK-052** `[Docs]` Final README/screenshots/evidence scope mới, cả hai modes; Agent A single writer cập nhật `docs/tasklist/TaskList.md` chỉ theo bằng chứng đã accepted, không tick vì viết docs. *(Legacy estimate: 1h · Priority: Medium)*

## NEXT FEATURE — DISCOUNT · VNPAY PAYMENT

- [x] **TSK-063** `[Docs]` Hoàn thiện thiết kế Discount + VNPay pipeline trong API/database/system diagrams trước khi implement. *(Estimate: 1h · Priority: High)*

- [x] **TSK-064** `[DB_Discount]` Thêm migration `discount_codes`: code unique, type `PERCENT/FIXED`, value, cap, minimum amount, time window, usage limits, active flag. *(Estimate: 1h · Priority: High)*

- [x] **TSK-065** `[DB_Booking]` Mở rộng `bookings`: `subtotal_price`, `discount_code_id`, `discount_amount`, trạng thái `PENDING_PAYMENT`, và đảm bảo `total_price` là tổng sau discount. *(Estimate: 1h · Priority: High)*

- [x] **TSK-066** `[DB_Payment]` Mở rộng `payments`: provider `VNPAY`, currency `VND`, `provider_txn_ref`, `provider_transaction_no`, `raw_response`, trạng thái `PENDING/SUCCESS/FAILED/CANCELLED/EXPIRED`. *(Estimate: 1h · Priority: High)*

- [x] **TSK-067** `[BE_Discount]` Implement `DiscountCode`, repository, service validate/apply; backend tự tính lại giá, không tin discount amount từ frontend. *(Estimate: 2h · Priority: High)*

- [x] **TSK-068** `[API_Discount]` Thêm `POST /api/v1/discounts/validate` để preview discount trên booking page. *(Estimate: 1h · Priority: Medium)*

- [x] **TSK-069** `[BE_Payment]` Thêm VNPay config properties từ `.env`: `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `VNPAY_PAY_URL`, `VNPAY_RETURN_URL`, `VNPAY_IPN_URL`. *(Estimate: 1h · Priority: High)*

- [x] **TSK-070** `[BE_Payment]` Implement VNPay URL builder và secure-hash verifier theo sorted params + HMAC SHA512. *(Estimate: 2h · Priority: High)*

- [x] **TSK-071** `[BE_Booking]` Refactor `POST /bookings`: tạo booking `PENDING_PAYMENT`, payment `PENDING`, apply discount snapshot, redirect sang VNPay checkout URL. *(Estimate: 2.5h · Priority: High)*

- [x] **TSK-072** `[BE_Payment]` Implement `GET /api/v1/payments/vnpay/ipn`: verify checksum/amount, idempotent update payment success/failure, booking `CONFIRMED/CANCELLED`, tăng discount usage sau success. *(Estimate: 3h · Priority: High)*

- [x] **TSK-073** `[FE_Payment]` Thêm `GET /payments/vnpay/return` + template result page: hiển thị đang xác minh, polling status, redirect `/my-bookings`. *(Estimate: 2h · Priority: Medium)*

- [x] **TSK-074** `[API_Payment]` Thêm `GET /api/v1/payments/bookings/{bookingId}/status` cho payment result polling. *(Estimate: 1h · Priority: Medium)*

- [x] **TSK-075** `[FE_Booking]` Cập nhật booking page: promo code input, apply button, discount line, nút `Pay with VNPay`, hiển thị VND. *(Estimate: 2h · Priority: Medium)*

- [ ] **TSK-076** `[Notification · P1 · pending]` Email theo transition đã commit, không theo return URL; phân biệt online paid/direct outstanding/CONFIRMED/refund/payout và SLA business-days cấu hình, chống trùng/retry giới hạn/lỗi SMTP không rollback tiền. **AC:** mỗi business event một notification sau commit, retry/duplicate evidence TSK-077; không hứa3–5 ngày chưa duyệt. **Ref:** TSK-048/061, `notification/EmailNotificationService`.

- [ ] **TSK-077** `[Testing · P0 · pending]` Tổng hợp remediation evidence matrix **cả hai modes**, không thay thế implementation hay duplicate TSK-114. **AC:** signature/dueOnline/merchant/currency/envelope/auth, read-only return, duplicate/out-of-order/late/extra attempts, expiry/DB race, discount/caps/rounding/zero, self-booking/lock/completion/no-show/review/email, receipt/refund/payout/recovery đều có command/environment/result/duration/artifact/skipped; mock external không mock invariant, concurrency DB thật trong integration riêng khi được phép. **Ref:** TSK-070–074, TSK-114; docs hiện chưa là runtime evidence.

- [ ] **TSK-078** `[Docs · P1 · pending]` Agent A chốt bộ canonical version/decision log sau triển khai, đồng bộ business/API/DB/UI/Rules/config docs/README/tracker theo evidence. **AC:** mỗi state/endpoint/policy có version/approval/evidence link; revalidate TSK-071–073, sandbox khác production, cả hai modes không bị ghi proposal thành implemented. Agent B chỉ gửi review/report, không cùng edit docs. **Ref:** TSK-062/063; cập nhật backlog không đóng task.

---

# BUSINESS REMEDIATION — BA + CHECKER

**Nguồn/phạm vi:** baseline `9fee7a06029736292a71548383465d06e39bd4dc`; kế thừa BA review và quyết định user mới nhất, đồng bộ docs/business/API/DB/UI/sprint tracker. Không source/migration changes hay xác minh runtime trong lần cập nhật này. Các tham chiếu class/dòng dưới là **evidence source từ review trước**, không phải test mới; không thay thế contracts/evidence cần accepted tại TSK-078.

**Cần tái xác minh TSK-071–073:** giữ nguyên checkbox lịch sử, không coi là đã đáp ứng canonical flow. `MockPaymentService:97–105` success đặt booking `PENDING`, không `CONFIRMED`; `PaymentController:27–33` return gọi `handleVnpayIpn`. Verifier signature/amount đã tồn tại (`MockPaymentService:73–87`), không kết luận query giả bất kỳ thành công. `BookingServiceImpl:148–165,223–232` chỉ host sở hữu được accept/reject `PENDING`, không phải reject `CONFIRMED`; create `59–65` chưa loại self-owner; complete `170–175` do host/manual, chưa guard ngày. Email hiện xử lý CONFIRMED/REJECTED/CANCELLED, bỏ qua PENDING. DB đã có exclusion constraint: cần test invariant, **không khẳng định đã chứng minh overbooking**.

P0 blocks real money/P1MVP/P2optional. All eight S0 product designs approved/frozen **S0-109-v1 FROZEN_DESIGN** [policy/fixtures/typed API/handoff](../business/S0DecisionLog.md#s0-final-handoff--tsk-109).20%postdiscount/15%prediscountroom+clean/platformD/Cnetcoverage/noGuestfee/900s/max3/084refund/090time-evidence-review no longer proposals. Pending means implementation/evidence not accepted;095actual legal/contracts/tax/calendar/provider/signedops/liquidity evidence BLOCKED. No invented escrow/autotransfer/signatures or runtime compatibility.

| Phạm vi | P0 | P1 | P2 | needs PO approval | pending | Hoàn tất |
|---|---:|---:|---:|---:|---:|---:|
| TSK-076–114 (39 task; completed policy/foundation scope is not release/legal clearance) | 22 | 16 | 1 | 0 | 29 | 10 |

**TARGET core MVP** [Inventory](../business/PropertyInventoryBusinessRules.md)/[Payment](../business/PaymentBusinessRules.md), not fully implemented; [design evidence](../business/S0DecisionLog.md). **114 IDs: 81 checked (71 history +8 S0 designs +2 accepted foundations),33 unchecked; active MVP scope42, remaining32 +optional096; active done10/42**. S0 done8/8, pending0/8.079/097/081/084/086/088/090/109 completed design;099inventory and110internal financial primitives/rollback design independently accepted. S1 done2/2.095actualclearance BLOCKED; legacy exclusion and payments preserved, cutover/backfill blocked. [Accepted099scope/evidence](evidence/TSK-099.md), [accepted110scope/evidence](evidence/TSK-110.md). Only110newlyclosed; no098/102/106 or B task closure implied.

**Một nguồn dependency duy nhất:** các trường `Deps` ở bảng lịch sprint bên dưới; không còn inline/override competing edges. Mỗi ID unchecked có đúng một hàng, một Sprint/Agent/rank; completed S0 rows retained for history and dependency evidence. Deps chỉ explicit IDs (không range); Ref/AC/coverage mentions không tạo edge. Checked history không thêm edge redesign. Cùng sprint chỉ bắt đầu dependent sau accepted handoff của deps; rank là thứ tự topological gate, không bắt buộc tuần tự các task độc lập.

- [x] **TSK-079** `[P0 · completed policy design]` **S0-079-v1, 2026-10-23:** approved instant FULL_PREPAY/HYBRID_DEPOSIT state×actor×required-online/direct collection contract. **AC met (design only):** versioned user PO approval, no Host accept/reject, no monthly commission/no onsite StayHub QR; config109/086 and runtime/release remain separate. **Evidence:** [approval and validation log](../business/S0DecisionLog.md), [canonical matrix](../business/PaymentBusinessRules.md#1a-approved-contract-matrix--s0-079-v1); TSK-043/044/071–073 still require revalidation.
- [ ] **TSK-080** `[P0 · pending]` Đồng bộ transition/read-only status adapters theo approved instant. **AC:** return/poll không mutation; online attempt SUCCESS không đồng nghĩa fully paid/direct verified; CONFIRMED chỉ server state, no Host accept/reject. Core transaction thuộc A/082, B chỉ đọc/projection. **Ref:** TSK-071–075 revalidation.
- [x] **TSK-081** `[P0 · completed policy design]` **S0-081-v1, 2026-10-23:** approved original createdAt+15min hold, active t<expiry; max3 checkout creations inclusivefirst, no extension. Failure callback changes attempt only; expiry worker finalizes deterministic all3-terminal-unsuccessful failure or expiry, never kills another pending attempt. Atomic one collection/all-item release-conversion; late/extra money OperationsFinance reconcile/refund, no resurrection. **AC met (design only):** [Payment contract](../business/PaymentBusinessRules.md#3a-approved-hold--retry--expiry--s0-081-v1), [approval/check log](../business/S0DecisionLog.md); implementation082/102/runtime evidence remain pending.
- [ ] **TSK-082** `[P0 · pending]` Harden IPN/lifecycle hold. **AC:** verify signature/merchant/currency/ref/envelope/result và **attempt dueOnline**, không T; unique event/credit/replay/out-of-order, serialize expiry +all-item conversion +caps/ledger/outbox. Late/extra paid ghi reconcile/refund, không double charge/confirm. **Ref:** TSK-070/072/074.
- [ ] **TSK-083** `[P0 · pending]` Chứng minh invariant inventory dưới concurrency. **AC:** giữ baseline exclusion tới capacity protection/backfill reconcile; same-property different type, last-room/shared-plan, expiry/IPN và all-item rollback bằng DB thật. Không kết luận constraint cũ sai hay an toàn từ mocks. **Ref:** inventory canonical mục3/7/8.
- [x] **TSK-084** `[P0 · completed policy design]` **S0-084-v1, 2026-10-23:** >=48h/full collected, late/no-show retain room/refund collected unused net-cleaning absent actual stay; Host inability full collected, post-check-in Admin incident case. Source/charge/discount-funded conservation, separate platform/direct paid+reserved guards, no automatic B charge/invoice. Platform SLA5–10businessdays from eligible request; calendar/holiday/legal/tax clearance remains release gate, tax unset/disabled. **AC met design only:** [Payment section5a](../business/PaymentBusinessRules.md#5a-approved-cancellation--refund--s0-084-v1), [log/examples/checks](../business/S0DecisionLog.md); execution085/config109/release095 not closed.
- [ ] **TSK-085** `[P0 · pending]` Refund lifecycle độc lập trên ledger primitives110. **AC:** policy allocations +commission recompute; reserve atomically không quá online verified−paid−reserved và policy remaining, liquidity sau payout; Host direct responsibility riêng. Request/approved/processing/paid/failed, idempotent retry/reversal, manual email/admin proof/reference/audit/maker-checker khác người, signed configurable business-days SLA/timezone/holidays; không paid từ CANCELLED, không claim auto gateway refund.
- [x] **TSK-086** `[P0 · completed PRODUCT CONTRACT · NOT legal clearance]` **S0-086-v1:**15%room+clean excludingtax/platformD<=Cgross/Cnet=Cgross−D/hybrid20%T/O>=Cnet/HostS−Cgross/noGuestfee/providerfeeplatformexpense. Due/reserved/earned separate, conservative obligationreserve/ALLARRIVED+24/allgates/opsmakercheckerproof/recovery. [Payment](../business/PaymentBusinessRules.md)/[evidence](../business/S0DecisionLog.md).109design nowfrozen, actualcontracts/legal/signedops/calendar/tax/liquidity/runtime095 BLOCKED, no permissioncollect/hold/transfer.
- [ ] **TSK-087** `[P0 · pending]` Payout execution/reconciliation và revenue thật trên110/112. **AC:** gross/commission due/reserved/earned/payable/direct/refunds/funding không đếm đôi; chỉ platform-held eligible entitlement, exclude direct B, recheck dispute/refund/legal/liquidity dưới lock; manual proof/audit/maker-checker/idempotent retries/failed/recovery. Admin bỏ mock, không automatic bank automation. Không tái implement journal primitive110.
- [x] **TSK-088** `[P0 · completed policy design]` **S0-088-v1:**selfownerblock/onecode/nostacking/usercode1/finitecampaignQpublishinput; atomicoriginal900sreserve/acceptedonlineconsume/finalfail-expiryrelease/no consumedrestore/directBbypass. WholeVNDHALF_UP/stableallocation/nozeroT-O/platformD<=Cgross/O>=Cnet/noGuestfee. [Payment](../business/PaymentBusinessRules.md)/[Discount](../database/10_Discounts.md)/[log](../business/S0DecisionLog.md).109designfrozen,089runtime/095clearance pending.
- [ ] **TSK-089** `[P0 · pending]` Enforce booking/discount invariants. **AC:** self-owner/caps atomic, duplicate IPN không consume hai lần, release đúng lifecycle, rounding/base0/zero exception không gateway sai amount; exact breakdown/funding/commission coverage sau adjustment, preview không reserve quyền mã. **Ref:** TSK-064–068/072.
- [x] **TSK-090** `[P1 · completed policy design]` **S0-090-v1:**HCM14/11/1–30nights/sameday<20creation/retryoriginalexpiry; adults18+/eachroomadult/childinteger0–17/alloccupancy/noextrabedpricing. Perroomactual+Guestack/auditedAdmin/nofuture-prebooking/ALLARRIVED+24/allgates; completioncheckout11+actualstay/resolvedcases/noearlyexception; actualGuest reviewonce [completedAt,+14days)/no-showdeny. [Inventory](../business/PropertyInventoryBusinessRules.md)/[log](../business/S0DecisionLog.md).109designfrozen,091/092/112runtime/095clearance pending.
- [ ] **TSK-091** `[P1 · pending]` Enforce date/blackout/availability nhất quán read-only adapters. **AC:** search/detail/create cùng accepted engine/policy, past dates/capacity guards A102/083, sửa lịch không phá paid bookings; adjacency/timezone boundary tests. B không edit transaction core.
- [ ] **TSK-092** `[P1 · pending]` Guard completion/no-show/review. **AC:** actor/date/actual evidence theo090/112, no early completion/Host tap, eligible guest một review/window, moderation/host reply đúng scope; direct receipt/online SUCCESS không tự mở review.
- [ ] **TSK-093** `[P1 · pending]` Onboarding/lock/paid future stays. **AC:** PO duyệt rights/lock/session/checkout/IPN/future stay matrix rồi A enforce config/SecurityConfig; không mất callback/refund/support, direct evidence private/no bank credentials. Evidence lock trái quyền và callback vẫn xử lý.
- [ ] **TSK-094** `[P1 · pending]` Snapshot terms/archive. **AC:** policy/price/fee/discount/mode/funding/consent/version +listing snapshots; sửa/archive không đổi lịch sử/future rights; guest/support access, retention/private evidence policy, không delete audit.
- [ ] **TSK-095** `[P1 · pending]` Ký final release gates **sau108**, không là input077/078. **AC:** cả multi-room và FULL_PREPAY/HYBRID_DEPOSIT cùng accepted; toàn P0/evidence077/107/114 và approvals/Finance/Legal/Operations đạt, docs078/final049–052 và cutover108 đạt. Sandbox nhãn test funds không production-ready; chưa đạt core thì no-go, không hybrid flag deferral.
- [ ] **TSK-096** `[P2 · pending]` Optional **sau MVP**: chỉ đánh giá review hai chiều, go/no-go/double-blind/moderation/privacy và task riêng nếu chọn; không trì hoãn inventory hoặc payment core.

# CORE MVP — PROPERTY / ROOM-TYPE INVENTORY (FOUNDATION ACCEPTED; CUTOVER PENDING)

**Design inputs do not close implementation:**097/109design frozen;099inventory and110internal financial foundations/rollback designs independently accepted.100–102selection-hold/098host/101guest/106cutover/107runtime and111–114 financial delivery remain pending. Active fixture3rooms T6.800.000, hybridO1.360.000/B5.440.000/Cgross1.050.000/Cnet850.000/held510.000, fullheld5.950.000, Familysecondnight0 rollbackall. Legacy exclusion retained; no active multi-room cutover. Shared docs single integrator, B reportonly, ownership not concurrentwrite permission.

- [x] **TSK-097** `[P1 · completed policy design]` **S0-097-v1, 2026-10-23:** explicit user PO approval frozen: exclusive WHOLE_PLACE/ROOM_BASED, interchangeable pooled RoomTypes/shared-plan stock, PhysicalRoom assignment future, whole-place one type quantity1/bedrooms descriptive, one property/date range/VND and all-booking cancellation (no partial). **AC met (design only):** canonical Inventory + targeted Overview/API/ERD projections; [approval/evidence](../business/S0DecisionLog.md). Schema099/hold102/migration106/runtime107 and refund084 remain pending, no implementation claim.
- [ ] **TSK-098** `[P1 · pending]` Host types/plans/calendars: A sở hữu mutation/entities/DTO/ownership/CSRF/version/OOO/capacity guards, không template. **AC:** một property nhiều types, plans share stock, allowed payment modes hợp lệ, reductions không phá holds/confirmed, archive giữ future rights. B host UI thuộc113, không cùng sửa PropertyServiceImpl.
- [x] **TSK-099** `[P0 · DONE scoped foundation]` Controller-reserved V14 additive inventory schema/capacity primitives and rollback design independently APPROVED2026-10-23 after review fixes; [accepted scope, actual PostgreSQL evidence and exact manifest](evidence/TSK-099.md). **AC met:** scoped type-plan-property FKs, nonnegative shared stock, stable booking/type/plan/day lock order, archive-admission linearization, immutable snapshot history and observed DB transaction/concurrency proof; no plan stock. Legacy property exclusion unchanged; no backfill/cutover, runtime checkout or downstream task acceptance implied.
- [ ] **TSK-100** `[P1 · pending]` Read-only eligible offer search theo accepted repository/DTO. **AC:** một property/card, min stock mọi đêm/per-room occupancy, total basis rõ, no dates không live stock, shared plans không duplicate. A shared repository/entities boundary, B adapters/controllers chỉ theo manifest accepted.
- [ ] **TSK-101** `[P1 · pending]` Guest detail/select quantities/checkout inventory UX. **AC:** dates/rooms/adults/child ages/per-room allocations, fee/tax total stay, soldout/alternatives/stale409/reconsent, whole-place1 theo approved boundary. Payment-specific cards/receipts/admin thuộc113, không duplicate scope.
- [ ] **TSK-102** `[P0 · pending]` Header/items và atomic holds. **AC:** approved property-date-currency boundary, nightly/allocation/policy/mode snapshots; all items/nights or rollback, stable locks/idempotent submit, shared-plan/continuity/adjacency/expiry, cancellation granularity theo097/084.
- [ ] **TSK-103** `[P0 · pending]` Booking-level dual-mode quote/allocation/lifecycle composition. **AC:** exact item/room/night/discount/fee/tax/funding sums, dueOnline+B=T, commission coverage/zero/base0/rounding, server mode/plan validation và frozen snapshots; insufficient coverage reject/requote, no monthly receivable. Dùng110 primitives, IPN security/attempt credit thuộc082, refund execution085, không duplicate implementation.
- [ ] **TSK-104** `[P1 · pending]` Review/email/snapshot projection cross-impact. **AC:** một review/completed booking không theo quantity, optional type context, item email payload sau commit, archive giữ rights/support. TSK-076 sở hữu retry/send;092 guard core A, B chỉ projections/tests/docs handoff.
- [ ] **TSK-105** `[P1 · pending]` Read-only host/admin metrics đúng grain. **AC:** distinct header, rooms/roomnights/sellable denominator, gross/online/direct-attested/commission reserved-earned/refund/payable không duplicate join items; reconcile ledger/property/type, không tự implement payout core.
- [ ] **TSK-106** `[P0 · pending]` Additive backfill/reconcile/cutover rehearsal. **AC:** property default type1/plan/items, IDs/snapshots preserved, unknown provenance không bịa giá/mode/payment history; no auto address merge; counts/totals/commitments/online-direct-ledger reconcile. Capacity protection trước exclusion replacement, rollback giữ bookings mới; planned manifest/version claim A only.
- [ ] **TSK-107** `[P0 · pending]` Inventory runtime evidence matrix. **AC:** s0-fixtures-v1 Deluxe5/Family2/3rooms T6.800.000, Familysecondnight0 rollbackall; DBreal lastroom/sharedplan/expiry/lateIPN/allnight/continuity/child/adjacency/zero-reject/rounding/migration/metrics, externalmocks only, command/env/duration/artifacts/skips. No concurrency inference from docs/mocks.
- [ ] **TSK-108** `[P1 · pending]` Cutover/rollout readiness gate, không production authorization095. **AC:** shadow reconcile/write coordination/pilot/runbook/monitoring/rollback owner, all inventory097–107 và full/hybrid109–114/evidence/docs/final acceptance đạt. Flags chỉ phối hợp cutover, không defer hybrid hay multi-room khỏi MVP; activation tiền thật còn cần095, không dependency ngược095.

# CORE MVP — DUAL PAYMENT DELIVERABLES (FOUNDATION ACCEPTED; RUNTIME PENDING)

TSK-110 additive V15/internal JDBC foundation and rollback design independently
APPROVED with no findings after the foreign-XID fix:
[manifest, actual PostgreSQL results and acceptance limits](evidence/TSK-110.md).
110is DONE for this scope only; S1done2/2. No checkout/IPN cutover, real-money
permission or downstream closure. Dependency edges and owners remain unchanged.

- [x] **TSK-109** `[P0 · completed policy design]` **S0-109-v1,2026-10-23 FROZEN_DESIGN:** [policy](../business/contracts/s0-policy-v1.json), [fixtures](../business/contracts/s0-fixtures-v1.json), [typed DTO/errors](../business/contracts/s0-api-v1.json), [tracked stdlib verifier](../business/contracts/verify_s0_contracts.py), [approval/evidence/exact handoff](../business/S0DecisionLog.md#s0-final-handoff--tsk-109). All approved values/versioned consent/hash/amountOvsT and full/hybrid6.8m projections frozen; Money wholeVND, finitequota publishinput, no Guestfee/zero exception. **AC met design only:** computed positive/edge/mutation checks, counts/DAG/docs. SourceCompatibility NONE_IMPLEMENTATION_REQUIRED; no activation/runtime/FinanceLegal clearance.095contracts/legal/tax/calendar/provider/signedops/liquidity BLOCKED. Only109 newlyclosed, no S1work before userPRmerge/acknowledged exactmanifest.
- [x] **TSK-110** `[P0 · DONE scoped internal foundation]` Additive V15/internal JDBC snapshot/attempt/collection/journal/reserve primitives and rollback design independently APPROVED2026-10-23 with no findings after foreign-XID fix; [accepted manifest, actual PostgreSQL evidence and limits](evidence/TSK-110.md). **AC met:** immutable balanced journals with DB-owned construction XID, scoped event/key/hash conflicts, exact full/hybrid/funded amounts, original-deadline max3attempts/single accepted credit, memo-not-cash/source reserve bounds and observed transaction/concurrency proof;27focused/128full passes. V14/legacy payments/exclusion unchanged. **Không gồm:** checkout/IPN composition082/102/103, payout/refund execution087/085, receipt/check-in111/112, cutover or UI; real-money095BLOCKED.
- [ ] **TSK-111** `[P0 · pending]` Direct balance receipts/reconciliation. **Substeps:** (1) Host ownership/CSRF and append attestation amount/currency/method/actor/paidAt/recordedAt/ref/private evidence/key/hash/version; (2) partial accumulation/outstanding, refused/disputed states, no providerVerified flag; (3) Guest acknowledge/contest, admin disposition/correction reversal original, private evidence/access audit; (4) link direct refund responsibility/reconcile memorandum, no platform cash credit; (5) handoff endpoint/errors/fixture to113. **AC/tests:** partial300k+500k clears B800k attested only; duplicate retry once, stale version/changed-body/cross-owner/overpayment reject or explicit case, reversal restores outstanding, PII not returned. **Không gồm:** bank verification/credentials/onsite StayHub QR/payment automation/monthly invoice/UI.
- [ ] **TSK-112** `[P0 · pending]` Actual check-in/disputes/payout **eligibility**, not transfer. **Substeps:** (1) obtain090 PO evidence/two-party or contested admin fallback/boundary decision; (2) append per-item-room actualAt/timezone/actor/evidence/ack/version/key, partial arrivals tracked; (3) evaluate actual+24h applicable entitlement, dispute/refund/reconcile/liquidity/legal blocks, no Host-tap/calendar-only eligibility; (4) before/after payout case freeze/reserve/clawback/manual recovery owner/disposition; (5) publish frozen eligibility/read model to087/113. **AC/tests:** acknowledged actual arrival boundary +24h eligible only with all gates, partial/contested/duplicate/out-of-order timestamps blocked; scheduled date alone never eligible, post-payout dispute opens recovery not monthly commission. **Không gồm:** payout execution087/automatic bank transfer/PhysicalRoom assignment; unresolved boundary blocks delivery.
- [ ] **TSK-113** `[P1 · pending]` Dual-mode Guest/Host/Admin UI on frozen s0-api-v1. **Substeps:** modecards/immutableconsent/total-O-B/refund; onlinevsdirectattested/partial/reversal/ack/dispute; perroomactual/blockedpayout; Adminproof/makerchecker/sourcepools/recovery; realadapters only acceptedhandoff. **AC:** s0-fixtures-v1 full/hybrid6.800.000, no Guestcommissionleak/falsefullypaid/autopayout, bothhappy/stale/contested/desktop/mobile/keyboard evidence. No core/sharedDTO/security/entity edits or duplicated101.
- [ ] **TSK-114** `[P0 · pending]` Dual-mode financial test execution +operations runbook, evidence consumer077. **Substeps:** (1) implement isolated production-behavior happy/critical cases per mode; (2) approved integration DB races/attempt duplicates/late-extra paid/coverage/refund-pool/post-payout recovery, mock external; (3) fixture arithmetic/source distinction/UI privacy/actual-check-in+24h/manual proof-maker-checker; (4) runbook signed SLA/email, reconciliation/payout/refund failure/recovery owners/alerts/rollback; (5) report exact command/env/result/duration/artifacts/skipped, no evidence substitution. **AC:** both modes required; refund pool200k−50k paid−20k reserved allows130k rejects130001, partial arrival/disputed no eligibility; no real-money claim from sandbox. **Không gồm:** inventory-specific execution107/aggregate evidence077/release approval095; do not rerun unchanged passing checks without doubt.

# AUTHORITATIVE TWO-AGENT LOGICAL SPRINT PLAN

Sprint labels below replace old track headings **only for unchecked work**; checked history remains unchanged. Logical groups, not calendar promises or invented capacity. Agent A/B are recommended roles, not automatic assignment to external sessions. A is integrator/single shared-doc writer; B reviews through report, not concurrent editor. A bottleneck is deliberate. Deps are the only canonical dependency fields for all unchecked IDs; `none` means no redesign prerequisite.

| Task | Sprint | Agent | Rank | Deps | Phase / scope / accepted handoff and evidence |
|---|---|---|---:|---|---|
| TSK-079 | S0 | A | 1 | none | COMPLETED policy S0-079-v1 (2026-10-23); PaymentBusinessRules + S0DecisionLog approval/check evidence; not implementation |
| TSK-097 | S0 | A | 2 | none | COMPLETED policy S0-097-v1 (2026-10-23); Inventory/Overview/API/ERD + S0DecisionLog; implementation gates remain pending |
| TSK-081 | S0 | A | 3 | TSK-079, TSK-097 | COMPLETED policy S0-081-v1; Payment/Inventory/API + decision log/checks, no runtime claim |
| TSK-084 | S0 | A | 4 | TSK-079, TSK-097 | COMPLETED policy S0-084-v1; Payment/Inventory/Overview/API + decision log; runtime/tax/calendar clearance not claimed |
| TSK-086 | S0 | A | 5 | TSK-084 | COMPLETED PRODUCT CONTRACT S0-086-v1; NOT legal clearance; actual contracts/signedops/liquidity/tax/calendar evidence095 BLOCKED |
| TSK-088 | S0 | A | 6 | TSK-079, TSK-086 | COMPLETED design S0-088-v1; Payment/Discount/API/error semantics + decision log/checks, not runtime089/config109 |
| TSK-090 | S0 | A | 7 | TSK-079, TSK-084, TSK-097 | COMPLETED design S0-090-v1; Inventory/Payment/Overview/API/Review/log; no runtime or109 freeze |
| TSK-109 | S0 | A | 8 | TSK-079, TSK-084, TSK-086, TSK-088, TSK-090 | COMPLETED FROZEN_DESIGN s0-policy-v1/s0-fixtures-v1/s0-api-v1 + tracked verifier and durable handoff; no source/release clearance |
| TSK-099 | S1 | A | 1 | TSK-097, TSK-109 | DONE scoped foundation; V14 +InventoryCapacityService +30PostgreSQL cases +rollback design independently accepted; evidence/TSK-099.md; exclusion retained/cutover pending |
| TSK-110 | S1 | A | 2 | TSK-099, TSK-109 | DONE scoped internal foundation; corrected V15/JDBC primitives +27PostgreSQL cases/128full passes +rollback design independently accepted; evidence/TSK-110.md; runtime/downstream acceptance not implied |
| TSK-098 | S2 | A | 1 | TSK-097, TSK-099, TSK-109 | property mutation core/DTO; types/plans/calendar ownership/version tests; [implementation evidence pending independent review](evidence/TSK-098-102.md) |
| TSK-100 | S2 | B | 2 | TSK-099, TSK-098 | property read-only adapters/controllers +templates/property; eligible offers fixture→accepted API |
| TSK-101 | S2 | B | 3 | TSK-100, TSK-109 | templates/property,booking +static; inventory selection UI happy/soldout/stale evidence |
| TSK-102 | S2 | A | 4 | TSK-099, TSK-081, TSK-110, TSK-098 | booking core/items/DTO/holds; DB all-item rollback/expiry protocol; [implementation evidence pending independent review](evidence/TSK-098-102.md) |
| TSK-083 | S3 | A | 1 | TSK-079, TSK-081, TSK-099, TSK-102 | booking/property DB invariant cases; capacity proof, no premature exclusion drop |
| TSK-091 | S3 | B | 2 | TSK-090, TSK-083, TSK-100, TSK-101 | read-only property adapters/templates; shared date engine boundary evidence |
| TSK-103 | S3 | A | 3 | TSK-102, TSK-084, TSK-088, TSK-109, TSK-110 | booking/payment pricing+DTO; both modes exact allocations/coverage fixture |
| TSK-082 | S3 | A | 4 | TSK-079, TSK-081, TSK-099, TSK-103, TSK-110 | payment/booking transaction core +config callback exemption; verified O/attempt/race evidence |
| TSK-089 | S3 | A | 5 | TSK-088, TSK-082, TSK-103 | discount/booking atomic caps/funding tests; no double consume |
| TSK-080 | S3 | B | 6 | TSK-079, TSK-082, TSK-103 | read-only payment adapters/templates/payment,booking; status source distinction evidence |
| TSK-111 | S4 | A | 1 | TSK-103, TSK-110, TSK-082 | payment/booking +config ownership; direct receipts/reversals/dispute API contract +isolated tests |
| TSK-112 | S4 | A | 2 | TSK-090, TSK-086, TSK-102, TSK-110, TSK-111 | booking/payment/admin core; arrival/eligibility/dispute read model and proof |
| TSK-085 | S4 | A | 3 | TSK-082, TSK-084, TSK-103, TSK-110, TSK-111, TSK-112 | payment/admin refund execution; pool guard/manual proof/funding evidence |
| TSK-104 | S5 | B | 1 | TSK-102, TSK-080 | review/notification read projections +template payloads; frozen A guard API only |
| TSK-076 | S5 | B | 2 | TSK-079, TSK-080, TSK-082, TSK-104, TSK-085 | notification +templates; dedupe/retry/SMTP-failure evidence |
| TSK-092 | S5 | A | 3 | TSK-090, TSK-104, TSK-112 | booking/review guard core; early completion/no-show/review tests |
| TSK-093 | S5 | A | 4 | TSK-079, TSK-084, TSK-086, TSK-098, TSK-111 | config/SecurityConfig +auth/user/property; rights/lock callback/refund matrix evidence |
| TSK-094 | S5 | A | 5 | TSK-084, TSK-088, TSK-090, TSK-102, TSK-104, TSK-109 | booking/property snapshot/archive core; historical rights/retention evidence |
| TSK-105 | S5 | B | 6 | TSK-103, TSK-110, TSK-085 | admin/property read-only metrics adapters; no editing dirty AdminController until explicit handover |
| TSK-087 | S5 | A | 7 | TSK-085, TSK-086, TSK-105, TSK-110, TSK-111, TSK-112 | payment/admin payout execution; ledger reconcile/manual proof/no direct doublepay |
| TSK-113 | S5 | B | 8 | TSK-080, TSK-101, TSK-111, TSK-112, TSK-085, TSK-087, TSK-093, TSK-098 | templates/host,admin,booking,payment +static; accepted backend→dual-mode accessible UI evidence |
| TSK-106 | S6 | A | 1 | TSK-099, TSK-102, TSK-103, TSK-110, TSK-111, TSK-112, TSK-094 | reserved additive migrations/backfill/reconcile/rollback rehearsal; no unknown-history invention |
| TSK-107 | S6 | B | 2 | TSK-098, TSK-100, TSK-101, TSK-102, TSK-103, TSK-104, TSK-105, TSK-106, TSK-083, TSK-091, TSK-092 | src/test/.../property,booking,support; inventory matrix command/artifacts; shared tests serialized |
| TSK-114 | S6 | B | 3 | TSK-076, TSK-085, TSK-087, TSK-089, TSK-093, TSK-094, TSK-109, TSK-110, TSK-111, TSK-112, TSK-113, TSK-106 | src/test/.../payment,booking,config,notification,discount,support; financial matrix/runbook report to A |
| TSK-049 | S7 | B | 1 | TSK-107, TSK-114 | final both-mode/multi-room E2E acceptance evidence, baseline preflight cannot close |
| TSK-050 | S7 | A | 2 | TSK-049, TSK-106 | docker-compose.yml/application-docker.yml/pom.xml if approved; exact full-stack validation manifest |
| TSK-051 | S7 | B | 3 | TSK-049, TSK-050 | cross-review supplied diff/test evidence; no concurrent source editing |
| TSK-077 | S7 | B | 4 | TSK-076, TSK-080, TSK-081, TSK-082, TSK-083, TSK-084, TSK-085, TSK-086, TSK-087, TSK-088, TSK-089, TSK-090, TSK-091, TSK-092, TSK-093, TSK-094, TSK-107, TSK-114, TSK-049, TSK-050, TSK-051 | evidence matrix report; A publishes canonical shared docs, no095 edge |
| TSK-078 | S7 | A | 5 | TSK-079, TSK-077, TSK-106, TSK-113 | final shared business/API/ERD/UI/Rules/config docs version +approval/evidence links |
| TSK-052 | S7 | A | 6 | TSK-049, TSK-050, TSK-051, TSK-078 | final README/screenshots/TaskList evidence reconciliation; no false early closure |
| TSK-108 | S8 | A | 1 | TSK-107, TSK-077, TSK-078, TSK-114, TSK-052 | cutover readiness/pilot/shadow/rollback owner; release authorization separate095 |
| TSK-095 | S8 | A | 2 | TSK-108, TSK-077, TSK-078, TSK-049, TSK-050, TSK-051, TSK-052, TSK-109, TSK-110, TSK-111, TSK-112, TSK-113, TSK-114 | final signed MVP release gates both modes/all P0/config/legal; no-go if any core unmet |
| TSK-096 | POST | A | 1 | TSK-090, TSK-095 | optional two-way-review evaluation only after MVP; not active coverage |

## Substep/handoff recipe cho các ID tái dùng

Mỗi hàng thực hiện **1→2→3**, chỉ trong scope/path ở bảng sprint; owner/sprint/deps không lặp lại ở đây. Task policy: evidence là signed decision/version; task implementation: isolated happy+critical edge RED→GREEN rồi review, không tick từ docs. **Out-of-scope chung:** không thay quyết định core đã duyệt, không triển khai task downstream thay owner, không file ngoài acknowledged manifest, không automated bank transfer/monthly commission/new provider; các exclusions cụ thể trong AC vẫn áp dụng.

| Task | 1. Prepare | 2. Deliver | 3. Accepted handoff / evidence |
|---|---|---|---|
| TSK-076 | Freeze committed event/payload with104 | Dedupe send/retry/error observability | SMTP mock success/failure/retry report; no transaction rollback |
| TSK-077 | Import107/114/final acceptance manifests | Map every remediation AC→case→result/skipped | Aggregate signed evidence index, no code changes or095 prerequisite |
| TSK-078 | Compare accepted contracts to delivered behavior/evidence | Integrator updates canonical versions/decisions/config docs | Cross-doc link/status review; no runtime evidence invented |
| TSK-079 | Record approved instant/full/hybrid/+24h decisions | State×actor×collection matrix, unresolved fields separated | Signed contract version to109; no reopen approval flow |
| TSK-080 | Accept082 status/return DTO/error contract | Read-only adapters/result UI without financial mutation | Poll/return and false-fully-paid edge evidence to113 |
| TSK-081 | Approved original15min/max3 inclusivefirst | Frozen S0-081-v1 retry/worker/late-extra dispositions | Completed design to102/082; checks in S0DecisionLog, not runtime evidence |
| TSK-082 | Freeze provider envelope/attempt and lock protocol | Verified dueOnline credit +all-hold/cap/ledger/outbox transaction | Happy/wrong amount/duplicate/expiry/late proof; schema unchanged outside A manifest |
| TSK-083 | Review099/102 capacity/locking invariant | DB-real same-property/last-room/race cases | Transaction trace/case evidence to091/107; no premature exclusion removal |
| TSK-084 | Approved>=48h/fullcollected/late-unusednetcleaning | Frozen bysource pools/5–10businessdays/incidentcase | Design complete084/109, actualexecution085 pending |
| TSK-085 | Accept110 pool primitives/111 receipts/112 dispute blocks | Request/reserve/manual proof/checker/paid/retry/reversal | Happy refund +over-pool/retry/no-proof cases; no direct Host funds claimed as platform cash |
| TSK-086 | Approvedproductfinancial/ALLARRIVED+24/allgates | Frozenfunding/earningreserve/opsroles/recovery | PRODUCTCONTRACT complete, actualFinanceLegal/signedops evidence095 BLOCKED |
| TSK-087 | Reconcile eligible entitlement/105 metrics and112 gates | Manual payout approval/proof/paid/failure/recovery | Balanced journal and duplicate/blocked payout evidence; no doublepay direct B |
| TSK-088 | Approvedselfowner/caps/platformfund/nozeroexception | Frozenreserve/consume/release/HALF_UP/stableallocation | Designcomplete088/109,089runtimepending |
| TSK-089 | Freeze quote/cap lifecycle/allocation fixture | Enforce self-owner/reserve/consume/release/coverage | Happy+duplicate/refund/zero/base0/rounding test manifest |
| TSK-090 | Approvedtime/children/evidence/completion/review | FrozenALLARRIVED/Guestack-Admincase/14dayexclusive | Designcomplete090/109,112/091/092runtimepending |
| TSK-091 | Accept A date/availability engine API | Align search/detail/read projections/conflict UI | Timezone/adjacency/stale evidence, core mutations remain A |
| TSK-092 | Accept090/112 actual evidence and104 projection | Enforce actor/date/no-show/review guards | Early completion/unauthorized/repeated review edge evidence |
| TSK-093 | PO approves rights/lock/session/callback/refund matrix | A enforces exact SecurityConfig/auth/user boundaries | Locked guest/host negative +legitimate callback/refund cases |
| TSK-094 | Freeze retention/consent/mode/policy snapshot schema | Snapshot and archive without losing future rights | Archive/edit/history/privacy evidence to106/114 |
| TSK-095 | Gather108 readiness/all core evidence and approvals | Release lead checks both modes/all P0/no-go reasons | Signed authorization or explicit blocked status, no sandbox-only claim |
| TSK-096 | After095 only, frame optional two-way review | Assess privacy/moderation/double-blind | PO go/no-go and separate future scope; no MVP dependency |
| TSK-097 | ExplicitPOinventoryboundaryapproval | Frozenexclusivepool/sharedplans/wholeplace1/oneproperty-date-VND/wholecancel | Designcomplete097/109,099runtimepending |
| TSK-098 | Accept099/109 type-plan-policy schema | Host mutation/calendar/ownership/version guards | DTO/symbol/error manifest and capacity/OOO edge tests to100/102/113 |
| TSK-099 | Review composite FK/day locks/migration reservations | Additive schema +capacity transaction primitives | Schema/rollback/constraint test manifest to110/098; old exclusion retained |
| TSK-100 | Accept shared repository/DTO/query limits | Eligible offers/read-only controller projections | All-night/occupancy/no-dates fixture to101; no shared service edits |
| TSK-101 | Accept100 selection/quote/error schema | Inventory guest controls/allocation/stale reconsent UI | Happy/soldout/stale/keyboard report to113; payment cards separate |
| TSK-102 | Accept schema/hold policy/ledger-mode contracts | All-item transaction/idempotency/snapshots/expiry | Happy/Family-night2 rollback/duplicate/adjacency tests to083/103 |
| TSK-103 | Accept109 config/110 primitives/102 held snapshots | Both-mode quote/amount/funding/commission allocations | Full/hybrid/coverage rejection/rounding test fixture to082/111 |
| TSK-104 | Accept102 snapshots/080 status DTO | Review/email payload/read projection updates | Grain/history/payload schema tests to076/092/094, not shared guard edits |
| TSK-105 | Accept ledger/financial metrics definitions | Read-only distinct-header/rooms/payable projections | Reconcile totals/join-duplication evidence to087/113 |
| TSK-106 | Freeze latest migration manifest/provenance baseline | Rehearse additive backfill/reconcile/shadow/rollback | Counts/totals/commitments/financial audit report before107/114 |
| TSK-107 | Freeze106 schema and inventory matrix inputs | Execute approved isolated/integration inventory cases | Exact command/env/duration/artifacts/skips to077, not financial duplication |
| TSK-108 | Import107/077/078/114/052 accepted evidence | Rehearse cutover/pilot/write coordination/monitoring/rollback | Readiness decision to095; activation still blocked until authorization |

Final049: accept matrices→execute both-mode scope→E2E evidence;050: freeze final environment→Compose validation→environment/log/artifact summary;051: inspect supplied diff/evidence→resolve scoped doubts→review verdict;052: reconcile accepted evidence→publish final README/screenshots/tracker→status audit. Những steps này chỉ là kế hoạch, không phải commands đã chạy trong đợt docs.

## Sprint entry/exit, demo và safe parallel

Mỗi sprint entry = deps accepted +claim/files acknowledged +frozen contract version. Exit = tất cả deliverables sprint có AC evidence/reviewer accepted; task blocked giữ unchecked, không invent config hay tiến độ. B có thể chuẩn bị fixture/test/report độc lập ngoài acceptance rank nhưng không claim integrated trước contract/gate. Không chạy suite/integration tự động từ plan; execution cần request hoặc mandatory project policy tại thời điểm triển khai.

| Sprint | Active tasks | Entry / ordered vertical demo / exit | Safe parallel / no-go |
|---|---:|---|---|
| S0 Policy/contracts | 8 | DONE8 design:079→097→081/084→086→088/090→109; frozen contracts/fixtures/errors/consent and checker evidence | S1 both099/110foundations accepted, S1done2/2;095moneyclearance BLOCKED; no reopening approved modes |
| S1 Additive foundation | 2 | DONE2/2:099inventory then110internal financial primitives/rollback designs independently accepted; preserved legacy/capacity +balanced full/hybrid journals | Downstream work still needs own deps/claim/manifest; no shared entities/DTO/migration edits or runtime/release permission inferred from foundation acceptance |
| S2 Host/search/holds | 4 | 098 accepted before100→101 and102; demo one facility/types/eligible search/all-item hold/rollback | B read/UI after098; A booking core102, disjoint exact manifest; no shared PropertyService/DTO/repository writes |
| S3 Both-mode online payment | 6 | 083→091;103→082→089→080 accepted; demo both modes verified O instant, race/late/duplicate and caps | B091 while A103 only on frozen read API; B080 after082; never both PaymentService/BookingService/SecurityConfig |
| S4 Direct receipts/check-in/refund | 3 | 111→112→085; partial receipt/correction/contested arrival/+24h blocked gate/refund pool demo | A deliberately serialized; B fixture UI/test preparation only, no bank verification/actual payout claim |
| S5 Complete operations/UI | 8 | 104→076/092/094;093 access;105→087→113; guest-host-admin full/hybrid end-to-end ops demo | B notification/read metrics while A guards only after payload freeze; disjoint manifests, no simultaneous review/core/shared docs edits |
| S6 Migration +evidence | 3 | 106 before107/114; DB reconciliation/rollback and inventory/financial evidence consumed by077 | B tests only after A migration freeze; no simultaneous mutation of test DB/shared fixtures; record skipped/blockers |
| S7 Final acceptance/docs | 6 | 049→050→051→077→078→052; both modes E2E/Compose/review/evidence/docs accepted | Baseline preflight not closure; B review/report, A integrator publishes shared docs; no077/078 dependency on095 |
| S8 Cutover/release | 2 | 108 readiness→095 authorization, both modes/all core/all P0 signed; no-go if missing | Single integrator A, B evidence reviewer not editor; flags cannot defer hybrid/multi-room |

## Exact-path ownership và claim protocol (freeze lại ở S0)

Prefix Java `src/main/java/com/stayhub/`; resource `src/main/resources/`; tests `src/test/java/com/stayhub/`. Paths dưới là đã xác minh theo listing, **không phải source audit**; exact-file allowed manifest phải acknowledged trước edit. Models/entities và DTO **feature-local**, không có root entities/dto/security package. Existing target tree phía đầu tài liệu không override actual path map này.

| Actual paths / planned exception | Single writer / coordination |
|---|---|
| `src/main/resources/db/migration/` | A only; inspect latest version at execution, reserve filename/version in claim, never blindly reserve V14 or edit applied migration |
| `booking/` incl `BookingServiceImpl.java`, `BookingService.java`, `BookingPriceService.java`; `payment/` incl `PaymentService.java`, `VnpayPaymentService.java`, `MockPaymentService.java` | A transaction/entities/repositories/shared DTO writer. B read-only adapters only exact non-core files explicitly delegated/frozen; no same-file editing |
| `property/` incl `PropertyServiceImpl.java`, `PropertyService.java`, feature-local models/repositories/dto; `discount/`, `review/` guard core | A shared/mutation writer; B read-only projections/controllers only separately frozen manifest, no inferred package-wide permission |
| `config/SecurityConfig.java`, `auth/`, `user/`; root `pom.xml`, `mvnw`, `mvnw.cmd`, `docker-compose.yml`; resources `application.yml`, `application-local.yml`, `application-docker.yml` | A only shared security/config/build writer; B tests/review requests via report, no concurrent edits |
| `admin/AdminController.java`, `admin/AdminRestController.java` | A shared admin integration after explicit handover. **AdminController preexisting dirty: protected, do not overwrite**; B metrics adapters only exact disjoint approved files, never this controller by default |
| `docs/tasklist/TaskList.md`, `docs/business/`, `docs/architecture/3_API_Design.md`, `docs/database/`, `docs/ui/`, `docs/contributors/Rules.md` and named supporting architecture docs | A only integrator writer; B sends report/diff suggestion, not simultaneous editor; README/config doc manifest freeze before078/052 |
| `src/main/resources/templates/{admin,auth,booking,host,payment,property,user,home,error,fragments}/`, `src/main/resources/static/` incl `css/tailwind.css` | B UI single writer after contracts; generated CSS/build ownership/command agreed before edit. A does not patch templates concurrently |
| `notification/`, templates email payloads | B send/retry/presentation writer after A outbox/payload contract; A shared event/DTO only, exact separate files acknowledged |
| `src/test/java/com/stayhub/{booking,config,notification,payment,property,auth,discount,review,storage,support}/`; app tests; `src/test/resources/application-test.yml` | A core isolated tests while implementing, B evidence/integration tests after handoff; exact-file/time reservation for shared directories/DB/fixtures, config A only; directory does not grant simultaneous same-file edits |
| i18n/messages, fixture directories | No existing messages/i18n or tests/fixtures path established. Any new path explicitly **planned**, e.g. `src/test/resources/payment-fixtures/`, must be S0 manifest-approved; do not claim existing files |
| `.opencode/reports/task-<ID>-agent-<A-or-B>-<unique>.md` | Planned per-agent append/new report, never overwrite another actor; shared-doc progress published sequentially by A only |

1. Before edit: `git status`/scoped diff baseline, preserve dirty files; claim task/Agent/exact files/base commit/time/state (`proposed→acknowledged→editing→handoff→accepted`), migration version if any. S0 allowed-path checklist names core/shared exceptions and protected dirty files. Other actor acknowledges **before** edit; contested path pause and ask, no silent ownership takeover.
2. Prefer separate worktree/branch **optional user-manual setup**. Same tree: no reset/checkout/clean/stash/branch switch of others' changes; no shared index/parallel commit. No commits without user request. Do not mark prior edits as yours or overwrite them.
3. Handoff frozen version includes symbols/DTO fields/errors/schema/migration manifest/amount-policy fixture/test command+manifest/results/duration/skips and compatibility notes. Changed contract means pause/rebase dependent work **sequentially**, no parallel shared DTO fixes.
4. A integrates sequentially and updates dependencies/TaskList/business/API/ERD only after accepted contract/evidence. B reviews supplied diff/evidence, not simultaneous editor; run new tests only for changed inputs/unresolved doubt. Each feature implementation needs isolated happy+critical edge RED→GREEN, mocks external only; filename-limited runner verified. No task close without actual evidence; docs-only validation does not stand in for runtime.

---

# Project Done Checklist

- [ ] Core multi-room TSK-097–108 **và cả full/hybrid TSK-109–114** có evidence, migration reconcile/capacity protection đạt; toàn P0/approvals/gates095 đạt trước tiền thật. Docs không đóng implementation task.

- [ ] Chạy được app bằng `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` sau `docker compose up -d db`.
- [ ] Đăng ký / đăng nhập / phân quyền GUEST-HOST-ADMIN hoạt động (`SecurityConfig`).
- [ ] Search property theo địa điểm + ngày + số khách, có filter/sort/pagination.
- [ ] Xem property detail, check availability theo ngày trước khi đặt.
- [ ] Sandbox: ghi nhận baseline paid → PENDING/host approval là hành vi lịch sử, không dùng làm chuẩn nghiệm thu canonical (TSK-079/095).
- [ ] Canonical **S0-109-v1 design frozen, implementation pending**: bothmodes exactquote/consent→allitemhold→verifiedattemptO→CONFIRMED, returnreadonly, directB no wait.109not implementation/095authorization.
- [ ] Host không accept/reject TARGET; direct receipt Host-attested không provider event, actual per-room check-in evidence và +24h eligibility có dispute/refund/reconcile/legal gates, không automatic payout; manual proof/maker-checker đạt.
- [ ] User xem My Bookings, cancel được booking.
- [ ] Sau COMPLETED, user viết được review, hiển thị trên property detail.
- [ ] Admin xem được dashboard tổng quan + danh sách booking toàn hệ thống.
- [x] Nhận được email OTP khi đăng ký và email khi booking đổi trạng thái.
- [ ] `docker compose up -d --build` chạy được toàn bộ stack.

---

# Post-MVP (Optional)

- [ ] VNPay sandbox không đồng nghĩa sẵn sàng tiền thật: đóng gate TSK-095 và toàn bộ P0 trước production, không đẩy an toàn refund/payout sang optional. Momo/provider bổ sung mới là Post-MVP tùy chọn.
- [ ] Wishlist (lưu property yêu thích).
- [ ] Cache rating trung bình thay vì tính lại mỗi lần load property.
- [ ] Export báo cáo (CSV/PDF) cho Admin.
- [ ] Notification real-time (websocket) thay vì chỉ email.
- [ ] Rate limiting cho `auth` (chống brute-force login).

---
