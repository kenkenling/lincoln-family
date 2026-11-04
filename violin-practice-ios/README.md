# LK Practice for iPhone and iPad

Native SwiftUI port of the Android LK Practice application.

For the exact implementation checkpoint, validation status, known gaps, and
resume commands, see [CURRENT_PROGRESS.md](CURRENT_PROGRESS.md).

## Current milestone

The local-practice foundation is implemented:

- First-launch privacy consent and age-range confirmation
- Local-only profile, assignment, progress, event, and session models
- Multiple isolated student profiles
- Keychain-backed four-digit Parent Space PIN
- iPad-friendly Today dashboard and weekly practice dots
- One-to-30-day assignment ranges with independent daily progress
- Assignment editing, archiving, revision history, and quick date ranges
- Practice timer, pause/resume, repetitions, and free in-session mode switching
- Self-directed practice and gentle reflection
- Completion and mastery displayed separately
- Parent summaries, profile management, privacy policy, and delete-all-data
- Private JSON archive in Application Support
- YouTube and app-managed local reference-video playback
- PDF and image sheet-music import, preview, sharing, and zoomable viewing
- Private attachment copies with validation, size limits, and orphan cleanup
- Universal iPhone/iPad project configuration and LK branding

Camera recording, workbook import/export, and cloud synchronization remain
deferred to later milestones.

## Current development environment

This Mac has Xcode 27.2, the iOS 27.2 SDK and Simulator runtime, Swift 6.4.2,
and XcodeGen 2.46.0. The app builds and runs in the iPhone Simulator.

## Generate and run the Xcode project

1. Select the installed full Xcode:

   ```bash
   sudo xcode-select -s /Applications/Xcode-27.2.0.app/Contents/Developer
   ```

2. Install XcodeGen if needed:

   ```bash
   brew install xcodegen
   ```

3. Generate and open the project:

   ```bash
   cd /Users/yualing/Documents/Lincoln/fun_projects/lincoln-family/violin-practice-ios
   xcodegen generate
   open LKPractice.xcodeproj
   ```

4. In Xcode, select the `LKPractice` target, choose the LK Education development
   team under Signing & Capabilities, and keep bundle ID
   `com.lkeducation.lkpractice`.
5. Connect the iPad by cable, trust the Mac, enable Developer Mode if prompted,
   select the iPad as the run destination, and press Run.

## Local verification

Run:

```bash
swift test
xcodebuild \
  -project LKPractice.xcodeproj \
  -scheme LKPractice \
  -configuration Debug \
  -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO \
  build
```

The `project.yml` file is the source of truth for the generated Xcode project.
Do not hand-edit generated project settings unless the change is also recorded
in `project.yml`.
