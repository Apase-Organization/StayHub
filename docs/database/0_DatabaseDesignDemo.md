# StayHub MVP — Database Design & Diagrams

> **CURRENT/LEGACY design archive, không phải schema đầy đủ đã xác minh hay TARGET.** SQL/overlap/search/1:1-payment/full-total/migration list bên dưới là lịch sử, không chạy như migration hay dùng để reserve version tiếp theo. [Inventory](../business/PropertyInventoryBusinessRules.md), [Payment v2](../business/PaymentBusinessRules.md), [ERD](3_CoreERD.md) thay thế target. Full/hybrid cùng core MVP, instant đã duyệt, nhiều attempts/direct receipts/per-room actual check-in/balanced ledger/refund/payout/disputes tại [Payment data](8_Payment.md), không Post-MVP. Exclusion giữ tới additive backfill/capacity protection/reconcile. Không schema/source change trong lần soạn docs này.

> **Purpose:** Database and domain design proposal for the StayHub MVP, based on the project's planned modules: `user`, `property`, `booking`, `payment`, `review`, `host`, `admin`, `storage`, and `notification`.
>
> **Database:** PostgreSQL  
> **Migration:** Flyway  
> **ORM:** Spring Data JPA / Hibernate  
> **Primary key strategy:** `BIGINT` generated IDs for the MVP

---

# 1. Domain Overview

The MVP can be divided into the following main domains:

```mermaid
flowchart TB
    U[User & Authentication]
    P[Property Management]
    B[Booking]
    PAY[Payment]
    D[Discount]
    R[Review]
    S[Search & Availability]
    N[Notification]

    U --> P
    U --> B
    P --> S
    P --> B
    D --> B
    B --> PAY
    B --> R
    U --> R
    B --> N
```

## Main relationships

- A **User** can act as `GUEST`, `HOST`, or `ADMIN`.
- A **Host** owns multiple **Properties**.
- A **Property** contains multiple **PropertyImages** and many **Amenities** through `property_amenities`.
- A **Property** owns one or more **RoomTypes** (hạng phòng), each providing multiple **RatePlans** (gói giá) sharing physical inventory.
- A **Guest** can create multiple **Bookings**.
- Each **Booking** contains one or more **BookingItems** (chọn `RoomType` và `RatePlan`), phân bổ khách qua **RoomAllocations** và snapshot giá qua **NightlySnapshots**.
- A **Booking** can optionally apply one **DiscountCode** and stores price/discount snapshots.
- A **Booking** supports Dual Payment Modes (`FULL_PREPAY` vs `HYBRID_DEPOSIT`).
- A completed **Booking** can have at most one **Review**.
- Sơ đồ quan hệ chi tiết và các ràng buộc toàn vẹn xem tại [3_CoreERD.md](3_CoreERD.md).

---

# 2. Core ERD

