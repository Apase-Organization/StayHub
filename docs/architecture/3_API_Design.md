# API Design

> **Baseline/legacy contracts và flow proposals bên dưới không phải multi-room implemented.** [Canonical TARGET](../business/PropertyInventoryBusinessRules.md), TSK-097–108 là core MVP gate. Các ví dụ boolean property availability/single price chỉ legacy; không dùng làm contract target hay bằng chứng runtime.

## TARGET contract delta (chưa triển khai)

**Approved S0-088-v1 preview/checkout:** self-owner block, one code/no stacking, per-user/code1 accepted-online use, finite positive campaignQ required (never unlimited default). Preview advisory/read-only/no cap promise. Checkout revalidates global/user caps and reserves with original15min all-item hold; retries reuse/attempt failure retains, accepted online consumes once, guarded final-fail/expiry releases; consumed cancellation/refund never restores. Whole-VND HALF_UP/stable largest remainder, platformD<=Cgross/Cnet>=0/O>=Cnet, no Guest fee/zeroT/O/gateway0.400 ERR_SELF_BOOKING/ERR_DISCOUNT_STACKING/ERR_DISCOUNT_INVALID/ERR_NONPOSITIVE_BOOKING_AMOUNT;409 ERR_DISCOUNT_QUOTA_EXHAUSTED/ERR_DISCOUNT_USER_LIMIT/ERR_DISCOUNT_FUNDING/ERR_DISCOUNT_CONFIG/ERR_DEPOSIT_COVERAGE plus stale/version/idempotency conflicts. Rejection rolls back stock/caps/charge; concurrent last-slot loser conflicts. Tax remains unset/disabled/release-blocked. Details Payment2b; DTO/config109 and runtime089 pending.

**Inventory boundary freeze S0-097-v1 (2026-10-23):** user/PO approved exclusive WHOLE_PLACE or ROOM_BASED (no linked sale), truly interchangeable RoomType pools with shared-plan stock, whole-place one type quantity1 (bedrooms descriptive), one property/date range/VND per booking and all-booking cancellation only. PhysicalRoom assignment is future scope. Paths/DTO details remain proposed implementation contracts; these approved domain boundaries are not proposals or runtime evidence. Provenance: [S0DecisionLog](../business/S0DecisionLog.md).

**v2 payment precedence:** [PaymentBusinessRules](../business/PaymentBusinessRules.md) duyệt cả FULL_PREPAY/HYBRID_DEPOSIT core MVP, instant sau required online amount; không Host approval. Tất cả amount verification dưới đây theo **dueOnline của attempt**, không booking total. Legacy mục1–8 chỉ archive, không override v2.

- Search/detail nhận `checkIn/checkOut`, `adults`, `childrenAges`, `rooms` và allocation; trả property duy nhất cùng eligible types/plans, max quantity toàn stay, restrictions, currency và total stay/fee-tax inclusion. Không có dates trả indicative types, không live availability. Không trả dữ liệu booking khác/conflicting guest details.
- Check-availability/discount preview/`POST /bookings` dùng cùng selection: `propertyId`, date range, `items[{roomTypeId,ratePlanId,quantity,roomAllocations:[{adults,childrenAges}]}]`, optional code; checkout có idempotency key. Server kiểm ownership/type-plan-property, allocation và tính lại giá; client không quyết định amount/currency/policy. Preview không giữ stock.
- Booking response/detail chứa header totals/currency/state, items/allocation/nightly breakdown và policy snapshots. All-items hold transaction, conflict `409 ERR_ROOM_NOT_AVAILABLE` không để partial booking/hold; lỗi validation `400`, unauthorized/ownership theo chuẩn bên dưới.
- Host type/plan/calendar mutations là contract cần thiết kế TSK-098; ownership xuyên aggregate, CSRF session mutation, version/concurrency conflict; capacity reductions không phá commitments. Archive không xóa future/historic bookings.
- Payment/status vẫn booking-level; return read-only, verified IPN idempotent atomic conversion mọi holds, late paid reconcile. Cancel toàn header MVP, partial item cancellation chưa hỗ trợ. Provider IPN envelope giữ đúng giao thức provider, không ép thành ApiResponse; AJAX nội bộ vẫn ApiResponse.

## TARGET v1 — concrete proposed contract (chưa implemented)

Các path dưới là **đề xuất**, kể cả path trùng legacy; không bảo đảm source đã nhận payload này. Dùng fixture property42 Deluxe5 + Family2 tại canonical mục8; ngày theo timezone property, khoảng nửa mở. Cookie session + CSRF cho mutation nội bộ; public GET chỉ active published data. Quote/booking thuộc guest; host phải có ownership property→type→plan (admin override phải explicit/audited). Cross-owner trả404, anonymous401, role/CSRF403.

