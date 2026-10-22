# Discounts

**TARGET payment v2:** [Payment rules](../business/PaymentBusinessRules.md) yêu cầu discount/funding/commission allocation snapshot; dueOnline +direct balance = discounted total ở cả hai mode. Coverage deposit sau eligible adjustments/refunds phải đủ retained contract commission và liabilities hoặc reject/requote offer; không monthly invoice. Không coi deposit là earned/nonrefundable revenue. Caps consume một lần khi đủ required online +active hold, không chờ direct B; zero-online exception phải duyệt, không bypass hybrid deposit. Refund recompute exact allocated basis, không âm/rounding drift hay hoàn quá platform online pool.

> **LEGACY single-unit formula bên dưới; TARGET chưa triển khai:** [canonical](../business/PropertyInventoryBusinessRules.md) tính eligible base từ mọi item/night + fees/taxes đúng scope, booking fee một lần; snapshot funding/policy. Reserve/consume/release caps atomic với toàn booking; preview không giữ quyền dùng mã. Phân bổ largest remainder với stable ordinal tie-break, tổng allocations đúng header, base0 không chia0/discount quá charge; zero-total và refund/rounding policy cần duyệt (TSK-088/089/103).

Discounts are applied during booking price calculation and must be revalidated on the backend when creating a payment. The frontend discount preview is only a convenience for the user.

| Column | Type | Constraint | Description |
|---|---|---|---|
| `id` | BIGINT | PK | Discount identifier |
| `code` | VARCHAR(50) | NOT NULL, UNIQUE | Public promo code, stored uppercase |
| `name` | VARCHAR(150) | NOT NULL | Display/internal name |
| `type` | VARCHAR(20) | NOT NULL | `PERCENT`, `FIXED` |
| `value` | NUMERIC(12,2) | NOT NULL | Percentage or fixed amount depending on type |
| `max_discount_amount` | NUMERIC(12,2) | NULL | Cap for percent discounts |
| `min_booking_amount` | NUMERIC(12,2) | NULL | Minimum subtotal required |
| `starts_at` | TIMESTAMPTZ | NULL | Valid from |
| `ends_at` | TIMESTAMPTZ | NULL | Valid until |
| `usage_limit` | INT | NULL | Global usage limit |
| `used_count` | INT | NOT NULL | Successful paid usages |
| `per_user_limit` | INT | NULL | Optional per-user cap |
| `active` | BOOLEAN | NOT NULL | Whether code can be used |
| `created_at` | TIMESTAMPTZ | NOT NULL | Creation timestamp |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Last update timestamp |

## Recommended constraints

```sql
CONSTRAINT chk_discount_type
CHECK (type IN ('PERCENT', 'FIXED'));

CONSTRAINT chk_discount_value_positive
CHECK (value > 0);

CONSTRAINT chk_discount_used_count
CHECK (used_count >= 0);
```

## Calculation rules

```text
subtotal_price = nightly_price * nights + cleaning_fee + service_fee

PERCENT:
discount_amount = subtotal_price * value / 100
discount_amount = min(discount_amount, max_discount_amount) when cap exists

FIXED:
discount_amount = min(value, subtotal_price)

total_price = subtotal_price - discount_amount
```

## Usage rules

- A code is valid only when `active = true` and current time is between `starts_at` and `ends_at` if they are set.
- `subtotal_price` must satisfy `min_booking_amount` if present.
- `used_count` is incremented only after payment becomes `SUCCESS` through a verified payment callback.
- If payment fails/cancels/expires, do not increment `used_count`.
- Backend must revalidate the code when creating booking/payment; never trust discount amount from frontend.

## Discount validation flow

```mermaid
flowchart TD
    A[User enters code] --> B[POST /api/v1/discounts/validate]
    B --> C[Backend recalculates subtotal]
    C --> D{Code valid?}
    D -->|No| E[Return validation error]
    D -->|Yes| F[Return discount preview]
    F --> G[User submits booking]
    G --> H[Backend validates discount again]
    H --> I[Create booking/payment with final total]
```
