# Core ERD

## V14 actual additive subset — independently accepted TSK-099

TSK-099 adds the seven inventory tables shown below, nullable legacy sale_mode,
HCM timezone, composite FKs, deferred graph-shape checks and DB capacity triggers.
See [exact schema/lock/test/rollback evidence](../tasklist/evidence/TSK-099.md).
Classification maps to existing `properties.property_type` (not a new column).
Money uses an exact NUMERIC domain with whole/range checks to reject fractions
before SQL typmod rounding. Whole-place type identity is conservatively unique
even after archive (same identity can reactivate). Type scope, item selection and
nightly snapshots are immutable; snapshot DELETE-and-replace is forbidden.
Current occupancy limits remain editable: items capture immutable max-adults /
max-occupants rights; new admission checks current limits under type/plan SHARE
locks, while existing holds can convert/release after archive or limit changes.
Capacity primitives exist, but no checkout cutover/backfill is implied.
The property-wide legacy exclusion remains. Financial/evidence records and extra
header fields below are still TARGET, **not** implemented by V14. Independent
review accepted the corrected foundation and rollback design on2026-10-23;
TSK-099 is complete only for this subset. Backfill/cutover remain blocked.

## TARGET — conceptual fields, chưa có schema/migration

Theo [canonical](../business/PropertyInventoryBusinessRules.md); names dưới là đề xuất DB review TSK-099, không map tự động sang entity hiện tại. `type_day_inventory` là tên thống nhất cho type/date inventory (canonical từng gọi `type_date_inventory`).

**S0-109-v1 FROZEN_DESIGN:** [API field authority](../business/contracts/s0-api-v1.json), [policy](../business/contracts/s0-policy-v1.json). Conceptual DB decimal Money below must have integer wholeVND/scale0 constraints in099, no float; DB snake_case maps APIcamelCase. Required immutable policy_version/fixture_version/quote_hash/actor+consent+selection+amount snapshot, quote_expires_at distinct hold_expires_at(originalcreated+900s), max3 ordinal records/deduped credit. Active room7m/D200k/T6.8m/Cgross1.05m/Cnet850k/hybridO1.36m/B5.44m/held510k/fullheld5.95m; no Guestfee, funded expense separate grossdue. Real-money095 blocked, no schema/migration runtime claim.

**Approved domain S0-097-v1 (2026-10-23):** exclusive WHOLE_PLACE/ROOM_BASED, no linked whole+room sale; RoomType pools only truly interchangeable rooms, all RatePlans share type stock. Whole-place has one type quantity1; bedrooms are descriptive. Booking header fixes one property/date range/VND for every item; cancellation releases the whole booking, never selected item/quantity. PhysicalRoom assignment is future scope. These are approved design constraints, not optional schema assumptions; TSK-099 must implement/review actual cross-row protections and TSK-106 backfill must preserve evidence, not fabricate historical sale modes. [Approval log](../business/S0DecisionLog.md).