| Method / proposed path | Input / output contract |
|---|---|
| GET `/api/v1/properties` | location, checkIn/checkOut optional cùng cặp, adults, repeated childrenAges, rooms, roomAllocations (URL-encoded JSON cùng shape bên dưới), filters/page/sort; mỗi property một result, eligible offer combination và totalStay/basis, không phân trang mỗi type |
| GET `/api/v1/properties/{propertyId}` | metadata/gallery + roomTypes capacity/beds/photos/type amenities và plans conditions; không dates thì `availabilityStatus=NOT_QUERIED`, quantity null, indicative price có basis, không live stock |
| POST `/api/v1/properties/{propertyId}/availability` | selection dates/rooms/allocations; trả offers theo type/plan, `availableQuantity`, restrictions, `asOf`; advisory, không giữ stock; không lộ conflicts của khách khác |
| POST `/api/v1/booking-quotes` | selection đầy đủ dưới; server tính discount/fees/taxes; quote id/expiry/policy version và per-room nightly breakdown; không giữ stock/caps |
| POST `/api/v1/bookings` | quoteId + acceptedPolicyVersion; `Idempotency-Key`; atomic revalidate→hold mọi item/night, trả201 header/items/snapshots/holdExpiresAt/paymentUrl nếu cần |
| GET `/api/v1/bookings/{id}` | owner/support authorized: header dates/currency/totals, states, historical type/plan names/policies, quantity/allocations/nightly charges; không đọc giá live |
| GET `/api/v1/payments/bookings/{id}/status` | owner read-only: bookingStatus/paymentStatus/refundStatus, no provider raw data; pending/reconcile không giả confirmed |
| POST `/api/v1/bookings/{id}/cancel` | reason + version + idempotency key; all-booking cancel theo policy snapshot, release đúng commitments; refund request/status độc lập, không đồng nghĩa tiền đã hoàn |

### Selection / quote example

POST quote (currency là yêu cầu được validate, không cho client quyết định amount):

```json
{
  "propertyId": 42,
  "checkIn": "2030-12-10",
  "checkOut": "2030-12-12",
  "currency": "VND",
  "paymentMode": "FULL_PREPAY",
  "rooms": 3,
  "adults": 6,
  "childrenAges": [7],
  "items": [
    {"roomTypeId": 7, "ratePlanId": 70, "quantity": 2,
     "roomAllocations": [{"adults": 2, "childrenAges": []}, {"adults": 2, "childrenAges": []}]},
    {"roomTypeId": 8, "ratePlanId": 80, "quantity": 1,
     "roomAllocations": [{"adults": 2, "childrenAges": [7]}]}
  ],
  "discountCode": "DEMO200"
}
```

`quantity == roomAllocations.length`, rooms=sum(quantity), adults=sum allocated adults, childrenAges multiset khớp mọi allocation; kiểm từng phòng theo capacity/age policy đã duyệt. Cùng type khác plan cộng quantity trước kiểm shared pool; duplicate type/plan items reject400 để tránh nhập nhằng ordinals. S0-097-v1 rejects mixed property/date/currency; VND only, whole-place quantity1. Mọi amount/policy/guestId do client thêm không được tin (unknown fields reject400).

Response quote minh họa ApiResponse `data` (outer success=true/message/errorCode=null):

```json
{
  "quoteId": "q42",
  "expiresAt": "2030-11-01T10:05:00Z",
  "policyVersion": "demo-v1",
  "paymentMode": "FULL_PREPAY",
  "allowedPaymentModes": ["FULL_PREPAY", "HYBRID_DEPOSIT"],
  "paymentPolicyVersion": "payment-demo-v2",
  "dueOnline": 6900000,
  "balanceDueHost": 0,
  "currency": "VND",
  "items": [
    {"roomTypeId": 7, "ratePlanId": 70, "quantity": 2, "availableQuantity": 5,
     "nightlyPerRoom": [{"date": "2030-12-10", "roomCharge": 1000000}, {"date": "2030-12-11", "roomCharge": 1000000}], "roomCharges": 4000000},
    {"roomTypeId": 8, "ratePlanId": 80, "quantity": 1, "availableQuantity": 2,
     "nightlyPerRoom": [{"date": "2030-12-10", "roomCharge": 1500000}, {"date": "2030-12-11", "roomCharge": 1500000}], "roomCharges": 3000000}
  ],
  "itemFees": 0, "taxes": 0,
  "bookingFees": [{"scope": "PER_BOOKING_PER_STAY", "amount": 100000}],
  "subtotal": 7100000, "discount": 200000, "totalStay": 6900000,
  "taxDisclosure": "Fixture taxes zero; not a commercial tax policy"
}
```

