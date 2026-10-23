# Payment

## V15 internal primitives — independently accepted foundation

Independent re-review approved the corrected internal foundation and rollback
design with no findings after the foreign-XID fix. Acceptance is limited to the
primitives and evidence below; runtime composition and real-money release remain
pending/blocked. No checkout, provider verification or transfer permission implied.

TSK-110 adds `V15__add_payment_ledger_primitives.sql` and internal Spring JDBC
`payment.ledger.PaymentPrimitivesService` / `LedgerService`; [scope and actual test
evidence](../tasklist/evidence/TSK-110.md). This is additive foundation, not a
checkout/IPN cutover or public financial mutator. V14 and legacy `payments`
(`UNIQUE booking_id`, mutable `NUMERIC(12,2)`) remain untouched and isolated.
`BookingMapper` still projects legacy FULL_PREPAY; it does not read this ledger.

Actual new tables: `booking_financial_snapshots`, `payment_settlement_locks`,
`payment_primitive_keys`, `payment_attempts`, `payment_attempt_outcomes`,
`payment_collections`, `ledger_journals`, `ledger_lines`, `ledger_reservations`,
`ledger_reserve_movements`. `ledger_vnd` is unconstrained NUMERIC plus explicit
whole/nonnegative/range checks, **not a scale-0 typmod that rounds fractions**.
The snapshot freezes original booking/guest/actor, versions/hash format, mode and
approved20%/15%/platform-funded amounts, with original createdAt+900s deadline.
No historical snapshots/backfill, quote authentication, fees or taxes invented.

Attempts allocate1..3 under booking lock before external work, binding O/currency/
merchant/config/ref/deadline. Creation failure still consumes its ordinal. Only
creation outcomes exist here; provider callback outcomes and booking state belong082.
Trusted verified input may record ACCEPTED or RECONCILE; only one accepted exact O
before deadline is credited. Caller082 must supply the active-hold/lifecycle guard
and verified provider evidence in the same transaction. Late/extra/wrong-O receipts
remain reconciliation records, not accepted collections or automatic refunds.

POSTED headers/lines reject UPDATE/DELETE; new lines after the construction
transaction reject. Deferred DB checks require at least2 positive-one-side lines,
balanced wholeVND, scoped booking/currency, exact collection funding, and inverse
reversal accounts. Memo journals and their reversal chains cannot use cash or any
platform account. Journal event conflicts compare server-computed semantic hashes,
including actor and booking. Keys scope actor/operation/booking/key; JSON semantic
hashes normalize whole amounts and sort nested fields. The supplied quote hash is
validated for format only, not represented as server-authenticated consent.

Reserve availability comes from accepted online collections or separately typed
Host-attested memo collections, never arbitrary cash journals or uncollected B.
Booking→settlement lock/version protects sums, also forcing stale-snapshot writers
to abort at stronger isolation. Reserves and full RELEASE/CONSUME adjustments are
append-only; payout reserves are additionally capped by platform-held entitlement
and exclude Host direct. Trusted future085/112 callers supply cumulative reviewed
source policy/eligible bound, current liquidity bound and evidence digest under the
same lock/version. Those inputs are not authorization tokens and this primitive
does not evaluate arrival/dispute/legal eligibility or prove bank liquidity.
CONSUME is accounting only, not a transfer/PAID transition. Journal reversals do
not change collection provenance, receipt acknowledgments or bank cash;111/085
must compose source corrections and responsibility decisions, not infer them from
a reversed accounting memo. No earnings are posted automatically.

## TARGET v2 — both modes required; runtime composition pending

**S0-109-v1 FROZEN_DESIGN** [typed API](../business/contracts/s0-api-v1.json)/[policy](../business/contracts/s0-policy-v1.json)/[fixtures](../business/contracts/s0-fixtures-v1.json). API camelCase and DB snake_case are mappings, not competing DTO aliases; Money whole integer VND/SQL scale0 (conceptual columns below,099 reviews actual migration). policy_version/fixture_version/quote_hash and immutable consent/actor/amount/selection/expiry snapshot required. hold_expires_at=originalcreated+900s, max3creations; quote_expires_at distinct explicit server instant/no stock. No Guestfee, platformD<=Cgross, coverageO>=Cnet.095 legal/tax/calendar/providercontract/signedops/liquidity/runtime still BLOCKED; JSON design not environment activation.

[Payment canonical](../business/PaymentBusinessRules.md) và [ERD](3_CoreERD.md) có ưu tiên hơn legacy bên dưới. Booking-level settlement có **nhiều attempts**, không giữ UNIQUE booking_id trên attempts. Mode/policy/config/base/rate/rounding/funding/commission snapshots bất biến; full O=T/B0, hybrid required deposit O +direct Host balance B=T. Không dùng booking total để verify gateway hybrid.

