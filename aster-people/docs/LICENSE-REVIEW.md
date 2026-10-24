# Dependency review

Reviewed 2026-10-23 before installation. Direct package declarations are recorded in direct-dependencies.json. Exact transitive versions are in package-lock.json. Application code remains private and UNLICENSED; dependency licenses remain their authors' licenses.

MIT, MIT-0, ISC, BSD, Apache-2.0, Zlib and BlueOak dependencies permit private use and distribution with their applicable notices and other conditions. Preserve installed licenses and generated THIRD_PARTY_NOTICES.txt. Apache patent/NOTICE provisions remain applicable.

Exceptions specifically identified and accepted for unmodified private deployment:
- Next.js brings Sharp/libvips binaries under LGPL-3.0-or-later. These are separate dynamically loaded library artifacts. Their licenses and corresponding-source/relinking obligations must be fulfilled if distributing binaries; do not claim the whole dependency tree is permissive. No image-optimization feature is used by this application. https://github.com/libvips/libvips/blob/master/COPYING
- Tailwind's Lightning CSS components are MPL-2.0 (file-level copyleft). No modifications are made. Preserve licenses and make covered source available when distributing them. https://www.mozilla.org/en-US/MPL/2.0/FAQ/
- caniuse-lite contains CC-BY-4.0 data; retain attribution and license. It is build tooling data, not application-owned content.
- busboy and streamsearch omit license fields in some lock metadata; their upstream LICENSE files are MIT. https://github.com/mscdex/busboy/blob/master/LICENSE and https://github.com/mscdex/streamsearch/blob/master/LICENSE
- Redis is deliberately pinned to the 7.2 family, whose upstream license is BSD-3-Clause. Do not silently upgrade across licensing changes. https://redis.io/legal/licenses/
- PostgreSQL uses the PostgreSQL License. https://www.postgresql.org/about/licence/
- Node and Debian/Alpine container images include their own components and licenses. Preserve image notices and generate a container SBOM for each immutable production image before distribution. Docker Engine is an external operator prerequisite, not bundled application code.

No unknown-license dependency is knowingly approved. Future dependency updates must repeat review. This technical inventory is not trademark clearance or a guarantee about future distribution packaging; external distribution remains gated on the actual complete artifact's source/notice obligations.

Security: initial audit identified Nodemailer and deepmerge-ts advisories. Nodemailer was upgraded to the reviewed 10.x release. deepmerge-ts is explicitly overridden to reviewed 8.x; Prisma generation/migration tests validate compatibility.

Attendance verification adds @simplewebauthn/server 14.0.3 and @simplewebauthn/browser 14.0.0 (MIT), reviewed 2026-10-23. Installed transitive license declarations and notices are recorded in dependency-inventory.json and THIRD_PARTY_NOTICES.txt. npm reported no known vulnerabilities at installation.
