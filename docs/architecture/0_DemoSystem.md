> **LEGACY/demo archive:** toàn bộ wireframes, full-total/single-payment và property-boolean availability bên dưới là lịch sử, không chứng nhận runtime. [Inventory](../business/PropertyInventoryBusinessRules.md), [Payment v2](../business/PaymentBusinessRules.md) và [screens](../ui/ApplicationDesigned.md) có ưu tiên. User đã duyệt cả FULL_PREPAY/HYBRID_DEPOSIT core MVP, instant sau verified dueOnline +active all-item hold, không Host accept/reject. Hybrid balance trực tiếp Host lúc actual check-in, không chờ balance để confirm. Return/poll chỉ đọc; payout chỉ eligible actual-check-in+24h có dispute/reconcile/refund/legal gates, không automatic transfer. TSK-095 phải có evidence cả hai mode; legacy hình bên dưới không dùng làm target acceptance.

### Flow
```
User truy cập website
        │
        ▼
     Homepage
        │
        ▼
Search:
- Destination
- Check-in
- Check-out
- Guests
        │
        ▼
Search Results
        │
        ├── Filter
        ├── Sort
        └── Pagination
        │
        ▼
Property Detail
        │
        ├── Images
        ├── Description
        ├── Amenities
        ├── Location
        ├── Reviews
        ├── Availability
        └── Price
        │
        ▼
Select room / booking
        │
        ▼
Booking Information
        │
        ├── Guest information
        ├── Check-in / Check-out
        ├── Number of guests
        ├── Discount code
        └── Price summary
        │
        ▼
VNPay Payment
        │
        ▼
VNPay IPN verifies payment
        │
        ▼
Create/Confirm Booking
        │
        ▼
Booking Confirmed
        │
        ▼
My Bookings
        │
        ├── View detail
        ├── Cancel
        └── Review after stay
```

### User truy cập website
```
Guest -> Browse -> Search -> View Property
Book -> Login/Register
```

### Homepage
- Header:
    - Logo, Search destination, Login(register), Become a host
    - Nếu đã login: Profile, My Booking, Wishlist, Logout
- Body:
    - Search box:
    ```
    ┌───────────────────────────────────────────────────┐
    │ Where           Check-in    Check-out    Guests   │
    │ Ho Chi Minh     20/09       23/09        2        │
    │                                      [ Search ]   │
    └───────────────────────────────────────────────────┘
    ```
    - Popular destination:
        - Ho Chi Minh
        - Da Nang
        - Da Lat
        - Nha Trang
        - Ha Noi
    - Featured properties:
        - Property A
        - Property B
        - Property C
    - Popular categories:
        - Apartment
        - Villa
        - Hotel
        - Homestay
        - Resort

### Search results
- Layout:
    ```
    ┌───────────────┬───────────────────────────────────┐
    │               │                                   │
    │   FILTER      │       PROPERTY LIST               │
    │               │                                   │
    │ Price         │ ┌───────────────────────────────┐ │
    │               │ │ Image │ Property information  │ │
    │ Property type │ │       │ Rating                │ │
    │               │ │       │ Location              │ │
    │ Bedrooms      │ │       │ Price                 │ │
    │               │ └───────────────────────────────┘ │
    │ Amenities     │                                   │
    │               │ ┌───────────────────────────────┐ │
    │ Rating        │ │ Image │ Property information  │ │
    │               │ └───────────────────────────────┘ │
    └───────────────┴───────────────────────────────────┘
    ```
- Filter:
    - Price range
    - Property type
    - Bedrooms
    - Beds
    - Bathrooms
    - Guests
    - Amenities
    - Rating
- Amenities:
    - Wi-Fi
    - Swimming pool
    - Parking
    - Air conditioning
    - Kitchen
    - Washer
    - TV
- Sort:
    - Price: Low → High
    - Price: High → Low
    - Rating: High → Low
- Pagination:
    - < 1 2 3 4 5 >

### Property details
- Image gallery:
    ```
    ┌─────────────────────────────────────┐
    │                                     │
    │          Main image                 │
    │                                     │
    ├─────────┬─────────┬─────────┬───────┤
    │ Image 2 │ Image 3 │ Image 4 │ +10   │
    └─────────┴─────────┴─────────┴───────┘
    ```
- Property information:
    ```
    The River Apartment

    ⭐ 4.8 (126 reviews)

    📍 District 1, Ho Chi Minh City

    Entire apartment
    2 bedrooms · 2 beds · 2 bathrooms
    4 guests
    ```
- Description:
    ```
    About this place

    ...
    ```
- Amenities:
    ```
    ✓ Wi-Fi
    ✓ Swimming pool
    ✓ Air conditioning
    ✓ Kitchen
    ✓ Parking
    ✓ Washing machine
    ```