```mermaid
erDiagram

    USERS {
        bigint id PK
        varchar email UK
        varchar username UK
        varchar password_hash
        varchar full_name
        varchar phone
        varchar role
        varchar status
        varchar login_provider
        varchar provider_id
        timestamptz created_at
        timestamptz updated_at
    }

    PROPERTIES {
        bigint id PK
        bigint host_id FK
        varchar title
        text description
        varchar address
        varchar city
        numeric price_per_night
        int max_guests
        int bedrooms
        int beds
        int bathrooms
        varchar property_type
        varchar status
        timestamptz created_at
        timestamptz updated_at
    }

    PROPERTY_IMAGES {
        bigint id PK
        bigint property_id FK
        varchar image_url
        varchar public_id
        int display_order
        boolean is_cover
        timestamptz created_at
        timestamptz updated_at
    }

    AMENITIES {
        bigint id PK
        varchar name UK
        varchar icon
        timestamptz created_at
        timestamptz updated_at
    }

    PROPERTY_AMENITIES {
        bigint property_id PK, FK
        bigint amenity_id PK, FK
    }

    BOOKINGS {
        bigint id PK
        bigint property_id FK
        bigint guest_id FK
        date check_in_date
        date check_out_date
        int guests
        numeric nightly_price
        numeric cleaning_fee
        numeric service_fee
        numeric subtotal_price
        bigint discount_code_id FK
        numeric discount_amount
        numeric total_price
        varchar status
        timestamptz cancelled_at
        timestamptz created_at
        timestamptz updated_at
    }

    DISCOUNT_CODES {
        bigint id PK
        varchar code UK
        varchar name
        varchar type
        numeric value
        numeric max_discount_amount
        numeric min_booking_amount
        timestamptz starts_at
        timestamptz ends_at
        int usage_limit
        int used_count
        int per_user_limit
        boolean active
        timestamptz created_at
        timestamptz updated_at
    }

    PAYMENTS {
        bigint id PK
        bigint booking_id FK
        varchar payment_method
        varchar provider
        varchar status
        numeric amount
        varchar currency
        varchar transaction_id UK
        varchar provider_txn_ref UK
        varchar provider_transaction_no
        text raw_response
        timestamptz paid_at
        timestamptz created_at
        timestamptz updated_at
    }

    REVIEWS {
        bigint id PK
        bigint booking_id FK
        bigint property_id FK
        bigint guest_id FK
        smallint rating
        text comment
        timestamptz created_at
        timestamptz updated_at
    }

    USERS ||--o{ PROPERTIES : "hosts"
    USERS ||--o{ BOOKINGS : "creates"
    USERS ||--o{ REVIEWS : "writes"

    PROPERTIES ||--o{ PROPERTY_IMAGES : "contains"
    PROPERTIES ||--o{ BOOKINGS : "receives"
    PROPERTIES ||--o{ REVIEWS : "has"

    PROPERTIES ||--o{ PROPERTY_AMENITIES : "has"
    AMENITIES ||--o{ PROPERTY_AMENITIES : "assigned to"

    DISCOUNT_CODES ||--o{ BOOKINGS : "applies to"
    BOOKINGS ||--|| PAYMENTS : "has"
    BOOKINGS ||--o| REVIEWS : "can produce"
```

---

# 3. Relational Model

## 3.1 `users`

| Column | Type | Constraint | Description |
|---|---|---|---|
| `id` | BIGINT | PK | User identifier |
| `email` | VARCHAR(255) | NOT NULL, UNIQUE | Login email |
| `username` | VARCHAR(80) | UNIQUE | Username for local accounts |
| `password_hash` | VARCHAR(255) | NOT NULL | BCrypt password hash |
| `full_name` | VARCHAR(150) | NOT NULL | User full name |
| `phone` | VARCHAR(30) | NULL | Contact phone |
| `role` | VARCHAR(20) | NOT NULL | `GUEST`, `HOST`, `ADMIN` |
| `status` | VARCHAR(20) | NOT NULL | `ACTIVE`, `LOCKED`, `INACTIVE` |
| `login_provider` | VARCHAR(20) | NOT NULL | `LOCAL`, `GOOGLE` |
| `provider_id` | VARCHAR(255) | NULL | External OAuth2 provider subject/id |
| `created_at` | TIMESTAMPTZ | NOT NULL | Creation timestamp |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Last update timestamp |

### Recommended constraints

```sql
CONSTRAINT chk_users_role
CHECK (role IN ('GUEST', 'HOST', 'ADMIN'));

CONSTRAINT chk_users_status
CHECK (status IN ('ACTIVE', 'LOCKED', 'INACTIVE'));
```

### Relationship

```text
users
 ├── 1 : N properties
 ├── 1 : N bookings
 └── 1 : N reviews
```

---

## 3.2 `properties`