Fixture quote timestamp/5-minute interval không thiết lập TTL thương mại. Equal nightlyPerRoom chỉ là compact quote display; booking detail phải expand roomOrdinal1/2 × từng ngày với allocations, fees/taxes/discount allocations và policy snapshots theo ERD; tổng không nhân quantity lần2. Search from-offer cho yêu cầu này phải basis3rooms/7guests/2nights và total6.900.000 với discount eligibility được nêu; nếu không áp code thì total7.100.000. Không lấy Deluxe1.000.000 làm tổng stay.

Submit body `{"quoteId":"q42","acceptedPolicyVersion":"demo-v1"}`; quote bound to actor + immutable selection/hash, expiry và currency server-validated. Nếu stock, price, policy hoặc discount thay đổi: **409**, không giữ partial items, không tạo charge; trả updated quote khi tính được và UI yêu cầu consent mới. Quote expired `ERR_QUOTE_EXPIRED`; price/policy `ERR_QUOTE_STALE`; stock `ERR_ROOM_NOT_AVAILABLE`; version/calendar `ERR_VERSION_CONFLICT`; key reuse khác body `ERR_IDEMPOTENCY_CONFLICT`. Same actor + operation/path + key + canonical body hash trả cùng booking/result (kể cả retry concurrent), không tạo hold/charge thứ hai; key lifecycle/retention phải duyệt, không tái dùng key cho quote mới. Preview400 cho dates/allocations/code không hợp lệ; 500 sanitized message/correlation ID, không SQL/stack/PII.

Booking201 `data` gồm `id`, `propertyId`, dates, currency, totals, `paymentMode`, `paymentPolicyVersion`, `dueOnline`, `balanceDueHost`, `paymentAttemptId`, `bookingStatus=PENDING_PAYMENT`, `onlinePaymentStatus=PENDING`, `holdExpiresAt`, immutable `items` expanded snapshots và checkout URL đã validate. Sau verified IPN đủ O/active hold cả hai mode instant CONFIRMED, không chờ direct B. Expiry/cancel release mọi holds/caps một lần; late paid có thể attempt SUCCESS nhưng booking không CONFIRMED, refund/reconciliation riêng. Return `/payments/vnpay/return` và polling **read-only**, kể cả query có signature; không xác nhận từ browser redirect.

### TARGET v2 — dual-mode financial contract

Quote request phải có `paymentMode`; server kiểm mode thuộc allowed modes **mọi selected plan**, merchant/config version và quote binding. Mode/rate/base/rounding/fee/commission/funding/consent snapshots frozen; client không gửi commission hay amount. Mode đổi invalid quote, 409 `ERR_QUOTE_STALE`; mode không allowed400 `ERR_PAYMENT_MODE_NOT_ALLOWED`, coverage không khả thi409 `ERR_DEPOSIT_COVERAGE`, không tạo hold/charge. Submit quote bound mode/version không được override. Plan mutation có `allowedPaymentModes` và paymentPolicyVersion server validated, không tạo stock riêng.

Quote example ở trên **FULL_PREPAY only**. Cùng selection đổi `paymentMode=HYBRID_DEPOSIT`, server tạo quote khác, guest projection counterpart:

```json
{"quoteId":"q42-hybrid","paymentMode":"HYBRID_DEPOSIT","paymentPolicyVersion":"payment-demo-v2","currency":"VND","totalStay":6900000,"dueOnline":1380000,"balanceDueHost":5520000,"balanceCollection":"DIRECT_HOST_CASH_OR_BANK_AT_CHECKIN","depositRateDisclosure":"20% fixture only, rollout config pending","refundResponsibility":"Policy allocation: platform online pool; Host direct receipts"}
```

Fixture basis tại payment canonical: fee100.000 Host entitlement, discount200.000 Host-funded, tax/provider cost0; illustrative commission1.035.000. **Host/admin-only** settlement projection: full heldHostPayable5.865.000; hybrid345.000 +direct5.520.000; commissionDue1.035.000/reserved chưa tự earned. Không expose commission/internal ledger/reconcile/provider raw/evidence private trong guest/public quote. Guest thấy authorized receipt summary và refund trách nhiệm, không bank credentials.

