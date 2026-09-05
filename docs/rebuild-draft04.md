# Draft 0.4 — first usable learning loop

Scope: slices A and B of App重构初版设计与复用审计_20260905_2323.md.
User approved a fresh profile on 2026-09-06 (UTC+8); old v1 progress is not imported.

Implemented curriculum: G00 → short E/F name introduction → P01 → TAB01 → P02 → P03.
P04–P09, mappings, staff, middle/full regions and structure courses are explicitly planned.
The original modules remain source material for later slices and are not navigable in v2.
MainActivity starts only the v2 coordinator. No old/new progress double-writing occurs.

MusicFacts owns standard tuning. AnswerEvaluator distinguishes pitch class, exact pitch,
coordinates, string, fret, set and sequence. Geometry supports the 9–15 inlay tour;
formal pitch courses remain inside 0–12. Demonstration, hint, preview, correction and
independent evidence are distinct. Each position needs 5/6 recent independent successes,
including two answers per direction with each direction's latest answer correct.

Room learning-v2.db owns the profile, tasks, attempts, evidence, node status and sessions.
Every input commits one transaction before updating UI. Task IDs deduplicate submissions.
The snapshot persists partially completed/corrected tasks as well as hint state.
No destructive migration fallback or automatic reset is permitted. Future schema changes
must add an explicit migration and test preservation. Export/restore uses versioned JSON.

Validation: full curriculum simulations with saved-state reloads; semantic answer examples;
hint/preview exclusion; two-position mastery; Room close/reopen, duplicate writes,
transaction rollback/retry, stale-revision rejection and backup validation.
Device layout, audio and actual installation still require runtime verification.

Verified on Android CI run 33979689190: 175 Debug unit tests and 175 Release unit tests
passed. Android API 35 emulator installed build 18, seeded a real Room learning profile,
updated to build 19 using adb install -r, launched the app and verified exact preservation
of profile, attempts, node state, active task and sound preference. These two smoke builds
share a CI test certificate; the delivered Release uses the separately pinned private key.
Database schema 1.json is checked in as the baseline for future non-destructive migrations.
