# Assigned-branch attendance

Implemented 2026-10-23. Employees can check in and out only with a fresh location
inside their assigned branch's **50-metre radius** and an HR-approved WebAuthn
passkey. Both checks run on the server. No branch coordinates are seeded.

## Setup

1. Run `npm ci`, `npm run db:generate`, and `npm run db:migrate` before starting the
   updated API. The attendance migration is additive; existing records remain.
2. Serve Daksha at a stable HTTPS origin for phone access and set `APP_ORIGIN` to
   that exact origin. The passkey relying-party ID is its hostname. Localhost HTTP
   is suitable for local development only, not access from employees' phones.
   Changing the hostname requires enrolling passkeys for the new hostname.
3. As Admin or HR, open **Attendance → Branches and attendance access**. Add the
   five branch names, cities and actual latitude/longitude coordinates. Radius is
   fixed at 50 metres. Edit or deactivate branches in the same screen.
4. Assign each active employee to one branch. An unassigned employee cannot punch.
5. Each employee signs in on their phone and chooses **Register phone passkey**.
   The new passkey is pending. HR verifies the employee's identity and enrollment
   before approving it. HR cannot approve their own passkey; another HR/Admin
   account must do that. Up to three non-revoked credentials are allowed per employee.
6. For each check-in/out, choose **get location**, then **Verify and check in/out**.
   Location is acquired on demand, with a separate verification button to preserve
   the browser's user-gesture requirement. Cancellation or failure records no punch.

## Enforcement

- Assigned branch only, including check-out. Branch must remain active and the
  assignment must still match when verification completes.
- Reported location accuracy must be 25 metres or better. Distance plus reported
  accuracy must fit within 50 metres, so uncertain readings near the edge fail.
  Location must be at most two minutes old; location permission is mandatory.
- Server-issued challenges expire after two minutes and bind to user, employee,
  company, login session, direction and the submitted location. They are consumed
  once, including on failed verification. The signature's origin, relying-party
  ID and user-verification flag are checked with SimpleWebAuthn.
- Approval/revocation is rechecked when punching. Counter updates, attendance,
  verification evidence and audit entries commit in one serializable transaction.
- The old direction-only punch payload is rejected. The legacy biometric-device
  ingestion endpoint is disabled, including for previously registered devices.
- The existing one-attendance-record-per-company-day model remains; this change
  does not add overnight shifts or multiple daily work sessions.

## What phone verification proves

WebAuthn verifies control of an approved credential and local user verification.
The operating system may offer a PIN in place of fingerprint/Face ID. Passkeys
may sync between devices through the employee's device account. This is not a
biometric-only identity check or a guarantee of one physical phone per employee.
HR approval helps control enrollment; it does not change those platform limits.

Browser location is client-reported and can be spoofed. These controls reduce
casual misuse but cannot guarantee that attendance fraud is impossible. A stronger
physical-device/location guarantee would need a separately designed managed app
or trusted on-site attendance system.

## Records and operations

Daksha stores public keys and verification metadata, never fingerprint templates
or face images. Successful punches retain coordinates, accuracy, calculated
distance, branch, credential, direction and timestamps in `AttendanceProof`.
There is no continuous location tracking and no employee-facing evidence endpoint.
Expired challenges are cleaned up on new challenge issuance. Proof records have
no automatic retention purge yet; define a retention period before broad rollout.

Lost phone: HR revokes its passkey in Attendance. The employee enrolls a replacement
and HR approves it. Location/phone failures have no bypass button. Resolve the
location or enrollment issue rather than recording an unverified punch.

## Validation

- `npm run typecheck`
- `npm test`: location geometry, accuracy, freshness and existing domain/API tests.
- `npm run test:attendance`: builds the API and uses the configured local database
  with temporary synthetic companies, removed in `finally`. Exercises real signed
  WebAuthn registration/assertions, HR approval, tenant/session binding, freshness,
  revocation, concurrent replay, rejected forged signatures and legacy-route closure.
- `npm run build` (or `npm run build -w @daksha/web -- --webpack` where Turbopack's
  worker port is unavailable).

A real phone check over the deployment's HTTPS origin remains necessary to verify
GPS quality at each office and fingerprint/Face ID prompts on supported devices.

Implementation references: [WebAuthn specification](https://www.w3.org/TR/webauthn-2/),
[SimpleWebAuthn server](https://simplewebauthn.dev/docs/packages/server),
[SimpleWebAuthn browser](https://simplewebauthn.dev/docs/packages/browser), and
[Geolocation specification](https://www.w3.org/TR/geolocation/).