| Proposed endpoint (chưa implemented) | Protected contract / invariants |
|---|---|
| POST `/api/v1/bookings/{id}/payment-attempts` | Guest owner +CSRF; version/key. Approved S0-081-v1: exact server expected O, no installments; immutable merchant/ref/currency/config/deadline. Max3 creations inclusivefirst, allocate atomically before provider call (failure/timeout counts); same-key/body replay no extra ordinal. holdExpiresAt=original booking createdAt+15min, active t<expiry, no extension/new creation at t>=expiry or after accepted credit. Callback failure changes attempt only; worker final-fails early only if all3 terminal unsuccessful/no credit; any pending can settle before expiry. Serialize allocation/IPN/worker/all-item-cap changes; late/extra money receipt+reconcile/refund, no resurrection/double-credit. Return read-only. Quote fixture 5min is preview TTL, not hold TTL |
| GET `/api/v1/payments/bookings/{id}/status` | Guest owner/support read-only: bookingStatus, paymentMode, onlinePaymentStatus, onlinePaidVerified, balanceDueHost, directPaidAttested, directOutstanding, directReceiptStatus, refundStatus; **không** map SUCCESS=fully paid; hosted receipt không providerVerified |
| POST `/api/v1/host/bookings/{id}/direct-receipts` | Host ownership +CSRF; amount/currency/method CASH or BANK_TRANSFER, paidAt/reference/evidenceHandle/version/key; append partial attestation, no provider event; correction endpoint `/direct-receipts/{receiptId}/reversals` audit original, không delete |
| POST `/api/v1/bookings/{id}/direct-receipts/{receiptId}/acknowledgments` | Guest owner +CSRF, acknowledge/contest/version/key; independent evidence, không certify bank collection; refused/partial case tracked |
| POST `/api/v1/host/bookings/{id}/check-in-events` | Host ownership +CSRF; itemId/roomOrdinal/actualAt/timezone/evidenceHandle/version/key; không tap-to-pay. Guest owner `/api/v1/bookings/{id}/check-in-events/{eventId}/acknowledgments`; contested admin resolution audited |
| POST `/api/v1/bookings/{id}/disputes` | Guest owner +CSRF; reason/affected room/receipt/evidence/version/key; host response dưới host prefix; admin disposition dưới admin prefix, authorization mỗi resource |
| POST `/api/v1/admin/bookings/{id}/refunds` | Admin maker/checker +CSRF; policyVersion/reason/amount/currency/version/key, split totalPolicyRefund/platformRefundable/hostResponsibility; paid action `/refunds/{refundId}/paid` cần proof/reference và checker khác maker; reserve online pool atomically |
| GET `/api/v1/admin/payout-eligibility` | Admin only; actual check-in evidence boundary +24h, blocked reasons/reserves/commission adjustment/liquidity/reconcile/legal; scheduled date không eligible |
| POST `/api/v1/admin/bookings/{id}/payouts` | Admin +CSRF/version/key, entitlement-scoped amount; recheck eligibility dưới lock; `/payouts/{payoutId}/paid` proof/reference/maker-checker; manual, không claim auto bank transfer |

Financial mutation body example (evidence handle private; key in `Idempotency-Key`):

**Approved S0-084-v1 refund projection:** expose snapshot scheduled check-in/cancel request instants, >=48h free-window decision, actual-stay evidence/case reason, named charge gross/discount/net allocations, collected source and Rp/Rh entitlements with source-specific paid/reserved/remaining. Free-window/Host inability returns all collected Guest cash; late/no-show only collected unused net-cleaning without actual check-in; after-arrival incident requires Admin disposition. Server computes amount/policy, never client-asserted eligibility. Reserve under lock bounded by both source policy remaining and collected-minus-paid-minus-reserved plus liquidity; no Host money in platform pool, no automatic B charge/monthly invoice. Status projection distinguishes REQUESTED/PROCESSING/PAID and proof, platform SLA5–10businessdays from eligibleRequestAt (retry unchanged), Host direct separately without promised SLA. Calendar/holiday source and tax/legal clearance unresolved → paid activation blocked, tax config unset/disabled; never infer tax law. Exact DTO/error freeze109 and execution085 remain pending.

```json
{"amount":300000,"currency":"VND","method":"CASH","paidAt":"2030-12-10T08:00:00Z","reference":"host-receipt-1","evidenceHandle":"private-evidence-opaque-id","expectedVersion":4}
```

Every mutation uses actor+operation+resource+key+body hash, retry same result, changed body409 `ERR_IDEMPOTENCY_CONFLICT`; stale expectedVersion409 `ERR_VERSION_CONFLICT`. Validate positive amount/currency/allowed actor, prevent aggregate receipt beyond B (overpayment case), cross-owner404, role/CSRF403/anonymous401; evidence scoped private/audit, no PII logs. Correction/reversal references originals. Refund guard onlineCollected −paidRefund −reservedRefund and policy remaining, plus liquidity; `ERR_REFUND_POOL_EXCEEDED`, `ERR_PAYOUT_NOT_ELIGIBLE`, `ERR_CHECKIN_CONTESTED`, `ERR_MANUAL_PROOF_REQUIRED` are409 business conflicts. No self-approval. Guest sees totalPolicyRefund vs platform part vs Host direct responsibility, no internal commission amounts.

IPN matches **attempt ID/ref/merchant, expected O/currency/scaling**, signature and provider envelope/result; serialize booking credit, all-item conversion, caps, ledger/outbox. Unique provider transaction prevents multiple credit. No blanket callback auth bypass; no guest-session requirement for exact provider callback. Online success does not settle direct B; unsolicited extra money still recorded and reconciled, not discarded. Unknown/late attempts never revive inventory. Check-in/receipt policy/evidence/all-arrived vs per-room boundary must be approved before dependent delivery, not silently selected by endpoint design.