```mermaid
erDiagram
    PROPERTIES ||--o{ ROOM_TYPES : owns
    ROOM_TYPES ||--o{ RATE_PLANS : offers
    ROOM_TYPES ||--o{ TYPE_DAY_INVENTORY : calendar
    PROPERTIES ||--o{ BOOKINGS : receives
    BOOKINGS ||--|{ BOOKING_ITEMS : contains
    ROOM_TYPES ||--o{ BOOKING_ITEMS : selected
    RATE_PLANS ||--o{ BOOKING_ITEMS : priced
    BOOKING_ITEMS ||--|{ ROOM_ALLOCATIONS : allocates
    ROOM_ALLOCATIONS ||--|{ NIGHTLY_SNAPSHOTS : snapshots
    BOOKING_ITEMS ||--o{ INVENTORY_COMMITMENTS : reserves
    TYPE_DAY_INVENTORY ||--o{ INVENTORY_COMMITMENTS : accounts
    BOOKINGS ||--o{ PAYMENT_ATTEMPTS : attempts
    BOOKINGS ||--o{ DIRECT_RECEIPTS : host_attests
    ROOM_ALLOCATIONS ||--o{ CHECKIN_EVENTS : arrives
    BOOKINGS ||--o{ LEDGER_JOURNALS : accounts
    LEDGER_JOURNALS ||--|{ LEDGER_LINES : balances
    BOOKINGS ||--o{ REFUNDS : refunds
    BOOKINGS ||--o{ PAYOUTS : pays_host
    BOOKINGS ||--o{ DISPUTE_CASES : disputes
    BOOKINGS ||--o| REVIEWS : reviews
    PROPERTIES {
        bigint id PK
        bigint host_id FK
        string classification
        string sale_mode
        string timezone
        string metadata
    }
    ROOM_TYPES {
        bigint id PK
        bigint property_id FK
        string name
        int base_quantity
        int max_adults
        int max_occupants
        json beds_photos_amenities
        string status
    }
    RATE_PLANS {
        bigint id PK
        bigint property_id FK
        bigint room_type_id FK
        string currency
        string policy_version
        json pricing_restrictions
        json allowed_payment_modes
        string payment_policy_version
    }
    TYPE_DAY_INVENTORY {
        bigint room_type_id PK,FK
        date stay_date PK
        int total
        int out_of_order
        boolean stop_sale
        bigint version
    }
    BOOKINGS {
        bigint id PK
        bigint property_id FK
        bigint guest_id FK
        date check_in
        date check_out
        string currency
        string status
        decimal subtotal
        decimal discount
        decimal total
        string payment_mode
        decimal due_online
        decimal balance_due_host
        json payment_commission_funding_snapshot
        bigint version
        json booking_fees_policy_snapshot
        string idempotency_body_hash
    }
    BOOKING_ITEMS {
        bigint id PK
        bigint booking_id FK
        bigint property_id FK
        bigint room_type_id FK
        bigint rate_plan_id FK
        int quantity
        json type_plan_policy_snapshot
    }
    ROOM_ALLOCATIONS {
        bigint item_id PK,FK
        int room_ordinal PK
        int adults
        json children_ages
    }
    NIGHTLY_SNAPSHOTS {
        bigint item_id PK,FK
        int room_ordinal PK,FK
        date stay_date PK
        decimal room_charge
        decimal fees
        decimal taxes
        decimal discount_allocation
        json policy_snapshot
    }
    INVENTORY_COMMITMENTS {
        bigint item_id PK,FK
        date stay_date PK,FK
        bigint room_type_id FK
        int quantity
        string state
        datetime expires_at
    }
    PAYMENT_ATTEMPTS {
        bigint id PK
        bigint booking_id FK
        decimal expected_amount
        string currency
        string provider_txn_ref UK
        string provider_event_key UK
        string status
        json provider_config_snapshot
    }
    DIRECT_RECEIPTS {
        bigint id PK
        bigint booking_id FK
        decimal amount
        string currency_method
        string source_HOST_ATTESTED
        datetime paid_at
        datetime recorded_at
        bigint actor_id FK
        string reference_evidence_handle
        string acknowledgment_dispute_state
        string idempotency_hash
        bigint version
        bigint reversal_of FK
    }
    CHECKIN_EVENTS {
        bigint id PK
        bigint item_id FK
        int room_ordinal FK
        datetime actual_at
        string timezone
        bigint actor_id FK
        string evidence_ack_disposition
        bigint version
    }
    LEDGER_JOURNALS {
        bigint id PK
        bigint booking_id FK
        string business_event_key UK
        string currency
        bigint reversal_of FK
    }
    LEDGER_LINES {
        bigint id PK
        bigint journal_id FK
        string account_beneficiary
        decimal debit
        decimal credit
    }
    REFUNDS {
        bigint id PK
        bigint booking_id FK
        decimal policy_amount
        decimal platform_amount
        decimal host_direct_responsibility
        string status_direction_method
        string proof_reference_key
        bigint maker_id FK
        bigint checker_id FK
        bigint version
    }
    PAYOUTS {
        bigint id PK
        bigint booking_id FK
        json entitlement_boundary_snapshot
        datetime eligible_at
        decimal platform_held_host_amount
        string status_blocked_reasons
        string proof_reference_key
        bigint version
    }
    DISPUTE_CASES {
        bigint id PK
        bigint booking_id FK
        string evidence_scope_owner
        string recovery_status_disposition
        decimal reserved_or_recovery_amount
    }
```

