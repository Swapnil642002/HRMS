# HR workflow reference for Daksha

Reviewed on 2026-10-23 from the local Frappe HR checkout at commit
`b1adf8ee82e854f32743800e79701123d6bcd2fa`.
Upstream: https://github.com/frappe/hrms/tree/b1adf8ee82e854f32743800e79701123d6bcd2fa

The checkout was clean, including untracked and ignored files, before removal.
No references to Frappe or the sibling checkout were found in Daksha's searched
application/configuration files. This review preserves workflow ideas and a
future implementation backlog; it does not establish whether earlier work used
the reference. No Frappe source code or assets were copied during this review.

## Priorities and current gaps

| Priority | Area | Current Daksha foundation | Useful next step |
| --- | --- | --- | --- |
| 1 | Leave | Annual employee allowance, date overlap checks, pending/approved requests counted against allowance | Introduce leave types, allocation periods and a balance ledger; handle holidays, half days, carry-forward, expiry and cancellation reversals explicitly. |
| 1 | Payroll | Basic/allowance inputs, effective-dated policies, draft/finalized runs, separate finalizer and payslip PDFs | Model payable days, joining/leaving dates and unpaid leave before automating prorated salaries; preserve the exact inputs used for each finalized run. Current calculation uses monthly amounts and the run route excludes mid-month joiners. |
| 1 | Attendance | One daily attendance record per employee, check-in/out, device event deduplication | Keep raw punches separately from calculated attendance; add shift assignments, overnight shifts, holidays, missing-punch corrections and late/early thresholds. |
| 2 | Expenses and advances | Generic requests, approval decisions, approved/paid status and travel-linked expense summaries | Add claim line items, claimed versus sanctioned amounts, advance allocations, partial settlement and outstanding balances. Approval and payment should have distinct histories. |
| 2 | Onboarding | Per-employee tasks with due dates and completion timestamps | Add reusable templates, responsible owners, mandatory prerequisites and aggregate progress; connect accepted offers to onboarding without creating duplicate employees. |
| 3 | Performance | One review per employee/cycle with goals, feedback and rating | Add structured weighted goals, separate self-assessment and reviewer feedback, cycle dates and explicit completion rules. |
| 3 | Employee self-service | Existing web application and employee-scoped records | Keep check-in, leave balance, pending requests and payslips easy to reach; show status, next approver and decision history together. |

## Behavioral lessons and future acceptance cases

- Leave: validate eligibility, allocation boundaries, available balance and overlapping requests before approval. Approval/cancellation should reconcile the leave ledger and attendance consistently. Cover half-day overlaps, allocation expiry, cross-year requests and dates already processed in payroll.
- Payroll: define the working-day and holiday policy explicitly. Cover joining/leaving during a period, half-day holidays, unpaid leave and reproducibility after employee salary changes. These are product requirements, not a claim of statutory compliance.
- Attendance: calculate attendance by employee and shift start, including shifts crossing midnight. Wait for the relevant device synchronization window before declaring absence. Cover duplicate events, missing checkout, overlapping shifts and delayed synchronization.
- Expenses: validate employee/company ownership and approver authority. Limit advance allocation to available amounts and ensure cancellation restores balances. Cover partial payments, fully advance-funded claims and duplicate settlement attempts.
- Onboarding: prevent duplicate onboarding for the same offer and enforce required tasks before employee creation where that workflow is enabled. Track task ownership and overdue work.
- Reviews: prevent duplicate or overlapping review periods and validate goal weight totals before computing a final score.

These are proposed improvements, not features implemented by this cleanup.
Implement each with its own schema change, API behavior, UI and focused tests.

## Source map for future reference

All paths below are relative to the pinned upstream repository above, so the
reference can be recovered without keeping the checkout locally.

- `hrms/hr/doctype/leave_application/leave_application.py`: validation, approval/cancellation, leave ledger and attendance reconciliation.
- `hrms/hr/doctype/leave_application/test_leave_application.py`: overlap, half-day, allocation-expiry and attendance scenarios.
- `hrms/hr/doctype/shift_type/shift_type.py` and `test_shift_type.py`: shift configuration, attendance processing, overnight and duplicate attendance scenarios.
- `hrms/hr/doctype/expense_claim/expense_claim.py`: employee scope, sanctioned amounts, advances and payment status.
- `hrms/hr/doctype/employee_onboarding/employee_onboarding.py`: offer uniqueness, mandatory tasks and onboarding completion.
- `hrms/hr/doctype/appraisal/appraisal.py`: cycle uniqueness, weighted goals and feedback scores.
- `hrms/payroll/doctype/salary_slip/salary_slip.py`: payable days, joining/leaving dates, holidays and attendance-based calculations.
- `frontend/src/views/`: self-service screen organization for attendance, leave, advances, expenses and payslips.

Daksha comparison points: `prisma/schema.prisma`,
`apps/api/src/routes.ts` and `apps/api/src/payroll.ts`.