### Host nested contracts

Prefix proposed `/api/v1/host/properties/{p}`. `GET/POST /room-types`, `GET/PUT/DELETE /room-types/{t}` (DELETE archive); fields name, capacity/maxAdults/maxOccupants, beds, type amenities/photos, baseQuantity/status/version. `POST /room-types/{t}/images` và `DELETE /room-types/{t}/images/{imageId}` ownership + validation/cleanup như property images. `GET/POST /room-types/{t}/rate-plans`, `GET/PUT/DELETE /room-types/{t}/rate-plans/{r}` cho pricing/currency/policy/restrictions/version, không có stock riêng. Archive không xóa snapshots/commitments hay tự cancel booking; publish cần hợp lệ mọi domain.

`GET/PUT /room-types/{t}/calendar`: PUT body `{"days":[{"date":"2030-12-10","total":5,"outOfOrder":1,"stopSale":false,"expectedVersion":3}]}`. **Absolute update**, không delta/increment theo retry; unique type/day, version check409, khóa toàn batch cùng stable order, all-or-nothing. Không nhận held/confirmed từ host. Reject409 nếu total−OOO < active commitments; stopSale chỉ chặn bán mới. Response mỗi day có version mới/held/confirmed/remaining; không tiết lộ guest. Capacity sửa baseQuantity không rewrite các ngày đã có commitments âm thầm; effective-date preview và warning/reject bắt buộc. Host plan calendar restrictions không clone inventory.

### Provider boundary (TARGET security)

Calendar `stopSale` ở type/day áp dụng mọi plan; plan/day stop-sale nằm trong pricing/restrictions của nested rate-plan contract và chỉ chặn plan đó. Cả hai chỉ đổi sales eligibility: offer availableQuantity0 khi đóng, còn `remaining = total - outOfOrder - held - confirmed >= 0` không đổi; mọi plan vẫn dùng chung pool. Không release commitments hay từ chối conversion hợp lệ của hold cũ chỉ vì đóng bán sau khi hold; capacity invariant vẫn bắt buộc khi stopSale=true.

`GET /api/v1/payments/vnpay/ipn` là server callback proposed behavior, không guest session/CSRF; verify chữ ký trên params chuẩn provider, merchant, reference, amount scaled chính xác, currency theo payment/provider config và cả responseCode/transactionStatus. Không permit wildcard API. Provider envelope `{"RspCode":"00","Message":"Confirm Success"}` theo giao thức, không ApiResponse; duplicate/out-of-order xử lý idempotent theo ref/event và state matrix, acknowledge đúng kết quả đã commit. Payment+convert holds+discount+outbox atomic, expiry serialize theo ERD. Failure sanitize/log correlation audit không secret/raw PII; không lộ raw provider response cho guest.

## CURRENT/LEGACY reference — phần 1–8 bên dưới

Giữ nguyên để trace lịch sử; endpoint lists và full-total/host approval examples không phải runtime audit hay contract target. Sample conflictingDates không được đưa vào target response; instant đã duyệt ở v2 nhưng baseline từng paid→PENDING (TaskList), không suy source đã đúng. JSON có HTTP headers/ellipsis là legacy pseudo-example, không phải executable JSON.

## 1. Nguyên tắc chung

StayHub sử dụng **RESTful API** cho các endpoint trả về dữ liệu dạng JSON (dùng cho AJAX, Alpine.js, htmx). Các trang chính sử dụng **Thymeleaf** và được phục vụ qua các controller trả về template.

- **Endpoint cho API**: Luôn có tiền tố `/api/v1/`.
- **Endpoint cho View**: Không có tiền tố `/api`, trả về tên template.

## 2. Chuẩn response

Tất cả API response đều được bọc trong đối tượng `ApiResponse<T>`:

```text
{
  "success": true,
  "message": "Thành công",
  "data": { ... },
  "errorCode": null
}
```

- `success`: `true` hoặc `false`.
- `message`: Thông báo dành cho người dùng (có thể hiển thị lên UI).
- `data`: Dữ liệu trả về (có thể là object, array, null).
- `errorCode`: Mã lỗi nội bộ (nếu có), ví dụ `"ERR_ROOM_NOT_AVAILABLE"`.

## 3. HTTP Status codes

| Status | Mô tả |
| :--- | :--- |
| `200 OK` | Thành công (GET, PUT, DELETE) |
| `201 Created` | Tạo mới thành công (POST) |
| `400 Bad Request` | Dữ liệu gửi lên không hợp lệ |
| `401 Unauthorized` | Chưa đăng nhập |
| `403 Forbidden` | Không có quyền truy cập |
| `404 Not Found` | Không tìm thấy tài nguyên |
| `409 Conflict` | Xung đột dữ liệu (ví dụ: phòng đã được đặt) |
| `500 Internal Server Error` | Lỗi server |