| Column | Type | Constraint | Description |
|---|---|---|---|
| `id` | BIGINT | PK | Property identifier |
| `host_id` | BIGINT | FK → users.id | Owner/host |
| `title` | VARCHAR(255) | NOT NULL | Property title |
| `description` | TEXT | NOT NULL | Description |
| `address` | VARCHAR(500) | NOT NULL | Address |
| `city` | VARCHAR(100) | NOT NULL | Searchable city |
| `price_per_night` | NUMERIC(12,2) | NOT NULL | Base nightly price |
| `cleaning_fee` | NUMERIC(12,2) | NOT NULL, default 0 | Host-configured cleaning fee |
| `max_guests` | INT | NOT NULL | Maximum guests |
| `bedrooms` | INT | NOT NULL | Bedroom count |
| `beds` | INT | NOT NULL | Bed count |
| `bathrooms` | INT | NOT NULL | Bathroom count |
| `property_type` | VARCHAR(30) | NOT NULL | Property category |
| `status` | VARCHAR(20) | NOT NULL | Listing state |
| `rating_avg` | NUMERIC(3,2) | NOT NULL, default 0 | Cached review average |
| `created_at` | TIMESTAMPTZ | NOT NULL | Creation timestamp |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Last update timestamp |

### Suggested values

```text
property_type:
- APARTMENT
- HOUSE
- VILLA
- HOTEL_ROOM
- HOMESTAY
- RESORT

status:
- ACTIVE
- INACTIVE
- DRAFT
```

### Relationship

```text
users (HOST)
       1
       │
       │ owns
       ▼
properties
  ├── 1 : N property_images
  ├── N : M amenities
  ├── 1 : N bookings
  └── 1 : N reviews
```

---

## 3.3 `property_images`

| Column | Type | Constraint | Description |
|---|---|---|---|
| `id` | BIGINT | PK | Image identifier |
| `property_id` | BIGINT | FK | Related property |
| `image_url` | VARCHAR(1000) | NOT NULL | Image URL |
| `public_id` | VARCHAR(255) | NULL | Cloudinary/local storage identifier |
| `display_order` | INT | NOT NULL | Gallery order |
| `is_cover` | BOOLEAN | NOT NULL | Cover image flag |
| `created_at` | TIMESTAMPTZ | NOT NULL | Creation timestamp |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Last update timestamp |

### Important rule

At most one image should be marked as the cover image for each property.

V4 enforces the invariant with a partial unique index; the service additionally requires a cover before a listing becomes `ACTIVE`:

```text
For each property:
COUNT(property_images WHERE is_cover = true) <= 1
```

Host delete operations archive listings by moving them to `INACTIVE`. They do not physically delete property rows that will be referenced by Booking and Review history.

---

## 3.4 `amenities`

| Column | Type | Constraint | Description |
|---|---|---|---|
| `id` | BIGINT | PK | Amenity identifier |
| `name` | VARCHAR(100) | UNIQUE | Amenity name |
| `icon` | VARCHAR(100) | NULL | Icon identifier |
| `created_at` | TIMESTAMPTZ | NOT NULL | Creation timestamp |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Last update timestamp |

Examples:

```text
WiFi
Air Conditioning
Kitchen
Pool
Parking
Washer
TV
Workspace
```

---

## 3.5 `property_amenities`

This table implements the many-to-many relationship.

| Column | Type | Constraint |
|---|---|---|
| `property_id` | BIGINT | PK, FK → properties.id |
| `amenity_id` | BIGINT | PK, FK → amenities.id |

```mermaid
flowchart LR
    P[Property] -->|1..N| PA[property_amenities]
    A[Amenity] -->|1..N| PA
```

---

# 4. Booking Data Model

## 4.1 `bookings`

| Column | Type | Constraint | Description |
|---|---|---|---|
| `id` | BIGINT | PK | Booking identifier |
| `property_id` | BIGINT | FK → properties.id | Booked property |
| `guest_id` | BIGINT | FK → users.id | Guest |
| `check_in_date` | DATE | NOT NULL | Check-in |
| `check_out_date` | DATE | NOT NULL | Check-out |
| `guests` | INT | NOT NULL | Guest count |
| `nightly_price` | NUMERIC(12,2) | NOT NULL | Snapshot of nightly price |
| `cleaning_fee` | NUMERIC(12,2) | NOT NULL | Snapshot fee |
| `service_fee` | NUMERIC(12,2) | NOT NULL | Snapshot fee |
| `subtotal_price` | NUMERIC(12,2) | NOT NULL | Price before discount |
| `discount_code_id` | BIGINT | FK → discount_codes.id, NULL | Applied discount code |
| `discount_amount` | NUMERIC(12,2) | NOT NULL | Discount snapshot |
| `total_price` | NUMERIC(12,2) | NOT NULL | Final total after discount |
| `status` | VARCHAR(20) | NOT NULL | Booking status |
| `cancelled_at` | TIMESTAMPTZ | NULL | Cancellation timestamp |
| `created_at` | TIMESTAMPTZ | NOT NULL | Creation timestamp |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Last update timestamp |

