# Sprint 0 decision and delivery log

## Provenance and status

Approval source: user answered all seven policy questionnaire groups approving the proposals on 2026-10-23. User acts as product owner, not legal counsel or a named Finance/Operations signatory. No signatures or real-money permission are inferred.

Decision approval, completed design deliverable and runtime clearance are separate statuses. Only TSK-079 is delivered here. Other seven questionnaire approvals are preserved as provenance, not completed tasks; their detailed contracts/fixtures will be frozen sequentially.

| Task | Decision approval | Deliverable status / version |
|---|---|---|
| TSK-079 | Approved instant, both required modes, required-online authority and exclusions | Completed policy design, S0-079-v1, 2026-10-23 |
| TSK-097 | Approved inventory boundaries | Pending dedicated deliverable |
| TSK-081 | Approved hold/retry/late-payment parameters | Pending dedicated deliverable |
| TSK-084 | Approved cancellation/refund parameters | Pending dedicated deliverable |
| TSK-086 | Approved financial/operations design direction, not legal clearance | Pending dedicated deliverable |
| TSK-088 | Approved discount/abuse parameters | Pending dedicated deliverable |
| TSK-090 | Approved time/arrival/completion/review parameters | Pending dedicated deliverable |
| TSK-109 | Approved numerical choices, not completed config/DTO freeze | Pending dedicated deliverable |

## TSK-079 acceptance evidence

Canonical [S0-079-v1 matrix](PaymentBusinessRules.md#1a-approved-contract-matrix--s0-079-v1) records mode × required-online/direct balance, state × authorized actor × collection, immutable version/consent expectations, read-only return and exact server attempt O with active-hold guard. No Host accept/reject, monthly commission collection or onsite StayHub QR. Full and hybrid confirmation are equally mandatory; attested direct receipts/refund/payout states are not conflated with provider SUCCESS.

TaskList closes only this policy deliverable; TSK-043/044/071–073 remain historical and require runtime revalidation. Config/earning/legal detail stays with TSK-109/086, lifecycle with TSK-081, refund with TSK-084 and arrival/completion with TSK-090. Earlier numerical fixtures/proposal wording in other sections are not approved config and remain staged for those tasks; no downstream implementation claim.

Validation command: `python3 .opencode/verify-dual-payment-docs.py` (stdlib explicit document manifest; IDs/counts, DAG/ranks, fences/JSON/links and illustrative amount boundaries). PASS on 2026-10-23 in 0.350s: 114 IDs, 72 checked/42 unchecked, active done1/42, S0 retained8 with only079 completed, 21 documents, 11 JSON +1 JSONC blocks, 76 relative links; no production runtime proof. `git diff --check` PASS. Validator incrementally updated for completed S0 history and honest counts; `.opencode` validator is ignored/local, not a tracked deliverable. No dummy RED/GREEN for docs. Integration/e2e/full suites skipped: docs-only scope. Real-money use blocked on legal/contracts, signed human operations evidence, source implementation and TSK-095 release approval.
