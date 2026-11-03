# LK Practice iOS Development Checkpoint

Checkpoint date: November 3, 2026
Project status: Milestone A implemented in source; full Xcode, Simulator, and
physical-iPad validation remain pending.

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

### Shared core

- Typed `DateKey`
- Household, child profile, assignment, daily progress, event, session,
  mastery, and teacher-extension models
- Practice engine for profile isolation, assignments, sessions, progress,
  weekly summaries, reflection, and history
- Pacific-time-safe date arithmetic so adding a day does not shift a date
  backward when it is formatted

## Verification completed on this Mac

The following checks passed on November 3, 2026:

- `xcodegen generate`
- Generated Xcode project property-list validation
- Privacy manifest validation
- Asset catalog JSON validation
- SwiftUI source parser check
- Shared core and app-support compilation through Swift Package Manager
- `LKPracticeCoreChecks`, covering date behavior, assignment ranges, session
  modes, repetitions, daily progress isolation, mastery, assignment history,
  profile isolation, serialization, and weekly summaries
- `git diff --check`

The formal `swift test` suite is present, but it cannot run with this Mac's
Command Line Tools alone. Compilation reaches the tests and then stops because
Apple's `TestingMacros` plugin is unavailable without full Xcode. This is an
environment blocker, not a recorded passing test run.

The SwiftUI application has not yet been type-checked by full Xcode, launched
in an iOS Simulator, signed, or installed on the family iPad.

## Current machine blocker

- Active developer directory: `/Library/Developer/CommandLineTools`
- Full Xcode is not installed
- XcodeGen 2.46.0 is installed
- Only about 3.7 GB of disk space was free at this checkpoint, which is not
  enough for full Xcode and an iOS Simulator runtime

## Intentionally deferred

These belong to the next media and file milestones:

- Camera and microphone recording with AVFoundation
- Local and reference-video playback
- YouTube assigned-video playback
- Embedded sheet-music viewer
- Recording file naming, browsing, sharing, and deletion
- CSV/XLSX import and export
- SwiftData or Core Data repositories
- Android-to-iOS archive import
- Cloud synchronization
- App Store signing, TestFlight, screenshots, and submission metadata

## Resume steps

1. Free enough disk space for Xcode, an iOS runtime, build products, and
   Simulator data.
2. Install the current full Xcode from the Mac App Store.
3. Launch Xcode once and install the requested iOS platform components.
4. Select full Xcode:

   ```bash
   sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
   ```

5. Regenerate and open the project:

   ```bash
   cd /Users/yualing/Documents/Lincoln/fun_projects/lincoln-family/violin-practice-ios
   xcodegen generate
   open LKPractice.xcodeproj
   ```

6. Run the first full build and tests:

   ```bash
   xcodebuild \
     -project LKPractice.xcodeproj \
     -scheme LKPractice \
     -configuration Debug \
     -destination 'generic/platform=iOS Simulator' \
     build
   swift test
   ```

7. In Xcode, select an iPad Simulator and manually test:
   - Consent and first profile setup
   - Parent PIN creation, lock, unlock, and recovery behavior
   - Single-day and multi-day assignment creation
   - Timer, pause/resume, repetitions, and mode switching
   - Reflection, completion, mastery, and summary
   - Profile isolation and deletion
   - Delete all data and relaunch
   - Pacific-time date boundaries and date rollover
8. Fix any SwiftUI type-check or runtime issues found by full Xcode.
9. Select the LK Education development team under Signing & Capabilities,
   connect the family iPad, and repeat the workflow on the real device.
10. Begin Milestone B with AVFoundation recording and embedded sheet music.

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