## Why store price snapshots?

Do not calculate historical booking totals from the current `properties.price_per_night`.

Example:

```text
January booking:
price_per_night = 1,000,000 VND

March:
Host changes property price to 1,500,000 VND
```

The January booking must still preserve:

```text
nightly_price = 1,000,000
```

Therefore:

```text
properties.price_per_night
        ≠
bookings.nightly_price
```

`bookings` stores the financial snapshot at booking time, including discount snapshots. The backend recalculates price and discount during booking creation; frontend discount previews are not trusted.

---

# 5. Booking State Diagram

```mermaid
stateDiagram-v2

    [*] --> PENDING_PAYMENT : Booking submitted

    PENDING_PAYMENT --> CONFIRMED : VNPay success
    PENDING_PAYMENT --> CANCELLED : VNPay failed/cancelled/expired

    CONFIRMED --> CANCELLED : Guest cancels
    CONFIRMED --> COMPLETED : Stay finished

    CANCELLED --> [*]
    COMPLETED --> [*]
```

## State transition matrix

| Current | Action | Next |
|---|---|---|
| `PENDING_PAYMENT` | VNPay confirms success through verified IPN | `CONFIRMED` |
| `PENDING_PAYMENT` | VNPay failed/cancelled/expired | `CANCELLED` |
| `CONFIRMED` | Guest cancels | `CANCELLED` |
| `CONFIRMED` | Stay completed | `COMPLETED` |

Invalid transitions should raise a business exception.

For example:

```text
COMPLETED → CANCELLED  ❌
CANCELLED → CONFIRMED  ❌
```

---

# 6. Availability / Date Overlap Model

A new booking request conflicts with an existing active booking when:

```text
requested.check_in < existing.check_out
AND
requested.check_out > existing.check_in
```

Equivalent SQL concept:

```sql
WHERE property_id = :propertyId
  AND status IN ('PENDING_PAYMENT', 'CONFIRMED')
  AND check_in_date < :requestedCheckOut
  AND check_out_date > :requestedCheckIn
```

## Visual example

```text
Existing:
|--------- Occupied ---------|
10        11        12        13        14

Request A:
      |----- overlap -----|
      11        12        13

=> NOT AVAILABLE
```

Adjacent bookings should normally be allowed:

```text
Existing:
[ Check-in 10 ] -------- [ Check-out 12 ]

New:
                            [ Check-in 12 ] ----- [ Check-out 15 ]

=> AVAILABLE
```

This is why the overlap condition uses:

```text
new_check_in < existing_check_out
AND
new_check_out > existing_check_in
```

rather than inclusive comparisons.

---

# 7. Payment Model

## `payments`

| Column | Type | Constraint | Description |
|---|---|---|---|
| `id` | BIGINT | PK | Payment identifier |
| `booking_id` | BIGINT | FK, UNIQUE | Related booking |
| `payment_method` | VARCHAR(20) | NOT NULL | `MOCK`, `VNPAY`, `MOMO` |
| `provider` | VARCHAR(20) | NOT NULL | `MOCK`, `VNPAY` |
| `status` | VARCHAR(20) | NOT NULL | Payment state |
| `amount` | NUMERIC(12,2) | NOT NULL | Payment amount |
| `currency` | VARCHAR(3) | NOT NULL | `VND` |
| `transaction_id` | VARCHAR(255) | UNIQUE | Gateway transaction reference |
| `provider_txn_ref` | VARCHAR(100) | UNIQUE | VNPay `vnp_TxnRef` |
| `provider_transaction_no` | VARCHAR(100) | NULL | VNPay `vnp_TransactionNo` |
| `raw_response` | TEXT | NULL | Raw gateway response for audit/debugging |
| `paid_at` | TIMESTAMPTZ | NULL | Success time |
| `created_at` | TIMESTAMPTZ | NOT NULL | Creation timestamp |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Last update timestamp |

