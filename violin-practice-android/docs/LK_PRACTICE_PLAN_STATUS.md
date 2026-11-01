# LK Practice plan status

Date: November 1, 2026
Current local release: `0.11.0` (`versionCode 16`)

This file maps the ordered tickets in `LK_Practice_Plan.docx` to the verified
family-only Android implementation. “Deferred” means the work requires a
product or architecture decision; it is not represented as an implemented
feature.

| Ticket | Current status | Verified boundary |
| --- | --- | --- |
| 01 Audit current architecture | Complete | Baseline, architecture, migration, and regression notes are documented. |
| 02 Account, Household, ChildProfile, Role | Partial by design | Household and isolated child profiles exist. No online Account or Teacher role exists. |
| 03 Profile chooser | Complete | A child enters a named practice profile in one tap. |
| 04 Protect Adult Space | Complete locally | Parent actions require a salted four-digit device PIN and relock on background/child return. This is not online authentication or legal consent. |
| 05 Remove hardcoded student/test defaults | Complete for live UI | Names and instruments come from profiles; legacy Lincoln/Violin strings remain only for migration compatibility and violin templates. |
| 06 Instrument as data/config | Complete | Violin, piano, and generic templates use one assignment and session engine. |
| 07 Normalize assignments/steps/sessions | Complete | Assignment versions, selected steps, repetition/time goals, sessions, restart handling, and reflections are persisted. |
| 08 Persistence and migration plan | Complete locally | Per-child version-7 snapshots, backups, CSV, and XLSX portability are implemented. Cross-device canonical storage is deferred. |
| 09 Privacy, deletion, consent, media scoping | Partial | Parent export, exact-name profile deletion, scoped media deletion, local-only storage, and retention copy are implemented. Target age, legal consent, privacy policy, and store disclosures remain product/legal work. |
| 10 Student-aware assignment editor | Complete | Parent Space creates, edits, moves, archives, restores, imports, exports, and permanently removes assignments for the selected child. |
| 11 Weekly progress and reflection | Complete | Counts derive from saved records; weekly patterns have no streak penalty or fake skill score. |
| 12 Teacher invite/link/revoke | Deferred | Requires authenticated identity, scoped backend authorization, consent, invitation, and revocation design. |
| 13 Encouraging completion messages | Complete | Copy emphasizes effort, small steps, reflection, and neutral return after skipped days. |
| 14 Offline reconciliation and recovery | Complete locally; cloud portion deferred | Airplane-mode practice, timer persistence, interrupted-session recovery, and workbook error recovery work without cloud sync. Idempotent cloud reconnection cannot be implemented until a sync backend exists. |
| 15 Beta checklist and store assets | In progress | Unit tests, lint, emulator workflows, 360dp/130% text, export/delete, archive/restore, and APK assembly pass. TalkBack, measured contrast, broader devices, store metadata, policy/legal review, demo data, and pilot testing remain. |

## Safe next implementation boundary

The family-only local beta can continue with TalkBack, contrast, device-matrix,
crash/ANR, camera-permission, local-video failure, and airplane-mode scenarios.

Teacher collaboration should begin only after choosing:

1. target age band and guardian-consent approach;
2. Android-only versus a shared Android/web product;
3. account and household ownership model;
4. backend, authorization, invitation, and revocation model;
5. whether recordings stay device-local or can be guardian-approved uploads;
6. deletion, retention, audit, and conflict-resolution rules.