## 4. Danh sách API chính

### 4.1. Authentication & User

| Method | Endpoint | Mô tả | Yêu cầu |
| :--- | :--- | :--- | :--- |
| `GET` | `/login` | Trang đăng nhập | - |
| `POST` | `/login` | Xử lý đăng nhập | `username`, `password` |
| `GET` | `/oauth2/authorization/google` | Bắt đầu đăng nhập Google OAuth2 | Spring Security OAuth2 client |
| `GET` | `/login/oauth2/code/google` | Callback Google OAuth2 | Google redirect URI |
| `GET` | `/register` | Trang đăng ký | - |
| `POST` | `/register` | Gửi OTP đăng ký qua email, chưa tạo user | `fullName`, `username`, `email`, `password`, `confirmPassword` |
| `GET` | `/register/verify` | Trang nhập OTP đăng ký | Query: `email` |
| `POST` | `/register/verify` | Xác thực OTP và tạo user `LOCAL` | `email`, `otp` |
| `POST` | `/logout` | Đăng xuất | - |

### 4.2. Property & Search

| Method | Endpoint | Mô tả | Yêu cầu |
| :--- | :--- | :--- | :--- |
| `GET` | `/` | Trang chủ | - |
| `GET` | `/home` | Alias trang chủ | - |
| `GET` | `/properties` | Trang kết quả tìm kiếm | Query params: `location`, `checkIn`, `checkOut`, `guests`, `page`, `sort`... |
| `GET` | `/properties/{id}` | Trang chi tiết property | Path: `id` |
| `GET` | `/api/v1/properties` | API tìm kiếm trả về JSON | Tương tự query params + filter (price, type, amenities...) |
| `GET` | `/api/v1/properties/{id}` | API lấy chi tiết property (JSON) | Path: `id` |

### 4.3. Booking

| Method | Endpoint | Mô tả | Yêu cầu |
| :--- | :--- | :--- | :--- |
| `GET` | `/properties/{id}/book` | Trang đặt phòng | Path: `propertyId`, query: `checkIn`, `checkOut`, `guests` |
| `POST` | `/bookings` | Tạo booking mới | Body: `propertyId`, `checkIn`, `checkOut`, `guests`, `guestInfo`... |
| `GET` | `/my-bookings` | Trang danh sách booking của tôi | - |
| `GET` | `/bookings/{id}` | Trang chi tiết booking | Path: `id` |
| `GET` | `/bookings/{id}/payment` | Trang payment legacy/future scope | Flow VNPay mới dùng redirect checkout + return page |
| `POST` | `/api/v1/bookings/check-availability` | Kiểm tra khả dụng (AJAX) | Body: `propertyId`, `checkIn`, `checkOut` |
| `POST` | `/api/v1/bookings/{id}/cancel` | Hủy booking | Path: `id` |
| `GET` | `/api/v1/payments/bookings/{bookingId}/status` | Poll trạng thái thanh toán/booking sau khi quay về từ VNPay | Path: `bookingId` |

### 4.3.1. Discount