## Payment states

```text
PENDING
SUCCESS
FAILED
CANCELLED
EXPIRED
REFUNDED
```

## VNPay relationship

```mermaid
sequenceDiagram
    participant Guest
    participant StayHub
    participant VNPay
    participant DB

    Guest->>StayHub: Submit booking + optional discount
    StayHub->>DB: Create booking PENDING_PAYMENT
    StayHub->>DB: Create payment PENDING provider=VNPAY
    StayHub-->>Guest: Redirect to VNPay checkout URL
    Guest->>VNPay: Pay with bank/card/QR
    VNPay-->>StayHub: IPN with vnp_SecureHash
    StayHub->>StayHub: Verify hash and amount
    StayHub->>DB: Payment SUCCESS, booking CONFIRMED
    VNPay-->>Guest: Return URL
    Guest->>StayHub: Payment result page polls status
    StayHub-->>Guest: Redirect /my-bookings
```

### Recommended business sequence

```text
1. Validate request
2. Check property availability
3. Calculate subtotal and validate discount
4. Create booking with PENDING_PAYMENT
5. Create payment with PENDING and provider_txn_ref
6. Redirect to VNPay checkout URL
7. VNPay calls IPN/webhook
8. Verify secure hash and amount
9. If payment SUCCESS:
      payment = SUCCESS
      booking = CONFIRMED
      increment discount usage if applicable
10. Result page polls status and redirects user to /my-bookings
```

The IPN/webhook is the source of truth. Return URL is only for user experience and must not confirm booking by itself.

---

# 8. Review Data Model

## `reviews`

| Column | Type | Constraint | Description |
|---|---|---|---|
| `id` | BIGINT | PK | Review identifier |
| `booking_id` | BIGINT | FK, UNIQUE | Source booking |
| `property_id` | BIGINT | FK | Reviewed property |
| `guest_id` | BIGINT | FK | Review author |
| `rating` | SMALLINT | NOT NULL | Rating from 1 to 5 |
| `comment` | TEXT | NULL | Review content |
| `created_at` | TIMESTAMPTZ | NOT NULL | Creation timestamp |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Last update timestamp |

### Review rule

```text
Booking.status must equal COMPLETED
```

before a review can be created.

```mermaid
flowchart LR
    B[Booking] --> C{Status = COMPLETED?}
    C -->|No| X[Reject Review]
    C -->|Yes| R[Create Review]
    R --> P[Property]
```

### Recommended constraints

```sql
CONSTRAINT chk_review_rating
CHECK (rating BETWEEN 1 AND 5);
```

`booking_id UNIQUE` ensures:

```text
One booking → at most one review
```

---

# 9. Class Diagram

This is a conceptual JPA/domain class diagram.

