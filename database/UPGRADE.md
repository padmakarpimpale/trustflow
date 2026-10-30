# MySQL schema management

New installations: use an empty `trustflow` database. Flyway runs V1 and V2; Hibernate validates the result.

Existing H2 data stays in its original file; switching to MySQL does not copy it. The older sample H2 dump is not a MySQL dump.

Existing MySQL installations without flyway_schema_history: startup deliberately refuses to baseline automatically. Export and verify a backup first. Compare the schema with V1, including types and constraints. Only after it matches, explicitly baseline version 1 using Flyway tooling, then run V2. Do not use baseline-on-migrate against an unknown schema. A fresh separate database is the simplest coursework upgrade; keep the original database intact.

V2 leaves policy snapshots NULL on historical cases, because historical policy values cannot reliably be reconstructed. New cases save all policy values and routing rule version. Do not backfill old cases from current policy settings.

Email upgrade V3 is additive and applies automatically to databases already managed by V1/V2. It creates email_delivery and widens outbox payload storage. SMTP is disabled unless explicitly enabled; enabling it does not delete cases.
