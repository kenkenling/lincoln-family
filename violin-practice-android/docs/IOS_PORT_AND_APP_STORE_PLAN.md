# LK Practice iOS Port and App Store Plan

Status: planning only
Last reviewed: November 2, 2026

## Goal

Create an iPhone version of LK Practice with the same simple child-first practice workflow as the Android app, distribute it through TestFlight, and then publish it in the Apple App Store.

The Android APK/AAB cannot be converted or submitted directly to Apple. The recommended approach is a separate native SwiftUI application that follows the existing product model while replacing Android-specific services with iOS equivalents.

## Recommended project structure

- Keep the current Android application in `violin-practice-android`.
- Create a sibling project named `violin-practice-ios`.
- Use:
  - App name: `LK Practice`
  - Bundle ID: `com.lkeducation.lkpractice`
  - Initial App Store version: `1.0.0`
- Keep the Android and iOS release histories independent.
- Do not add cloud accounts or synchronization for the first iOS release.
- Re-evaluate shared Kotlin Multiplatform code only after the native iOS MVP works.

## Current starting point

- [ ] Commit and push the pending Android package-name and branding changes.
- [ ] Preserve the current Android behavior as the functional reference.
- [ ] Save screenshots or a short screen recording of every Android workflow.
- [ ] Confirm that Android tests and builds are green before starting the port.
- [ ] Install the full current version of Xcode. This Mac currently has only Apple Command Line Tools.

## Phase 1: Apple account and product decisions

- [ ] Enroll in the Apple Developer Program.
- [ ] Decide whether to enroll as:
  - An organization, if LK Education is a recognized legal entity and should appear as the seller.
  - An individual, understanding that the account holder's legal name normally appears as the seller.
- [ ] If using an organization account, prepare the legal-entity and D-U-N-S information Apple requests.
- [ ] Register `com.lkeducation.lkpractice` as the iOS Bundle ID.
- [ ] Create the LK Practice app record in App Store Connect.
- [ ] Decide the minimum supported iOS version based on the family test devices and Apple's current requirements.
- [ ] Decide the initial category:
  - Recommended first choice: Education.
  - Use the Kids Category only after reviewing its stricter parental-gate, privacy, and third-party-content rules.
- [ ] Decide the YouTube approach:
  - Keep assigned YouTube playback and disclose its third-party network behavior.
  - Restrict external navigation and unrelated YouTube content.
  - Or omit YouTube from the first iOS release and support parent-selected local reference videos only.

Recheck Apple's current submission, SDK, privacy, age-rating, and Kids Category requirements when implementation begins because these requirements change over time.

## Phase 2: Create the iOS application shell

- [ ] Create a SwiftUI iOS app named `LK Practice`.
- [ ] Configure automatic code signing with the Apple Developer account.
- [ ] Add development, TestFlight, and production build configurations.
- [ ] Add the light-blue sky theme, typography, app icon, and launch screen.
- [ ] Create the top-level launch flow:
  1. Privacy and parent consent
  2. Family/profile selection
  3. Student Today page
  4. Parent Space behind the adult gate
- [ ] Add dependency containers/protocols so stores and managers can be replaced with test doubles.

## Phase 3: Port the shared data model

Port the concepts and behavior, not the Android framework code.

- [ ] Port the core models:
  - Household
  - Child profile
  - Assignment and assignment history
  - Daily progress
  - Practice event
  - Practice session
  - Practice mode
  - Mastery status
  - Teacher extension models
  - Typed date keys
- [ ] Preserve stable IDs, revision numbers, timestamps, assignment versions, and recording names.
- [ ] Define one documented portable archive format for future Android/iOS transfer.
- [ ] Add model serialization and round-trip tests.

## Phase 4: Replace Android persistence

| Android implementation | iOS replacement |
| --- | --- |
| Room database | SwiftData, or Core Data if the chosen deployment target requires it |
| SharedPreferences | UserDefaults for simple settings |
| Salted Parent Space PIN hash | Keychain-backed local secret or hash storage |
| Android app-managed files | iOS Application Support/Documents directories |
| Android content URI | File URL plus security-scoped bookmark where persistent access is required |
| Android document picker | `UIDocumentPickerViewController` or SwiftUI file importer/exporter |
| Android share intent | `ShareLink` or `UIActivityViewController` |

- [ ] Create targeted repositories for profiles, assignments, progress, events, and sessions.
- [ ] Make timer updates change only the relevant rows.
- [ ] Port assignment history without overwriting completed-practice context.
- [ ] Port the history-limit warning behavior.
- [ ] Implement delete-all-data for the database, settings, bookmarks, and managed recordings.
- [ ] Add repository and migration tests.

## Phase 5: Port features in this order

### Milestone A: local practice MVP

- [ ] First-launch privacy and parent confirmation.
- [ ] Create, edit, select, and delete child profiles.
- [ ] Parent Space PIN and adult gate.
- [ ] Assignment creation and editing.
- [ ] Today page with greeting, enabled practice modes, and weekly practice dots.
- [ ] Start, pause, resume, and finish a practice session.
- [ ] Repetition tracking.
- [ ] In-session mode switching without resetting time or repetitions.
- [ ] Reflection, student confidence, and completion-versus-mastery display.
- [ ] Summary and practice history.

### Milestone B: media and files

