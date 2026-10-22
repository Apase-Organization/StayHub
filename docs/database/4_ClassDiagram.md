# Domain class projections

## TARGET — conceptual, không phải Java classes đã có

```mermaid
classDiagram
    Property "1" --> "0..*" RoomType : owns
    RoomType "1" --> "0..*" RatePlan : offers
    RoomType "1" --> "0..*" TypeDayInventory : stock
    Property "1" --> "0..*" Booking : receives
    Booking "1" *-- "1..*" BookingItem
    BookingItem "0..*" --> "1" RoomType
    BookingItem "0..*" --> "1" RatePlan
    BookingItem "1" *-- "1..*" RoomAllocation
    RoomAllocation "1" *-- "1..*" NightlySnapshot
    BookingItem "1" --> "0..*" InventoryCommitment
    InventoryCommitment "0..*" --> "1" TypeDayInventory
    Booking "1" --> "0..*" PaymentAttempt
    Booking "1" --> "0..*" DirectReceipt
    RoomAllocation "1" --> "0..*" CheckInEvent
    Booking "1" --> "0..*" LedgerJournal
    LedgerJournal "1" *-- "1..*" LedgerLine
    Booking "1" --> "0..*" Refund
    Booking "1" --> "0..*" Payout
    Booking "1" --> "0..*" DisputeCase
    Booking "1" --> "0..1" Review
    class RoomType {
        +int baseQuantity
        +int maxAdults
        +int maxOccupants
    }
    class Booking {
        +String paymentMode
        +Money dueOnline
        +Money balanceDueHost
        +Snapshot paymentCommissionFundingPolicy
        +long version
    }
    class PaymentAttempt {
        +Money expectedOnlineAmount
        +String uniqueProviderReference
        +String providerVerifiedStatus
    }
    class DirectReceipt {
        +Money amount
        +String methodSourceHostAttested
        +Instant paidAt
        +String actorEvidenceReference
        +long version
    }
    class CheckInEvent {
        +Instant actualAt
        +String acknowledgmentEvidenceDisposition
    }
    class LedgerJournal {
        +String uniqueBusinessEvent
        +String currency
    }
    class LedgerLine {
        +String accountBeneficiary
        +Money debit
        +Money credit
    }
    class RatePlan {
        +String policyVersion
        +Money pricing
    }
    class TypeDayInventory {
        +Date stayDate
        +int total
        +int outOfOrder
        +boolean stopSale
        +long version
    }
    class BookingItem {
        +int quantity
        +Snapshot typePlanPolicy
    }
    class RoomAllocation {
        +int roomOrdinal
        +int adults
        +List childrenAges
    }
    class NightlySnapshot {
        +Date stayDate
        +Money roomCharge
        +Money feesTaxesDiscount
    }
    class InventoryCommitment {
        +Date stayDate
        +int quantity
        +String state
        +Instant expiresAt
    }
```

Associations không hàm ý cascade delete; snapshot phải giữ khi archive. Shared stock và protocol giao dịch theo [ERD](3_CoreERD.md), không gọi từng item confirm độc lập. Service boundary target gồm quote/revalidate, reserveAll, convertAll, releaseAll; financial refund riêng cancellation.

Payment v2 boundary: [Payment rules](../business/PaymentBusinessRules.md) / [data fields](8_Payment.md). Cả full/hybrid; reserve commission không phải earned revenue, direct receipt không provider event. Append reversals và balanced journal; refund/payout proof/maker-checker/private evidence/idempotency/version. Eligibility service đọc actual per-room check-in+24h/dispute/reconcile/legal, không calendar hoặc Host tap. Proposed classes không phải file Java hiện hữu.

## CURRENT/LEGACY conceptual archive

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
