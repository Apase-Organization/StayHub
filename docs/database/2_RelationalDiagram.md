# Relational projections

**V14 actual additive subset (independently accepted TSK-099):** property/type/plan/day/item/allocation/
nightly/commitment links now have actual migrations ([V14__add_room_type_inventory_foundation.sql](../../src/main/resources/db/migration/V14__add_room_type_inventory_foundation.sql) and [V16__add_inventory_rate_plan_pricing_and_hold_keys.sql](../../src/main/resources/db/migration/V16__add_inventory_rate_plan_pricing_and_hold_keys.sql)) and capacity primitives; [manifest and limitations](../tasklist/evidence/TSK-099.md). Sơ đồ thực thể chi tiết xem tại [3_CoreERD.md](3_CoreERD.md).

## Active Relational Schema (Multi-Room & Dual-Payment)

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


**S0-109-v1 FROZEN_DESIGN** [typed contract](../business/contracts/s0-api-v1.json): draft0children/submit>=1item; ERD/Payment keys/atomicity multipleattempts/uniqueevent/journal/directmemo/perroomactual/balancedledger. Immutable policyVersion/fixtureVersion/quoteHash/snapshots, wholeVND/noGuestfee/platformD/O>=Cnet, zeroT/O rejected/no exception. Original900s/max3, ALLARRIVED+24/allgates and review090 frozen; no migration/runtime/legal claim. Legacy1:1 below archive.

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
