# Sprint 0 decision and delivery log

## Provenance and status

Approval source: user answered all seven policy questionnaire groups approving the proposals on 2026-10-23. User acts as product owner, not legal counsel or a named Finance/Operations signatory. No signatures or real-money permission are inferred.

Decision approval, completed design deliverable and runtime clearance are separate statuses. TSK-079 and TSK-097 are delivered here. Other six questionnaire approvals are preserved as provenance, not completed tasks; their detailed contracts/fixtures will be frozen sequentially.

| Task | Decision approval | Deliverable status / version |
|---|---|---|
| TSK-079 | Approved instant, both required modes, required-online authority and exclusions | Completed policy design, S0-079-v1, 2026-10-23 |
| TSK-097 | Approved inventory boundaries | Completed policy design, S0-097-v1, 2026-10-23 |
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

## TSK-097 acceptance evidence

User PO questionnaire approval frozen on 2026-10-23 as **S0-097-v1** in [canonical Inventory](PropertyInventoryBusinessRules.md), with targeted [Overview](OverviewBusiness.md), [API](../architecture/3_API_Design.md) and [ERD](../database/3_CoreERD.md) projections. Approved boundaries: exclusive WHOLE_PLACE/ROOM_BASED without linked sale; truly interchangeable pooled RoomTypes and shared-plan stock; PhysicalRoom assignment future; whole-place one type quantity1 with descriptive bedrooms; one property/date range/VND per booking; whole-booking cancellation only, not partial items/quantities. Financial partial refund is distinct and remains TSK-084's deliverable.

Only097 newly completed; cumulative policy designs079/097, S0 done2/8, active done2/42, 73 checked/41 unchecked across114 IDs. No schema/source/runtime/capacity or migration correctness claimed. Existing financial fixture replacement remains staged for109, not changed or commercially approved by097. Remaining downstream protections require099/102/106/107 and release095.

Checks: `python3 .opencode/verify-dual-payment-docs.py` PASS (2026-10-23, 0.341s): 114 IDs/73 checked/41 unchecked, policy done2, DAG/ranks/retained S0 history, S0-097-v1 projections, 21 documents/11 JSON/1 JSONC/84 links and illustrative financial boundaries. `git diff --check` PASS. Validator uses an explicit document manifest, no suite-wide runner; ignored local validator incrementally updated. Payment precedence wording also updated to reference the independent inventory approval, without changing financial policy. Runtime/integration/e2e skipped for docs-only scope; no dummy RED/GREEN. No business approval blocker for097; implementation and real-money gates remain blocked independently.