```mermaid
classDiagram

    class BaseEntity {
        <<abstract>>
        +Long id
        +Instant createdAt
        +Instant updatedAt
    }

    class User {
        +String email
        +String username
        +String passwordHash
        +String fullName
        +String phone
        +UserRole role
        +UserStatus status
        +UserLoginProvider loginProvider
        +String providerId
    }

    class Property {
        +Long hostId
        +String title
        +String description
        +String address
        +String city
        +BigDecimal pricePerNight
        +Integer maxGuests
        +Integer bedrooms
        +Integer beds
        +Integer bathrooms
        +PropertyType propertyType
        +PropertyStatus status
    }

    class PropertyImage {
        +String imageUrl
        +String publicId
        +Integer displayOrder
        +Boolean isCover
    }

    class Amenity {
        +String name
        +String icon
    }

    class Booking {
        +Long propertyId
        +Long guestId
        +LocalDate checkInDate
        +LocalDate checkOutDate
        +Integer guests
        +BigDecimal nightlyPrice
        +BigDecimal cleaningFee
        +BigDecimal serviceFee
        +BigDecimal subtotalPrice
        +Long discountCodeId
        +BigDecimal discountAmount
        +BigDecimal totalPrice
        +BookingStatus status
    }

    class DiscountCode {
        +String code
        +DiscountType type
        +BigDecimal value
        +BigDecimal maxDiscountAmount
        +BigDecimal minBookingAmount
        +Instant startsAt
        +Instant endsAt
        +Integer usageLimit
        +Integer usedCount
        +Integer perUserLimit
        +Boolean active
    }

    class Payment {
        +Long bookingId
        +PaymentMethod paymentMethod
        +PaymentProvider provider
        +PaymentStatus status
        +BigDecimal amount
        +String currency
        +String transactionId
        +String providerTxnRef
        +String providerTransactionNo
        +Instant paidAt
    }

    class Review {
        +Long bookingId
        +Long propertyId
        +Long guestId
        +Integer rating
        +String comment
    }

    class UserRole {
        <<enumeration>>
        GUEST
        HOST
        ADMIN
    }

    class UserLoginProvider {
        <<enumeration>>
        LOCAL
        GOOGLE
    }

    class BookingStatus {
        <<enumeration>>
        PENDING_PAYMENT
        PENDING
        CONFIRMED
        CANCELLED
        REJECTED
        COMPLETED
    }

    class PaymentMethod {
        <<enumeration>>
        MOCK
        VNPAY
        MOMO
    }

    class PaymentProvider {
        <<enumeration>>
        MOCK
        VNPAY
    }

    class PaymentStatus {
        <<enumeration>>
        PENDING
        SUCCESS
        FAILED
        CANCELLED
        EXPIRED
        REFUNDED
    }

    class DiscountType {
        <<enumeration>>
        PERCENT
        FIXED
    }

    BaseEntity <|-- User
    BaseEntity <|-- Property
    BaseEntity <|-- PropertyImage
    BaseEntity <|-- Amenity
    BaseEntity <|-- Booking
    BaseEntity <|-- DiscountCode
    BaseEntity <|-- Payment
    BaseEntity <|-- Review

    User --> UserRole
    User --> UserLoginProvider
    Booking --> BookingStatus
    Payment --> PaymentMethod
    Payment --> PaymentProvider
    Payment --> PaymentStatus
    DiscountCode --> DiscountType

    User "1" --> "0..*" Property : hosts
    User "1" --> "0..*" Booking : guest
    Property "1" --> "0..*" PropertyImage
    Property "1" --> "0..*" Booking
    DiscountCode "1" --> "0..*" Booking : optional
    Booking "1" --> "1" Payment
    Booking "1" --> "0..1" Review
```

---

# 10. Java Entity Relationship Mapping

Suggested mapping:

```text
User
  @OneToMany(mappedBy = "host")
      → properties

Property
  @ManyToOne
      → host

Property
  @OneToMany(mappedBy = "property")
      → images

Property
  @ManyToMany
      → amenities

Booking
  @ManyToOne
      → property

Booking
  @ManyToOne
      → guest

Booking
  @ManyToOne(optional = true)
      → discountCode

Payment
  @OneToOne
      → booking

Review
  @OneToOne
      → booking
```

## Recommended ownership

```mermaid
flowchart TB
    U[User]
    P[Property]
    PI[PropertyImage]
    A[Amenity]
    B[Booking]
    PAY[Payment]
    D[DiscountCode]
    R[Review]

    U -->|host_id| P
    P -->|property_id| PI
    P <-->|property_amenities| A
    P -->|property_id| B
    U -->|guest_id| B
    D -->|discount_code_id| B
    B -->|booking_id| PAY
    B -->|booking_id| R
```

---

# 11. Repository / Relational Table Summary

| Table | Primary Key | Main Foreign Keys |
|---|---|---|
| `users` | `id` | — |
| `properties` | `id` | `host_id → users.id` |
| `property_images` | `id` | `property_id → properties.id` |
| `amenities` | `id` | — |
| `property_amenities` | `(property_id, amenity_id)` | property + amenity |
| `discount_codes` | `id` | — |
| `bookings` | `id` | `property_id`, `guest_id`, optional `discount_code_id` |
| `payments` | `id` | `booking_id` |
| `reviews` | `id` | `booking_id`, `property_id`, `guest_id` |

