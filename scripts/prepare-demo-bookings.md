# Demo reset preparation — execution blocked

`prepare-demo-bookings.sql` is a read-only, rollback-only planning report, not
a cleanup executor. No live invocation has been performed. It reports every
PENDING/PENDING_PAYMENT/CONFIRMED booking; it does not claim those rows are demo
data, paid, financially resolved, or eligible for completion.

Proposed fixture dates are 29–30 October 2026, checkout 11:00 Asia/Ho_Chi_Minh.
The intended evidence marker is `SYNTHETIC DEMO ONLY; NOT CUSTOMER ACKNOWLEDGMENT`.
No arrival or admin verification is inserted by this script.

## Concrete blocker

V25's `inventory_completion_guard` rejects completion before actual server time
reaches checkout and overwrites completed_at with `clock_timestamp()`. Thus the
requested future completion timestamp cannot safely be prepared on 29 October.
No trigger changes or clock manipulation are proposed. ArrivalEligibilityService
also requires per-room verified arrivals and rejects unresolved financial cases.
This report deliberately excludes all mutations rather than fabricate evidence.

## Requirements before a separately reviewed executor

- Attest the entire target database is dedicated demo data; pause booking writers,
  schedulers and outbox dispatchers.
- Produce and verify a restorable full database backup (including bookings,
  inventory holds/commitments/counters, discounts, payments, snapshots, ledger,
  arrivals/events, settlement locks and outboxes). Never blindly restore selected
  rows over subsequent financial activity; restoration requires a quiescent DB.
- Review original rows, unresolved refunds/disputes/reconciliation/recovery and
  existing arrivals; exclude unresolved cases without altering financial history.
- Verify the existing hold/confirmed release protocols and all date dependencies
  before shifting stays. Do not manually subtract inventory counters.
- Establish schema-supported, explicitly marked synthetic admin demo evidence,
  never guest acknowledgments. Keep production review and completion guards.
- Recheck review eligibility on 31 October using actual completion time and the
  existing review window. This report does not guarantee review eligibility.

The SQL command is documented in the script for controller review only. There
is intentionally no execute flag, mutation, backup file generation, provider
call, refund, email, or inventory release in this preparation deliverable.
