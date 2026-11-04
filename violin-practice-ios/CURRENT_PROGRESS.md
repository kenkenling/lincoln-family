# LK Practice iOS Development Checkpoint

Checkpoint date: November 4, 2026
Project status: Milestone B reference media is implemented and builds in the
iPhone Simulator. Physical-iPad and real-file playback validation remain.

## What is implemented

### Project foundation

- Native SwiftUI application for iPhone and iPad
- Bundle ID: `com.lkeducation.lkpractice`
- App version: `1.0.0` (build `1`)
- Minimum deployment target: iOS 17
- XcodeGen source configuration in `project.yml`
- Generated `LKPractice.xcodeproj`
- LK Practice app icon, in-app logo, sky-blue theme, and privacy manifest

### Local practice workflow

- First-launch privacy consent and age-range confirmation
- Multiple isolated student profiles
- Four-digit Parent Space PIN stored with Keychain-backed salted hashing
- Today page with weekly practice dots
- One-to-30-day assignments with independent progress for each date
- Assignment create, edit, archive, restore, and revision history
- Practice timer with pause and resume
- Repetition tracking
- Listen, Play Along, and Play Solo modes
- Free mode switching during a session without resetting time or repetitions
- Self-directed practice on empty or completed days
- Reflection and student-confidence capture
- Separate completion and mastery states
- Parent summary, profile management, privacy information, and delete-all-data
- Versioned local JSON archive in Application Support
- YouTube reference playback in a temporary embedded web session
- Local reference-video import into app-private storage and native playback
- Reference-video start-time selection and assignment preview
- PDF and image sheet-music import, validation, preview, sharing, and zoom
- Attachment availability warnings, replacement, removal, and orphan cleanup

### Shared core

- Typed `DateKey`
- Household, child profile, assignment, daily progress, event, session,
  mastery, and teacher-extension models
- Practice engine for profile isolation, assignments, sessions, progress,
  weekly summaries, reflection, and history
- Pacific-time-safe date arithmetic so adding a day does not shift a date
  backward when it is formatted

## Verification completed on this Mac

The following checks passed on November 4, 2026:

- `xcodegen generate`
- Xcode 27.2 iPhone Simulator build
- Simulator installation and launch
- 18 Swift tests in 4 suites
- Generated Xcode project property-list validation
- Privacy manifest validation
- Asset catalog JSON validation
- Shared core and app-support compilation through Swift Package Manager,
  including the private media store
- YouTube URL parsing and assignment media validation tests
- `git diff --check`

The new media flows still require manual validation with a real YouTube
assignment, a selected MP4/MOV file, a multipage PDF, an image, and the family
iPad.

## Intentionally deferred

These belong to later milestones:

- Camera and microphone recording with AVFoundation
- Recording file naming, browsing, sharing, and deletion
- CSV/XLSX import and export
- SwiftData or Core Data repositories
- Android-to-iOS archive import
- Cloud synchronization
- App Store signing, TestFlight, screenshots, and submission metadata

## Resume steps

1. Regenerate and open the project:

   ```bash
   cd /Users/yualing/Documents/Lincoln/fun_projects/lincoln-family/violin-practice-ios
   xcodegen generate
   open LKPractice.xcodeproj
   ```

2. Run the build and tests:

   ```bash
   xcodebuild \
     -project LKPractice.xcodeproj \
     -scheme LKPractice \
     -configuration Debug \
     -destination 'generic/platform=iOS Simulator' \
     build
   swift test
   ```

3. In Xcode, select an iPhone or iPad Simulator and manually test:
   - Consent and first profile setup
   - Parent PIN creation, lock, unlock, and recovery behavior
   - Single-day and multi-day assignment creation
   - Timer, pause/resume, repetitions, and mode switching
   - Reflection, completion, mastery, and summary
   - Profile isolation and deletion
   - Delete all data and relaunch
   - Pacific-time date boundaries and date rollover
   - YouTube reference playback and external fallback
   - Local MP4/MOV import, preview, and playback
   - PDF and image sheet-music import, zoom, sharing, replacement, and removal
4. Select the LK Education development team under Signing & Capabilities,
   connect the family iPad, and repeat the workflow on the real device.
5. Begin the recording milestone with AVFoundation camera and microphone
   capture.

## Important implementation notes

- `project.yml` is the source of truth for generated project settings. Record
  project-setting changes there before regenerating the Xcode project.
- The current app is local-only. No child practice data is uploaded.
- The local JSON archive is a working iOS persistence foundation, not the final
  SwiftData/Core Data repository design.
- The Android app was not ported line-for-line. The iOS app shares product
  behavior and data concepts while using native SwiftUI architecture.
- Do not claim Simulator, iPad, signing, TestFlight, or App Store readiness
  until those steps have been completed and verified.