---

# 12. Full Relational Diagram

```mermaid
flowchart LR

    USERS[(users)]

    PROPERTIES[(properties)]
    IMAGES[(property_images)]
    AMENITIES[(amenities)]
    PA[(property_amenities)]

    BOOKINGS[(bookings)]
    PAYMENTS[(payments)]
    DISCOUNTS[(discount_codes)]
    REVIEWS[(reviews)]

    USERS -->|"1:N host_id"| PROPERTIES
    PROPERTIES -->|"1:N property_id"| IMAGES

    PROPERTIES -->|"1:N"| PA
    AMENITIES -->|"1:N"| PA

    USERS -->|"1:N guest_id"| BOOKINGS
    PROPERTIES -->|"1:N property_id"| BOOKINGS
    DISCOUNTS -->|"1:N optional discount_code_id"| BOOKINGS

    BOOKINGS -->|"1:1 booking_id"| PAYMENTS
    BOOKINGS -->|"1:0..1 booking_id"| REVIEWS

    USERS -->|"1:N guest_id"| REVIEWS
    PROPERTIES -->|"1:N property_id"| REVIEWS
```

---

# 13. Suggested Indexes

## `users`

```sql
CREATE UNIQUE INDEX uk_users_email
ON users(email);
```

## `properties`

```sql
CREATE INDEX idx_properties_host_id
ON properties(host_id);

CREATE INDEX idx_properties_city
ON properties(city);

CREATE INDEX idx_properties_price
ON properties(price_per_night);

CREATE INDEX idx_properties_status
ON properties(status);
```

## `bookings`

Availability checking will be a frequent query.

```sql
CREATE INDEX idx_bookings_property_dates
ON bookings(property_id, check_in_date, check_out_date);

CREATE INDEX idx_bookings_guest_id
ON bookings(guest_id);

CREATE INDEX idx_bookings_status
ON bookings(status);
```

## `discount_codes`

```sql
CREATE UNIQUE INDEX uk_discount_codes_code
ON discount_codes(code);

CREATE INDEX idx_discount_codes_active_window
ON discount_codes(active, starts_at, ends_at);
```

## `payments`

```sql
CREATE UNIQUE INDEX uk_payments_provider_txn_ref
ON payments(provider, provider_txn_ref);
```

## `property_images`

```sql
CREATE INDEX idx_property_images_property_id
ON property_images(property_id);
```

## `reviews`

```sql
CREATE INDEX idx_reviews_property_id
ON reviews(property_id);
```

---

# 14. Search Data Relationships

Search uses property data plus booking data.

```mermaid
flowchart LR

    C[SearchCriteria]
    P[properties]
    B[bookings]
    A[amenities]
    PA[property_amenities]
    R[reviews]

    C -->|city| P
    C -->|guests <= max_guests| P
    C -->|price filter| P
    C -->|property type| P

    C -->|amenity filter| PA
    PA --> A

    C -->|date availability| B
    P --> B

    R -->|rating filter| P
```

### Example search criteria

```text
location = "Da Nang"
checkIn = 2026-09-10
checkOut = 2026-09-13
guests = 2

filters:
- minPrice
- maxPrice
- propertyType
- bedrooms
- amenities
- rating
```

Search should return properties where:

```text
city matches
AND max_guests >= requestedGuests
AND price matches filters
AND no conflicting active booking exists
```

---

# 15. Database Migration Plan

Canonical Flyway migration sequence:

```text
V1__create_users.sql
V2__normalize_user_emails.sql
V3__create_properties.sql
V4__create_property_images.sql
V5__create_amenities.sql
V6__create_property_amenities.sql
V7__create_bookings.sql
V8__create_payments.sql
V9__create_reviews.sql
V10__add_usernames.sql
V11__add_user_login_provider.sql
V12__create_discount_codes.sql
V13__add_booking_discount_and_pending_payment.sql
V14__add_vnpay_payment_fields.sql
```

