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
- Universal iPhone/iPad project configuration and LK branding

Camera recording, local/reference video playback, embedded sheet music, workbook
import/export, and YouTube are intentionally deferred to the next media milestone.

## Current machine prerequisite

This Mac currently has Apple Command Line Tools but not the full Xcode app.
The shared Swift core can be built now, but an iPad application cannot be signed
or installed until Xcode and the iOS platform are installed.

## Generate and run the Xcode project

1. Install the current full Xcode from the Mac App Store.
2. Launch Xcode once and let it install the iOS platform components.
3. Select full Xcode:

   ```bash
   sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
   ```

4. Install XcodeGen:

   ```bash
   brew install xcodegen
   ```

5. Generate and open the project:

   ```bash
   cd /Users/yualing/Documents/Lincoln/fun_projects/lincoln-family/violin-practice-ios
   xcodegen generate
   open LKPractice.xcodeproj
   ```

6. In Xcode, select the `LKPractice` target, choose the LK Education development
   team under Signing & Capabilities, and keep bundle ID
   `com.lkeducation.lkpractice`.
7. Connect the iPad by cable, trust the Mac, enable Developer Mode if prompted,
   select the iPad as the run destination, and press Run.

## Local verification

The framework-free checks work with Command Line Tools:

```bash
swift run LKPracticeCoreChecks
```

After full Xcode is installed:

```bash
swift test
```

The `project.yml` file is the source of truth for the generated Xcode project.
Do not hand-edit generated project settings unless the change is also recorded
in `project.yml`.
