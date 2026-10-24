# Reporting-manager approvals

HR or Admin opens **Requests & Approvals → Assign reporting managers**, chooses
an active employee and an active Manager, HR or Admin account in the same company,
and saves the assignment. Employees cannot select or change their approver.
The manager must have a linked company account; a name in the employee directory
alone is not sufficient. No existing employees have been assigned automatically.

All five request types go to the assigned reporting manager first. Only that
person can approve or reject stage one, including when an Admin views the request.
An employee cannot be their own manager or approve their own request.

After manager approval, leave requests go to HR; travel, expenses, advances and
loans go to Finance. The existing Admin authority at this final stage remains.
The same person cannot approve both stages. Rejection ends the workflow.
Only approved expenses, advances and loans can be marked paid.

Managers see their assigned requests and their own employee requests. HR, Finance
and Admin retain company-wide visibility. The screen uses the API's eligibility
result to display Review buttons and shows the assigned manager's name at stage one.

Changing an employee's manager redirects requests still awaiting stage-one
approval, including older requests with no assigned manager. Requests already
reviewed by a manager retain their original manager and decision history.
Reassignment is audited. An inactive manager blocks new submissions and approvals
until HR assigns another eligible manager.

An employee without a manager receives an explicit instruction to contact HR.
An account without an employee profile can still review requests if authorized,
but cannot submit personal requests. HR/Admin may submit for active employees.

Apply migration `20261023030000_reporting_managers`, generate Prisma Client and
restart the API. The local development database has been migrated during this change.

Validation: `npm run test:requests` exercises all five request types, visibility,
permissions, both approval stages, payments, reassignment, legacy pending requests,
inactive managers and self-approval against local PostgreSQL. Test records use
synthetic companies and are removed afterward. Run `npm test` and
`npm run typecheck` for the remaining checks.