**Payment TARGET:** conceptual fields not exhaustive migrations; [Payment data](8_Payment.md) source/direction/method/timestamps/key/proof/makerchecker/privateevidence. Manyattempts/directmemo not platformcash, events allocation(item,ordinal), balanced journals/uniqueness/immutable reversals, commissionreserve not earned. Refund sourcepolicy/cash/paid/reserved/liquidity locks. ALLARRIVED maxactual+24/allgates and evidence090 frozen, not scheduledtransfer/perroom earlypayout. Bothmodes MVP, no monthlycommission; legacy1:1 below archive.

**Accounting choisi:** commitments là nguồn chuẩn duy nhất, mỗi item/night một row `HELD/CONFIRMED/RELEASED`, không có counters held/confirmed độc lập. Sum active HELD và CONFIRMED dưới khóa day row. Expired hold chỉ được bỏ khỏi sum sau transition RELEASED serialized; trước khi bán phải release expired trong transaction. `blocked = out_of_order`; stop-sale là sales restriction (không xóa commitments), không trừ cả total lần nữa. `remaining = total - out_of_order - held - confirmed`; `availableQuantity = 0` khi stop-sale, ngược lại min remaining toàn stay. Stop-sale chặn bán mới, OOO giảm capacity; cả hai không hủy booking/hold cũ.

**Integrity đề xuất:** UNIQUE(type_id, property_id), UNIQUE(plan_id, type_id, property_id), composite FK item→booking(id,property_id), item→type và plan cùng property/type; commitment→item(id,type_id) và day(type_id,date); snapshot→allocation(item_id,ordinal). Quantity>0, checkIn<checkOut; ordinal count=quantity, dates chính xác mọi đêm. Ràng buộc nhiều row dùng transaction/constraint trigger được DB review, không giả CHECK một row đủ bảo vệ. Monetary precision theo currency; sum nightly + scoped fees − discount = header, snapshot tên và policy không cascade mất khi archive.

**Transaction protocol:** khóa booking/lifecycle row trước (khi đã tồn tại), rồi mọi day row theo `(room_type_id, stay_date)` tăng dần; tạo day rows thiếu bằng unique key trước lock, không coi missing row là unlimited. Capacity update, hold, expiry, IPN, cancel cùng protocol. Create revalidate tất cả selection/caps rồi insert toàn items/commitments hoặc rollback. IPN khóa cùng booking, kiểm expiry và trạng thái, đổi HELD→CONFIRMED trên chính rows trong cùng commit payment, không insert thêm confirmation/decrement stock lần2. Expiry/cancel đổi RELEASED đúng một lần; late paid ghi ledger/refund reconciliation, không hồi sinh. Lock discount caps theo thứ tự toàn cục được review cùng protocol để tránh deadlock. Không có exclusivity theo type toàn booking; nhiều bookings dùng chung pool hợp lệ.

**Rollout:** additive types/plans/day/items/allocation/snapshots; backfill default type quantity1 và plan, giữ IDs, historical unknown có provenance. Không merge theo địa chỉ. Reconcile totals/active future commitments, shadow read/write coordination và capacity protection trước thay exclusion property. Rollback bảo toàn mới phát sinh; chi tiết canonical mục7. Physical rooms không bắt buộc schema này.

**Stop-sale scope:** `TYPE_DAY_INVENTORY.stop_sale` chặn bán mới mọi plan của type/ngày; plan/day stop-sale thuộc `RATE_PLANS.pricing_restrictions`, chỉ chặn plan đó. Offer quantity bằng min remaining toàn stay chỉ khi cả type và plan đủ điều kiện, ngược lại0; remaining không bị reset/trừ bởi stop-sale. Luôn enforce `0 <= out_of_order <= total` và `held + confirmed <= total - out_of_order`, kể cả đóng bán. Không release hoặc ngăn conversion hợp lệ của hold cũ chỉ vì stop-sale mới; mọi plans vẫn dùng cùng pool.

## CURRENT/LEGACY conceptual archive

Các entity/payment/state legacy dưới đây giữ nguyên để đối chiếu, không chứng minh target đã tồn tại.

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