| Method | Endpoint | Mô tả | Yêu cầu |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/discounts/validate` | Kiểm tra mã giảm giá và tính lại tổng tiền | `propertyId`, `checkIn`, `checkOut`, `guests`, `code` |

### 4.3.2. VNPay payment

| Method | Endpoint | Mô tả | Yêu cầu |
| :--- | :--- | :--- | :--- |
| `POST` | `/bookings` | Tạo booking `PENDING_PAYMENT`, payment `PENDING`, rồi redirect sang VNPay | Form booking + optional `discountCode` |
| `GET` | `/payments/vnpay/return` | Trang kết quả sau khi VNPay redirect về StayHub | VNPay query params + `vnp_SecureHash` |
| `GET` | `/api/v1/payments/vnpay/ipn` | VNPay IPN/webhook xác nhận thanh toán | VNPay query params + `vnp_SecureHash` |

### 4.4. Host

| Method | Endpoint | Mô tả | Yêu cầu |
| :--- | :--- | :--- | :--- |
| `GET` | `/host/dashboard` | Trang dashboard của Host | - |
| `GET` | `/host/properties` | Danh sách property của host | - |
| `GET` | `/host/properties/new` | Form tạo property mới | - |
| `POST` | `/host/properties` | Tạo property mới | Body form |
| `GET` | `/host/properties/{id}/edit` | Form chỉnh sửa property | Path: `id` |
| `PUT` | `/host/properties/{id}` | Cập nhật property | Path: `id`, Body |
| `DELETE` | `/host/properties/{id}` | Xóa property | Path: `id` |
| `GET` | `/api/v1/host/properties` | Danh sách property của host hiện tại | Session HOST/ADMIN |
| `POST` | `/api/v1/host/properties` | Tạo property ở trạng thái DRAFT | JSON + CSRF |
| `GET/PUT/DELETE` | `/api/v1/host/properties/{id}` | Đọc/cập nhật/archive property thuộc host | Ownership + CSRF khi mutate |
| `POST` | `/api/v1/host/properties/{id}/images` | Upload JPEG/PNG | Multipart + CSRF |
| `DELETE` | `/api/v1/host/properties/{id}/images/{imageId}` | Xóa ảnh | Ownership + CSRF |
| `PUT` | `/api/v1/host/properties/{id}/images/{imageId}/cover` | Chọn ảnh cover | Ownership + CSRF |
| `PUT` | `/api/v1/host/properties/{id}/images/order` | Sắp xếp toàn bộ gallery | JSON + CSRF |
| `GET` | `/api/v1/amenities` | Danh mục amenities public | - |
| `POST` | `/api/v1/host/bookings/{id}/accept` | Host chấp nhận booking | Path: `id` |
| `POST` | `/api/v1/host/bookings/{id}/reject` | Host từ chối booking | Path: `id` |

### 4.5. Admin

| Method | Endpoint | Mô tả | Yêu cầu |
| :--- | :--- | :--- | :--- |
| `GET` | `/admin/dashboard` | Dashboard admin | - |
| `GET` | `/admin/bookings` | Quản lý booking toàn hệ thống | - |
| `GET` | `/api/v1/admin/stats` | Thống kê tổng quan | - |
| `POST` | `/api/v1/admin/users/{id}/lock` | Khóa tài khoản người dùng | Path: `id` |
| `POST` | `/api/v1/admin/users/{id}/unlock` | Mở khóa tài khoản | Path: `id` |

### 4.6. Review

| Method | Endpoint | Mô tả | Yêu cầu |
| :--- | :--- | :--- | :--- |
| `GET` | `/bookings/{id}/review` | Trang viết review | Path: `id` |
| `POST` | `/reviews` | Gửi review | Body: `bookingId`, `rating`, `comment` |

### 4.7. Payment pipeline

Với tích hợp VNPay, `POST /bookings` vẫn là entrypoint chính của flow thanh toán. Backend tạo booking/payment ở trạng thái pending rồi redirect user sang VNPay, thay vì tạo mock payment success ngay lập tức.

### 4.8. Endpoint coverage note

- Các form/link Thymeleaf hiện cover flow chính: đăng ký OTP, đăng nhập local/Google, search, property detail, booking, my bookings, host dashboard, admin users/bookings.
- Một số REST API không được gọi trực tiếp từ template vì dành cho AJAX, client ngoài, hoặc future SPA/mobile: `/api/v1/host/properties/**`, `GET /api/v1/properties/**`, `GET /api/v1/amenities`, và các API booking ngoài `check-availability`.
- Menu `Help` và `Wishlists` trong navbar là placeholder Post-MVP, chưa có route backend tương ứng.
- `/home` là alias của `/`; UI ưu tiên link `/`.
- `/bookings/{id}/payment` còn template/controller legacy nhưng không nằm trong flow VNPay chính; flow mới dùng VNPay checkout redirect và `/payments/vnpay/return`.

## 5. Ví dụ request/response

### 5.1. Kiểm tra khả dụng

**Request:**
```http
POST /api/v1/bookings/check-availability
Content-Type: application/json

{
  "propertyId": 1,
  "checkIn": "2026-09-22",
  "checkOut": "2026-09-25"
}
```

**Response thành công:**
```json
{
  "success": true,
  "message": "Available",
  "data": { "available": true },
  "errorCode": null
}
```

**Response thất bại (xung đột):**
```json
{
  "success": false,
  "message": "Property is not available for the selected dates.",
  "data": {
    "available": false,
    "conflictingDates": [
      { "checkIn": "2026-09-20", "checkOut": "2026-09-23" }
    ]
  },
  "errorCode": "ERR_ROOM_NOT_AVAILABLE"
}
```

### 5.2. Tạo booking

**Request:**
```
POST /bookings
Content-Type: application/x-www-form-urlencoded (hoặc JSON)
propertyId=1&checkIn=2026-09-22&checkOut=2026-09-25&guests=2
```

**Response (redirect đến trang booking detail hoặc my bookings):**
- Với mock payment hiện tại: nếu thành công, redirect `302` đến `/bookings/{id}`.
- Với VNPay: nếu tạo payment thành công, redirect `302` đến VNPay checkout URL.
- Nếu thất bại, hiển thị lỗi.

### 5.3. Validate discount

**Request:**
```http
POST /api/v1/discounts/validate
Content-Type: application/json

{
  "propertyId": 1,
  "checkIn": "2026-09-22",
  "checkOut": "2026-09-25",
  "guests": 2,
  "code": "STAY10"
}
```

**Response thành công:**
```json
{
  "success": true,
  "message": "Discount applied",
  "data": {
    "code": "STAY10",
    "subtotalPrice": 1500000,
    "discountAmount": 150000,
    "totalPrice": 1350000
  },
  "errorCode": null
}
```

Backend chỉ dùng response này để preview trên UI. Khi user submit booking, backend phải tính lại giá và validate lại discount; không tin số tiền từ frontend.

### 5.4. VNPay IPN

**Request:**
```text
GET /api/v1/payments/vnpay/ipn?vnp_TxnRef=...&vnp_Amount=135000000&vnp_ResponseCode=00&vnp_TransactionStatus=00&vnp_SecureHash=...
```

**Xử lý:**
- Verify `vnp_SecureHash` bằng `VNPAY_HASH_SECRET`.
- Tìm payment bằng `vnp_TxnRef`.
- Kiểm tra `vnp_Amount / 100` khớp `payments.amount`.
- Nếu `vnp_ResponseCode=00` và `vnp_TransactionStatus=00`, cập nhật payment `SUCCESS`, booking `CONFIRMED`, tăng `discount_codes.used_count` nếu có, rồi gửi email confirmed.
- Nếu callback bị gửi lại, xử lý idempotent và không cộng usage nhiều lần.

**Response VNPay:**
```json
{
  "RspCode": "00",
  "Message": "Confirm Success"
}
```

Các mã chính:

| RspCode | Ý nghĩa |
| :--- | :--- |
| `00` | Confirm Success |
| `01` | Order not found |
| `02` | Order already confirmed |
| `04` | Invalid amount |
| `97` | Invalid checksum |
| `99` | Unknown error |

### 5.5. Host chấp nhận booking (AJAX)

**Request:**
```
POST /api/v1/host/bookings/5/accept
```

**Response thành công:**
```json
{
  "success": true,
  "message": "Booking accepted",
  "data": null,
  "errorCode": null
}
```

**Response thất bại (không hợp lệ state):**
```json
{
  "success": false,
  "message": "Booking is not in PENDING state",
  "data": null,
  "errorCode": "ERR_INVALID_STATE"
}
```

## 6. Authentication và Authorization

- Sử dụng **session-based authentication** (Spring Security).
- Các endpoint `/api/v1/**` yêu cầu xác thực, trừ `GET` public được khai báo cụ thể như đọc/search property. Không permit toàn bộ HTTP methods trên một public path pattern.
- Request API thay đổi dữ liệu dùng session cookie phải gửi CSRF token. Thymeleaf cung cấp `${_csrf.token}` và `${_csrf.headerName}` để JavaScript gửi đúng header.
- Lỗi security của `/api/**` luôn trả `ApiResponse` JSON: `ERR_UNAUTHORIZED` (401), `ERR_FORBIDDEN` (403), hoặc `ERR_CSRF` (403). Route MVC vẫn redirect anonymous user đến `/login` và trả HTML khi có lỗi.
- Phân quyền:
  - `/host/**` và `/api/v1/host/**` chỉ dành cho `HOST` hoặc `ADMIN`.
  - `/admin/**` và `/api/v1/admin/**` chỉ dành cho `ADMIN`.
  - `/my-bookings`, `/bookings/**` yêu cầu đăng nhập (bất kỳ role nào, nhưng sẽ kiểm tra ownership).
- Sử dụng `@PreAuthorize` hoặc cấu hình trong `SecurityConfig` để enforce.
- Public property detail chỉ trả listing `ACTIVE`. Mọi mutation Host kiểm tra ownership bằng `principal.id`; ID không thuộc Host trả 404 để tránh enumeration.
- Listing mới luôn là `DRAFT`; chỉ chuyển `ACTIVE` khi có cover. `DELETE` property là archive sang `INACTIVE` để giữ lịch sử cho Booking/Review.
- Upload chỉ nhận JPEG/PNG hợp lệ, giới hạn dung lượng/số lượng/pixel; file mới được xóa khi transaction rollback và file cũ chỉ bị xóa sau khi database commit.

## 7. Error handling

Lỗi REST controller được xử lý trong `ApiExceptionHandler` và trả `ApiResponse`. Lỗi MVC controller được xử lý trong `MvcExceptionHandler` và trả Thymeleaf error page. Lỗi phát sinh trong Spring Security filter chain do `ApiAuthenticationEntryPoint`/`ApiAccessDeniedHandler` xử lý.

Các custom exception:
- `ResourceNotFoundException`: 404
- `BusinessException`: 409 hoặc 400
- `InvalidStateTransitionException`: 409
- `AuthenticationException`: 401
- `AccessDeniedException`: 403

## 8. Tài liệu liên quan

- [Quy chuẩn API trong Rules.md](../contributors/Rules.md)
- [Luồng nghiệp vụ sơ bộ](0_DemoSystem.md)
