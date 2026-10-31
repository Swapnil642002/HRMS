# Step 2 follow-up validation

Multer 2.4.0 -> 2.5.0 through an explicit npm override. Nest remains 11.2.7;
its transitive vulnerable Multer is replaced. Source:
https://github.com/expressjs/multer/security/advisories/GHSA-q7qp-rjg7-q26m

Local command output (2026-10-31):

```text
npm ls multer
@nestjs/platform-express@11.2.7
└── multer@2.5.0 overridden

npm run test:uploads
✔ patched multipart parser accepts bytes and rejects oversized, invalid and aborted uploads
✔ actual HRMS upload URL route signs tenant-scoped S3 uploads and enforces validation/authentication
ℹ tests 2
ℹ pass 2
ℹ fail 0

npm run test:security
ℹ tests 5
ℹ pass 5
ℹ fail 0

npm test
ℹ tests 12
ℹ pass 12
ℹ fail 0

npm run typecheck
passed

npm audit
found 0 vulnerabilities
```

Multipart tests use a loopback test harness with the exact Multer version resolved
by Nest. They cover successful byte upload, size/type rejection, malformed input,
and connection teardown while asynchronous fileFilter is pending. The HRMS uses
presigned S3 PUTs; its actual HTTP route is tested with database doubles and
synthetic signing credentials. No S3 network request or production data is used.
A real staging bucket PUT/download still needs staging credentials and remains a
release smoke test. No payroll/attendance/leave/authorization rules changed.