- Reviews:
    ```
    ⭐ 4.8 / 5

    ★★★★★
    "Very clean and convenient..."
    Nguyen Van A

    ★★★★☆
    "Great location..."
    Tran Van B
    ```
    
### Availability check
- Trước khi user booking:
    ```
    Check-in
    Check-out
    Guests
    ```
- Backend check:
    ```
    Is property available for this date range?
    ```
- Example:
    ```
    Property A

    20/09 ───── 23/09
         BOOKED

    User:
    22/09 ───── 25/09
    ```
    - Không cho đặt


### Booking page
- Flow: 
    ```
    Property Detail -> Reserve -> Booking page
    ```
- Booking page:
    ```
    ┌─────────────────────────────────────────────┐
    │ Your booking                                │
    ├─────────────────────────────────────────────┤
    │                                             │
    │ Property                                    │
    │ The River Apartment                         │
    │                                             │
    │ Check-in      20 Sep                        │
    │ Check-out     23 Sep                        │
    │ Guests        2                             │
    │                                             │
    │ ─────────────────────────────────────────── │
    │                                             │
    │ 1,000,000 VND × 3 nights    3,000,000 VND   │
    │ Cleaning fee                   200,000 VND  │
    │ Service fee                    150,000 VND  │
    │ Discount code [ STAY10 ] [Apply]            │
    │ Discount                      -150,000 VND  │
    │                                             │
    │ Total                         3,200,000 VND │
    │                                             │
    │              [ Pay with VNPay ]             │
    └─────────────────────────────────────────────┘
    ```
- Flow VNPay payment:
    - Payment:
    ```
    Pay with VNPay -> VNPay Checkout -> VNPay IPN -> Payment Success -> Booking CONFIRMED
    ```
    - Status payment:
    ```
    Payment status = PENDING -> SUCCESS
    ```
    - Database:
    ```
    payments

    id
    booking_id
    amount
    payment_method
    status
    paid_at
    ```
    - Example:
    ```
    payment_method = VNPAY
    status = PENDING/SUCCESS
    ```
    
- Status Booking:
    - Flow:
    ```
    User
     ↓
    Create Booking PENDING_PAYMENT
     ↓
    VNPay Payment Success
     ↓
    Booking = CONFIRMED
     ↓
    User receives notification
    ```
    - Booking status enum:
    ```
    PENDING_PAYMENT
    CONFIRMED
    CANCELLED
    REJECTED
    COMPLETED
    ```
    - User confirm:
    ```
             ┌───────────────────┐
             │  PENDING_PAYMENT  │
             └─────────┬─────────┘
                    ┌──┴──┐
                    ↓     ↓
              CONFIRMED CANCELLED
                  │
                  ↓
              COMPLETED
                  │
                  ↓
               REVIEW
    ```
    - Nếu user cancel:
    ```
    PENDING_PAYMENT ──────> CANCELLED
    CONFIRMED ────> CANCELLED
    ```
    - Host confirm is skipped in the VNPay MVP flow. If restored later, add `PENDING_HOST_CONFIRMATION` after payment success.
     - Booking SUCCESSFULLY:
    ```
    My Bookings

    ┌─────────────────────────────────────────────┐
    │ The River Apartment                         │
    │                                             │
    │ 20 Sep → 23 Sep                             │
    │ 2 guests                                    │
    │                                             │
    │ Status: CONFIRMED                           │
    │ Total: $180                                 │
    │                                             │
    │ [ View Details ] [ Cancel ]                 │
    └─────────────────────────────────────────────┘
    ```
    - Detail:
    ```
    Booking #BK20260001

    Property:
    The River Apartment

    Guest:
    Ho Duy

    Check-in:
    20/09/2026

    Check-out:
    23/09/2026

    Guests:
    2

    Total:
    3,200,000 VND

    Payment:
    SUCCESS

    Status:
    CONFIRMED
    ```
     
### LAST FLOW:
```
                       ┌───────────┐
                       │   GUEST   │
                       └─────┬─────┘
                             │
                             ↓
                         Homepage
                             │
                             ↓
                         Search
                             │
                             ↓
                    Search Results
                             │
                   ┌─────────┴─────────┐
                   ↓                   ↓
                Filter               Sort
                   │                   │
                   └─────────┬─────────┘
                             ↓
                     Property Detail
                             │
                             ↓
                     Check Availability
                             │
                             ↓
                         Booking
                             │
                             ↓
                    Apply Discount
                         │
                         ↓
                    VNPay Payment
                             │
                             ↓
                    IPN Verification
                             │
                             ↓
                       CONFIRMED
                       │
                       ↓
                   CHECK-IN
                       │
                       ↓
                  CHECK-OUT
                       │
                       ↓
                   COMPLETED
                       │
                       ↓
                    REVIEW
```