| Proposed record | Fields / integrity / source |
|---|---|
| Booking financial snapshot | payment_mode, payment_policy_version, allowed_modes_snapshot, deposit_base/rate/rounding, due_online, balance_due_host, commission_basis/due/earning_rule, funding/fee/tax beneficiaries, currency, version; sums exact, no double legacy service fee |
| PaymentAttempt | id, booking_id, expected_amount (required online), currency, provider/merchant/config snapshot, unique provider_txn_ref, provider_transaction_no/event dedupe, status/received_at, idempotency body hash; many attempts, serialize amount credit; success may be late/extra reconciliation not booking confirmation |
| Collection | booking/attempt nullable ref, direction IN, method VNPAY/CASH/BANK_TRANSFER, amount/currency, source PROVIDER_VERIFIED or HOST_ATTESTED, occurred/recorded_at/actor/version; direct belongs Host, **not platform cash** |
| DirectReceipt + reversal | booking, method CASH/BANK_TRANSFER, amount/currency/paid_at/recorded_at/actor/reference/private evidence handle, acknowledgment/dispute state, idempotency hash/version, original receipt reversal FK; append audit, partial/refused/disputed outstanding tracked |
| CheckInEvent | booking_item_id +room_ordinal FK allocation, actual_at/timezone, actor/evidence handle, guest acknowledgment, contested/admin disposition, version/key; no dependency on PhysicalRoom, no scheduled-date proof |
| LedgerJournal / LedgerLine | journal unique business-event key/reversal_of, booking/currency/policy version; lines account/beneficiary/debit/credit/amount; sum debit=credit **per currency**, immutable; source links collection/refund/payout/funding, private |
| Refund | requested/approved/processing/paid/failed; policy amount vs platform share vs Host direct responsibility; direction OUT, source collection/reservation, amount/currency, maker/checker/reference/proof/timestamps/key/version; reserve pool atomically, no paid from booking CANCELLED alone |
| Payout | entitlement allocation/boundary snapshot, earliest actual check-in+24h, BLOCKED/ELIGIBLE/APPROVED/PROCESSING/PAID/FAILED, holds/dispute/refund/reconcile/legal reasons; manual proof/maker/checker/key/version; amount only platform-held Host entitlement, excludes direct B |
| Dispute / RecoveryCase | room/receipt/entitlement scope, evidence, owner, reserve/clawback/manual recovery, before/after payout, status/disposition; no monthly commission invoice |

Account separation: online cash clearing, commission reserve (not earned), commission revenue when earned, Host payable held, refund reserve/payable, provider costs, tax/fee liabilities and campaign funding expense/credit. Direct receipt memorandum subledger never debits platform cash. Typical illustrative T1m/C150k: full Dr online clearing1m /Cr commission reserve150k /Cr Host payable850k; hybrid Dr online clearing200k /Cr commission reserve150k /Cr Host payable50k; direct800k memo-only. Earn commission via separate reserve→revenue journal when earning rule permits. Reserve is not new revenue. Actual payout reduces payable/cash with proof; reversal/refund uses original allocation, never deletes entries. Fees/costs/funding require separate named balanced lines, not an unexplained balancing plug.

Lock protocol extends booking/lifecycle row → inventory day order → approved global discount/ledger account order; every writer must adopt same reviewed order. Under lock re-evaluate dueOnline outstanding, attempt credit uniqueness, coverage/allocations, refundable remaining/reserves, eligible payable and dispute/legal/liquidity gates. Unique event/journal keys + transactions prevent double credit/refund/payout. Platform `newRefund <= onlineVerified - paidRefunds - reservedRefunds` and policy remaining, with available liquidity check after payout; Host direct responsibility never increases that pool. Adjustments preserve exact header/item/room/night sums and nonnegative entitlements or open deficit recovery case.

Active s0-fixtures-v1: room7.000.000/clean0/Guestfee0/platformD200.000 → T6.800.000/Cgross1.050.000/Cnet850.000. FullO6.800.000/B0/held5.950.000; hybridO1.360.000/B5.440.000/held510.000, Host5.950.000 both. Separate grossdue1.050.000/fundedexpense200.000/retained850.000, not earned by IPN. Record grossdue/fundedexpense with balanced journal lines, not netretention pretending gross revenue. Tax/providerfixture0 not approval actual amount. ALLARRIVED maxverifiedactual+24h/allgates frozen090/086, no Hosttap or per-room early payout. Backfill only evidenced legacy mode/provenance, unknown not invented; no schema/source change.

## CURRENT/LEGACY archive — not target

> **LEGACY schema/flow proposal; chưa chứng minh implemented canonical.** [TARGET](../business/PropertyInventoryBusinessRules.md): payment tổng booking, không per-item; IPN verified + active all-item holds mới atomic confirm theo flow được PO duyệt. Return/poll read-only; expiry/late paid/duplicates có reconcile/refund riêng. Exact item allocations cộng đúng header, zero-total không gửi gateway sai amount. Refund state độc lập cancellation; funding/payout timing/rates TBD, không mặc định escrow.


| Column | Type | Constraint | Description |
|---|---|---|---|
| `id` | BIGINT | PK | Payment identifier |
| `booking_id` | BIGINT | FK, UNIQUE | Related booking |
| `payment_method` | VARCHAR(20) | NOT NULL | `MOCK`, `VNPAY`, `MOMO` |
| `provider` | VARCHAR(20) | NOT NULL | Payment provider, initially `MOCK` or `VNPAY` |
| `status` | VARCHAR(20) | NOT NULL | Payment state |
| `amount` | NUMERIC(12,2) | NOT NULL | Payment amount |
| `currency` | VARCHAR(3) | NOT NULL | `VND` |
| `transaction_id` | VARCHAR(255) | UNIQUE | Legacy/internal transaction reference |
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
3. Calculate subtotal
4. Validate discount again on backend
5. Create booking with PENDING_PAYMENT
6. Create payment with PENDING and provider_txn_ref
7. Redirect to VNPay checkout URL
8. VNPay calls IPN/webhook
9. Verify secure hash and amount
10. If payment SUCCESS:
      payment = SUCCESS
      booking = CONFIRMED
      increment discount usage if applicable
11. Result page polls status and redirects user to /my-bookings
```

## VNPay verification rules

- Treat IPN/webhook as the source of truth; return URL is for user experience only.
- Always verify `vnp_SecureHash` using `VNPAY_HASH_SECRET`.
- Always compare `vnp_Amount / 100` against `payments.amount`.
- Updates must be idempotent because VNPay can retry IPN.
- Never log `VNPAY_HASH_SECRET`.
- Do not mark booking confirmed from frontend-only success URL.
