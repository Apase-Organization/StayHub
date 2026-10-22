# QUY CHUẨN LÀM VIỆC CHUNG CỦA DỰ ÁN STAYHUB (PROJECT RULES & CONVENTIONS)

## 1. GIT WORKFLOW & BRANCHING

**Two-agent safety override cho MVP redesign:** [TaskList](../tasklist/TaskList.md) giữ single-writer ownership/claim protocol. Không chạy git checkout/reset/clean/stash/branch switch trên working tree có thay đổi của người khác; các bước Git minh họa dưới chỉ dùng trong workspace cá nhân sạch, không phải permission commit/push tự động. Separate branches/worktrees là tùy chọn do user tự tạo; không shared-index parallel commit, stage đúng files khi được yêu cầu. Giữ preexisting edits, reviewer không đồng thời editor.

**S0-109-v1 FROZEN_DESIGN** [policy](../business/contracts/s0-policy-v1.json)/[typed API](../business/contracts/s0-api-v1.json)/[handoff](../business/S0DecisionLog.md#s0-final-handoff--tsk-109): full/hybrid instant core, exactattemptO notT, immutable policy/fixture/hash/consent. WholeVND20%deposit/15%room+cleancommission/platformD/netcoverage/noGuestfee,900s/max3,090actual/review and084refund approved. DirectHostmemo notplatformcash, reserve notearned; privateproof/differenthumanmakerchecker/allgates, no monthlyinvoice/QR/autotransfer. Productconfig nowfrozen; missing actual provider/tax/legal/contracts/calendar/signedops/liquidity/runtime evidence blocks095 not invented PO choice. No S1work before user-reviewed PRmerge and acknowledged exactpath manifest; docs notruntimeproof.
Dự án áp dụng Branching Model cơ bản của Github Flow:
*   **`main` branch:** Source code sạch tuyệt đối để Deploy lên Server Thật (Production). Chỉ Leader/PM mới được phép nhấn nút Merge vào nhánh này.
*   **`dev` branch:** Nhánh môi trường STAGING. Tất cả anh em dev sẽ ghép code vào đây để review chéo và test tổng thể trước khi release. Cấm push thẳng (direct push) vào nhánh này.
*   **Nhánh cá nhân (Feature / Fixbug):** Bắt buộc phải rẽ nhánh từ `dev`.
    *   Cú pháp nhánh: `<loại_nhánh>/TSK-<ID-task>`
    *   Ví dụ làm tính năng: `feature/TSK-010`
    *   Ví dụ sửa bug: `fixbug/TSK-010`
    *   Ví dụ viết docs/setup/cấu hình: `chore/TSK-001`
    *   Ví dụ refactor code: `refactor/TSK-015`

## 2. QUY TẮC COMMIT MESSAGE (CONVENTIONAL COMMITS)
Bắt buộc phải có Tiền tố và Mã Task để dễ theo dõi lịch sử:
*   `feat: [TSK-010] tạo trang tìm kiếm property theo địa điểm` *(Thêm tính năng mới)*
*   `fix: [TSK-020] sửa lỗi tính sai tổng tiền booking khi có phí dịch vụ` *(Sửa lỗi)*
*   `chore: [TSK-002] cập nhật Spring Boot lên 3.x và cài Tailwind` *(Cấu hình, thư viện, không tác động code logic)*
*   `refactor: [TSK-015] tách logic tính giá booking ra BookingPriceService` *(Sửa code nhưng không làm thay đổi tính năng)*
*   `docs: [TSK-029] thêm file hướng dẫn setup Docker Compose` *(Cập nhật tài liệu)*

---

## 3. GIT STEP-BY-STEP (QUY TRÌNH HÀNG NGÀY)

Quy trình chuẩn khi bắt tay vào làm một Task mới:

```bash
# 1. Chuyển về nhánh dev và cập nhật code mới nhất từ team
git checkout dev
git pull origin dev

# 2. Tạo nhánh mới cho task của mình
git checkout -b feature/TSK-010

# 3. Add và Commit code
git add .
git commit -m "feat: [TSK-010] implement property search API"

# 4. Push nhánh cá nhân lên Github
git push -u origin feature/TSK-010
```
⚠️ **Quan trọng:** Sau khi push, lên Github tạo một Pull Request (PR) từ nhánh feature/TSK-010 vào nhánh dev. Gắn thẻ (Tag/Assign) một thành viên khác trong team để Review Code. Review xong mới được bấm Merge.

## 4. REST API STANDARDS (BACKEND)

**TARGET precedence:** [canonical decisions](../business/PropertyInventoryBusinessRules.md) → [API contracts](../architecture/3_API_Design.md), [ERD](../database/3_CoreERD.md), [UI](../ui/UXUI_DesignSystem.md) → [tasks/evidence](../tasklist/TaskList.md). Không triển khai theo demo legacy khi mâu thuẫn. Classification, sale/access mode và stock là khái niệm riêng; không dùng property overlap để khóa toàn bộ types. Docs target không chứng minh source đã hỗ trợ.

ApiResponse chỉ áp dụng API nội bộ; provider IPN giữ envelope giao thức riêng, verify signature/merchant/reference/amount/currency/result, không yêu cầu guest session/CSRF. Chỉ miễn CSRF đúng callback đã xác minh, không miễn `/api/**`. Server lỗi trả message an toàn + correlation ID, không stack trace/SQL/secret/PII hay dữ liệu booking khác.

> Lưu ý: StayHub là ứng dụng **Monolith render server-side bằng Thymeleaf**, nên phần lớn luồng chính (xem trang chủ, search, chi tiết property, booking...) sẽ đi qua **Controller trả về View (`.html`)**, không phải REST API JSON. Chuẩn REST bên dưới áp dụng cho các endpoint **AJAX nội bộ** (gọi từ Alpine.js/htmx trong trang, ví dụ: lọc property theo giá, check ngày trống, autocomplete địa điểm...) và cho các API phục vụ app mobile/admin export dữ liệu (nếu có sau này).

### Cấu trúc JSON Response (Chuẩn 1 chiều)
  - Tất cả API response (dù thành công hay thất bại) phải được bọc vào một class DTO duy nhất (`ApiResponse<T>`) để Frontend (Alpine.js/htmx) dễ dàng parse JSON.
```jsonc
{
  "success": true, // true | false
  "message": "Lấy danh sách property thành công",
  "data": {       // Payload trả về (nếu mảng trống trả [], nếu không có data trả null)
     "properties": [
       { "id": 12, "title": "The River Apartment", "pricePerNight": 1500000 }
     ],
     "totalResults": 120
  },
  "errorCode": null // Mã lỗi nội bộ để Frontend map UI (VD: "ERR_ROOM_NOT_AVAILABLE"), không có lỗi thì null
}
```

### Quy tắc định tuyến (Routing)
  - Dùng danh từ số nhiều, viết thường (lowercase), phân cách bằng dấu gạch ngang (kebab-case).
  - **Đúng:** `GET /api/v1/properties`, `GET /api/v1/search-locations`, `POST /api/v1/bookings/check-availability`
  - **Sai:** `GET /api/v1/getProperty`, `GET /api/v1/Search`
  - Các Controller trả về **View Thymeleaf** thì dùng route thân thiện, không cần tiền tố `/api`:
    - **Đúng:** `GET /properties/{id}`, `GET /host/dashboard`, `GET /admin/bookings`
    - **Sai:** `GET /property-detail-page`, `GET /viewHostDashboard`

### Quy tắc HTTP Status Code
  - `200 OK`: Trả về thành công (Dùng cho GET, PUT, DELETE).
  - `201 Created`: Tạo mới thành công (Dùng cho POST, VD: tạo booking mới).
  - `400 Bad Request`: Client gửi sai data, thiếu query params (VD: thiếu check-in/check-out khi search).
  - `401 Unauthorized` / `403 Forbidden`: Chưa đăng nhập / không đủ quyền (VD: guest cố vào trang `/admin`).
  - `404 Not Found`: Không tìm thấy dữ liệu (VD: property đã bị xoá hoặc không tồn tại).
  - `409 Conflict`: Trạng thái nghiệp vụ xung đột (VD: property đã hết phòng trống ngày khách chọn).
  - `500 Internal Server Error`: Lỗi server (DB sập, exception chưa handle...).

### Session API và CSRF
  - StayHub dùng session cookie, vì vậy mọi API thay đổi dữ liệu (`POST`, `PUT`, `PATCH`, `DELETE`) phải giữ CSRF protection.
  - JavaScript lấy token/header name do Thymeleaf cung cấp và gửi token trong request header; không disable CSRF cho toàn bộ `/api/**`.
  - Security failure của API phải trả JSON `ApiResponse`; security failure của route MVC giữ redirect/HTML behavior.

## 5. CODE STYLE & CONVENTIONS

### Đối với Backend (Java / Spring Boot)
  - **Class (Controller, Service, Entity, DTO...):** Dùng `PascalCase`, kèm hậu tố thể hiện vai trò rõ ràng:
    - `PropertyController`, `BookingService`, `BookingRepository`, `PropertyEntity` (hoặc `Property` nếu không trùng tên DTO), `CreateBookingRequest`, `BookingResponse`.
  - **Biến, phương thức (method):** Dùng `camelCase` (vd: `findAvailableProperties()`, `checkInDate`).
  - **Hằng số (constant):** Dùng `UPPER_SNAKE_CASE` (vd: `MAX_GUEST_PER_BOOKING`).
  - **Cấu trúc package theo tính năng (feature-based), không theo layer:**
    ```
    com.stayhub.property.PropertyController
    com.stayhub.property.PropertyService
    com.stayhub.property.PropertyRepository
    com.stayhub.booking.BookingController
    com.stayhub.booking.BookingService
    ```
  - **Bắt buộc** format code theo chuẩn mặc định của IDE (hoặc Spotless/Checkstyle nếu project cấu hình) trước khi commit.
  - **Xử lý lỗi:** Dùng `@ControllerAdvice`/`@RestControllerAdvice` để bắt exception tập trung, không `try-catch` rồi nuốt lỗi (`catch (Exception e) {}` rỗng là **cấm**).
  - **DTO tách biệt Entity:** Không trả trực tiếp JPA Entity ra View/API, luôn map qua DTO để tránh lộ field nhạy cảm (VD: password hash) và tránh lỗi lazy-loading.
  - **Email identity:** Email user phải được trim và lowercase bằng `Locale.ROOT` trước khi lưu hoặc lookup; database migration bảo vệ canonical format.

### Đối với Frontend (Thymeleaf + Tailwind CSS + Alpine.js/htmx)
   - TARGET chọn quantity/type/plan và allocation từng phòng, không nhập amount đáng tin; invalidation quote khi đổi dates/guests/items/code. Hiển thị total stay/điều khoản và conflict cần consent mới, không silently charge; redirect không phải confirmation. Giữ tokens StayHub, không sao chép branding Airbnb/Booking.
  - **Tên file template:** `kebab-case`, đặt tên theo trang/chức năng (vd: `property-detail.html`, `booking-payment.html`, `host-dashboard.html`).
  - **Fragment dùng chung** (header, footer, navbar...) đặt trong thư mục `fragments/` (vd: `fragments/navbar.html`), gọi bằng `th:replace` hoặc `th:insert`.
  - **Biến trong Thymeleaf (`th:*`):** Dùng `camelCase`, đồng bộ tên với field trong DTO/Model phía Java (vd: model có `pricePerNight` thì template dùng `${property.pricePerNight}`).
  - **CSS:** Ưu tiên dùng class utility của Tailwind trực tiếp trong HTML, hạn chế viết CSS custom riêng trừ khi thực sự cần (animation, style đặc thù).
  - **JS tương tác nhẹ (Alpine.js/htmx):** Viết inline trong thẻ HTML (`x-data`, `hx-get`...) cho các tương tác đơn giản; nếu logic phức tạp hơn thì tách ra file riêng trong `static/js/`, đặt tên `kebab-case` (vd: `property-search.js`).
  - Không được để sót `console.log()` trong code khi tạo Pull Request.

### Đối với Database (PostgreSQL)
   - TARGET additive/backfill giữ IDs/snapshots, không auto-merge property trùng địa chỉ. Capacity protection type/date phải có hiệu lực và reconcile trước bỏ property exclusion. Plans chia sẻ stock, hold→confirmed không trừ hai lần; mọi nights/items atomic. Thiết kế docs không được xem là migration đã apply.
  - **Tên Bảng (Table) và Cột (Column):** Dùng `snake_case` chữ thường (vd: `properties`, `bookings`, `check_in_date`, `price_per_night`, `host_id`).
  - **Khoá ngoại (Foreign key):** Đặt tên theo mẫu `<bảng_số_ít>_id` (vd: `property_id`, `guest_id`, `host_id`).
  - **Migration:** Mọi thay đổi schema phải đi qua file Flyway trong `src/main/resources/db/migration/`, đặt tên theo chuẩn `V{version}__{mo_ta_ngan}.sql` (vd: `V5__add_status_column_to_bookings.sql`). **Không** sửa trực tiếp DB production bằng tay.
  - Không dùng tiếng Việt có dấu, không dùng khoảng trắng trong tên bảng/cột.

---