## Recommended rule

Do not edit an already-applied migration.

Instead:

```text
Wrong:
V3__create_properties.sql
  → modify after other developers have run it
```

Use:

```text
Correct:
V11__add_property_status.sql
```

---

# 16. BaseEntity Design

All main entities can inherit common audit fields.

```java
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
```

Inheritance:

```text
BaseEntity
    │
    ├── User
    ├── Property
    ├── PropertyImage
    ├── Amenity
    ├── Booking
    ├── Payment
    └── Review
```

---

# 17. Important Business Rules

## User

```text
email must be unique
username should be unique for local accounts
role ∈ GUEST, HOST, ADMIN
login_provider ∈ LOCAL, GOOGLE
```

## Property

```text
price_per_night > 0
max_guests > 0
bedrooms >= 0
beds >= 0
bathrooms >= 0
```

## Booking

```text
check_in_date < check_out_date
guests > 0
guests <= property.max_guests
no date overlap with active bookings
PENDING_PAYMENT and CONFIRMED block availability
subtotal_price - discount_amount = total_price
```

## Discount

```text
code must be unique
type ∈ PERCENT, FIXED
value > 0
used_count increments only after verified payment SUCCESS
frontend preview must be revalidated when creating booking/payment
```

## Payment

```text
amount >= 0
one payment record per MVP booking
VNPay IPN/webhook is the source of truth
verify secure hash and amount before marking payment SUCCESS
booking CONFIRMED only after verified payment SUCCESS
```

## Review

```text
1 <= rating <= 5
booking must belong to the reviewing guest
booking.status = COMPLETED
one booking can create at most one review
```

---

# 18. Recommended Package ↔ Table Mapping

```text
com.stayhub.user
    └── users

com.stayhub.property
    ├── properties
    ├── property_images
    ├── amenities
    └── property_amenities

com.stayhub.booking
    └── bookings

com.stayhub.discount
    └── discount_codes

com.stayhub.payment
    └── payments

com.stayhub.review
    └── reviews
```

This keeps the Java code feature-based while maintaining a clear mapping to relational tables.

---

# 19. Future Extensions

These should not necessarily be included in the first MVP schema.

## Wishlist

```text
wishlists
- id
- user_id
- property_id
- created_at
```

Relationship:

```text
User N : M Property
```

## Notification

```text
notifications
- id
- user_id
- type
- title
- content
- is_read
- created_at
```

## Payment extensions

Possible additions:

```text
payment_attempts
payment_webhook_events
refunds
```

## Availability optimization

If the application grows significantly, consider:

```text
property_availability
```

or PostgreSQL range/exclusion constraints to strengthen date-overlap guarantees.

---

# 20. Final MVP Database Summary

```text
                    ┌───────────────┐
                    │     USERS     │
                    └───────┬───────┘
                       HOST │ GUEST
                            │
          ┌─────────────────┴─────────────────┐
          ▼                                   ▼
   ┌───────────────┐                   ┌───────────────┐
   │  PROPERTIES   │◄──────────────────│   BOOKINGS    │
   └───────┬───────┘                   └───┬─────┬─────┘
           │                               │     │
     ┌─────┼─────────┐                     │     ▼
     ▼     ▼         ▼                     │  PAYMENTS
  IMAGES AMENITIES BOOKINGS                │
           │                               ▼
           ▼                         DISCOUNT_CODES
  PROPERTY_AMENITIES                       │
                                           ▼
                                        REVIEWS
```

## MVP tables

```text
1. users
2. properties
3. property_images
4. amenities
5. property_amenities
6. bookings
7. payments
8. reviews
9. discount_codes
```

This design supports the complete MVP flow:

```text
Register/Login
    ↓
Search Property
    ↓
Property Detail
    ↓
Check Availability
    ↓
Apply Discount optional
    ↓
Create Booking PENDING_PAYMENT
    ↓
Create VNPay Payment PENDING
    ↓
VNPay Checkout
    ↓
Verified IPN Payment Success
    ↓
Booking CONFIRMED
    ↓
COMPLETED
    ↓
Review
```
