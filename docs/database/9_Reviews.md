# Reviews

**S0-109-v1 FROZEN_DESIGN:** [typed API](../business/contracts/s0-api-v1.json) ReviewRequest/ERR_REVIEW_DUPLICATE/ERR_REVIEW_WINDOW_CLOSED/ERR_REVIEW_INELIGIBLE, all409; owner404/session401/role-CSRF403.090 policy below frozen,092runtimepending, not109TBD. [Fixtures](../business/contracts/s0-fixtures-v1.json) compute exclusive14day/no-show/duplicate boundaries, not runtimeproof.

**TARGET payment/check-in v2:** [Payment rules](../business/PaymentBusinessRules.md) tách actual per-room check-in evidence, guarded completion/no-show và financial collection. Host-attested receipt/online SUCCESS/calendar date không tự mở review hay payout; +24h eligibility dựa actual arrival boundary đã duyệt. Một review/eligible booking cho cả full/hybrid, không nhân theo rooms/receipts.

> **TARGET delta chưa triển khai:** [canonical](../business/PropertyInventoryBusinessRules.md) giữ một review/completed booking, aggregate property bất kể quantity/items, optional booked-type context snapshot; không sinh review mỗi phòng. Guard actor/ngày/window theo TSK-090/092, review hai chiều chỉ optional TSK-096. Bảng dưới là baseline, không bằng chứng runtime guard.


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

### Approved S0-090-v1 review rule (not implemented)

Canonical [Inventory3a](../business/PropertyInventoryBusinessRules.md): authenticated booking actual-stay Guest only; COMPLETED requires scheduled checkout date11:00 Asia/Ho_Chi_Minh boundary reached plus actual-stay evidence/no unresolved incident (early checkout cannot complete early). Require `completedAt <= now < completedAt + 14days`, exact end exclusive; one review per booking across all rooms/modes. No-show/calendar/payment alone grants no eligibility. Unique booking constraint plus transaction guard/idempotency handles concurrent duplicate attempts; same-key replay same review, distinct second create409 duplicate, closed window409, nonactual/incomplete409 ineligible; ownership404/session401/role-CSRF403. Retain immutable completedAt; repeated completion cannot reset window. Baseline status-only diagram below is incomplete legacy, not target authorization or runtime evidence. Moderation/two-way096 scope not expanded.

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