- [ ] Replace CameraX with AVFoundation camera and microphone capture.
- [ ] Preserve the existing descriptive recording filename convention.
- [ ] Save recordings privately inside app-managed storage.
- [ ] View, share, and delete individual recordings.
- [ ] Stop or safely finalize recording when the app becomes inactive.
- [ ] Add local reference-video selection and playback.
- [ ] Add optional sheet-music attachment and viewing.
- [ ] Add camera and microphone permission explanations.
- [ ] Handle denied or unavailable permissions without blocking non-recording practice.

### Milestone C: YouTube and workbook support

- [ ] If retained, implement constrained assigned-video playback with `WKWebView`.
- [ ] Block navigation outside the assigned video experience.
- [ ] Avoid cookies and unnecessary web storage where possible.
- [ ] Update privacy disclosures for any information YouTube or Google receives.
- [ ] Port XLSX creation, reading, merging, duplicate detection, and save verification.
- [ ] Persist file access safely with security-scoped bookmarks.
- [ ] Port CSV import and export.
- [ ] Port profile archive export.
- [ ] Add profile archive import if cross-device transfer is needed for version 1.

## Phase 6: Privacy, children, and content review

- [ ] Rewrite the in-app policy so it is platform-neutral or specifically describes iOS rather than Android.
- [ ] Publish a public privacy-policy page that does not require a family passcode or login.
- [ ] Add the privacy-policy URL to App Store Connect and Parent Space.
- [ ] Complete App Store Privacy answers based on actual application and third-party behavior.
- [ ] Add an iOS privacy manifest and required-reason declarations where applicable.
- [ ] Verify that no analytics, advertising, tracking, or undeclared SDK is included.
- [ ] Keep all child recordings local unless a parent deliberately shares them.
- [ ] Keep external sharing, destructive actions, and settings in Parent Space.
- [ ] If using the Kids Category, implement and test required parental gates before external links or other restricted actions.
- [ ] Use only content the family, teacher, and student have permission to record, attach, play, or publish.
- [ ] Use fictional child names and non-sensitive content in screenshots and review demonstrations.

## Phase 7: Accessibility and quality testing

- [ ] Port the existing accessibility labels and merged task-card descriptions.
- [ ] Verify Dynamic Type at large accessibility sizes.
- [ ] Verify VoiceOver navigation on every screen.
- [ ] Verify sufficient color contrast in light and dark appearance, or intentionally support light appearance only.
- [ ] Respect Reduce Motion.
- [ ] Test large controls with a child on a real iPhone.
- [ ] Test:
  - App relaunch during a running timer
  - Date rollover
  - Incoming calls and backgrounding
  - Recording interruption
  - Low storage
  - Permission denial
  - Missing reference files
  - Workbook moved or deleted
  - Profile deletion
  - Delete all data
  - App upgrade with existing data
- [ ] Add unit tests for models, stores, timer, sessions, assignments, profile isolation, and deletion.
- [ ] Add UI tests for consent, profile setup, practice, Parent Space, and data deletion.

## Phase 8: App Store assets and TestFlight

- [ ] Prepare the 1024-by-1024 App Store icon and required in-app icon sizes.
- [ ] Prepare current-device screenshots using fictional names.
- [ ] Write:
  - App name
  - Subtitle
  - Description
  - Keywords
  - Support URL
  - Privacy-policy URL
  - Review notes
- [ ] Complete:
  - Age-rating questionnaire
  - App Privacy questionnaire
  - Content-rights declaration
  - Export-compliance questionnaire
  - Advertising identifier declaration
- [ ] Explain Parent Space access and any sample PIN to App Review.
- [ ] Explain where reviewers can test recording, deletion, local files, and assigned video.
- [ ] Archive and upload a signed release build from Xcode.
- [ ] Run an internal TestFlight test.
- [ ] Run a small external TestFlight test with family members.
- [ ] Fix crashes, privacy mismatches, accessibility problems, and review blockers.
- [ ] Submit version `1.0.0` for App Review.

## Data portability and future synchronization

The first iOS release should remain local-first. Android and iPhone data will not automatically stay synchronized.

Short-term options:

1. Export a portable child-profile archive from one device and import it on another.
2. Use the workbook for parent-managed assignment transfer, while keeping practice recordings local.

Long-term option:

- Add a separate authenticated web/cloud service only after defining parent ownership, teacher access, child privacy, conflict resolution, encryption, account deletion, and operating costs.

Cloud synchronization is not required for the iOS MVP.

## Definition of iOS MVP complete

- [ ] A parent can accept privacy terms and create a child profile.
- [ ] A parent can create or import an assignment.
- [ ] A child can open the app and immediately start today's practice.
- [ ] Timer, repetitions, enabled modes, mode switching, and reflection work.
- [ ] Completion and mastery remain separate.
- [ ] A child can optionally record a take with camera and microphone permission.
- [ ] A parent can review history, share or delete recordings, and delete all app data.
- [ ] No child data crosses the network except behavior clearly disclosed for an assigned third-party reference video.
- [ ] VoiceOver and large-text testing pass on a real iPhone.
- [ ] Unit and UI tests pass.
- [ ] A signed build installs and works through TestFlight.

## Resume checklist

When this work is picked up again:

1. Read this plan and the Android `FINAL_AUDIT.md`.
2. Check the Android branch and commit status.
3. Re-run the Android tests to freeze the behavioral baseline.
4. Verify current Apple submission requirements.
5. Confirm the Apple Developer account type and seller name.
6. Install or update Xcode.
7. Confirm the Kids Category and YouTube decisions.
8. Create `violin-practice-ios`.
9. Implement Milestone A before media, workbook, or cloud work.
