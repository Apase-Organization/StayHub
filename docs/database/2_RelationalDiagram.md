# Relational projections

## TARGET — proposed, chưa migration

```mermaid
flowchart LR
    U[users] -->|host 1:N| P[properties]
    P -->|1:N| T[room_types]
    T -->|1:N shared stock| R[rate_plans]
    T -->|1:N| D[type_day_inventory]
    P -->|1:N| B[bookings]
    U -->|guest 1:N| B
    B -->|1:N| I[booking_items]
    T -->|1:N| I
    R -->|1:N| I
    I -->|1:N| A[room_allocations]
    A -->|1:N| S[nightly_snapshots]
    I -->|1:N| C[inventory_commitments]
    D -->|1:N| C
    B -->|1:N| Pay[payment_attempts]
    B -->|1:N| DR[direct_receipts]
    A -->|1:N actual evidence| CI[checkin_events]
    B -->|1:N| LJ[ledger_journals]
    LJ -->|1:N balanced| LL[ledger_lines]
    B -->|1:N| RF[refunds]
    B -->|1:N| PO[payouts]
    B -->|1:N| DC[dispute_cases]
    B -->|1:0..1| Rev[reviews]
```

Cardinalities cho draft có thể 0 children; submit phải >=1 item. [CoreERD](3_CoreERD.md) và [Payment v2](8_Payment.md) giữ keys/atomicity: nhiều online attempts, unique event/ref/journal, versioned Host-attested direct receipts/reversals, per-room actual check-in, balanced ledger/refund/payout/disputes. Booking snapshot mode/required online/direct split/policy/funding; zero-online exception phải duyệt, không bypass hybrid deposit. Both modes core MVP; legacy1:1 bên dưới không còn là target.

## CURRENT/LEGACY conceptual archive — không phải target

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
