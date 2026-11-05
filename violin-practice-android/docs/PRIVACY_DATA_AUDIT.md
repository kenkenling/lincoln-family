# LK Practice privacy and data audit

Audit date: November 5, 2026

Publisher: LK Education
Application ID: `com.lkeducation.lkpractice`

## Launch and consent

- `MainActivity` checks the current privacy-policy acceptance before accessing the
  lazy `PracticeViewModel`.
- `onStart()` also checks consent, and `onStop()` only touches the ViewModel when
  it has already been initialized.
- A first launch therefore shows the parent consent screen before
  `FamilyStore.loadHousehold()` creates a profile, identifiers, or practice
  snapshots.
- The consent record contains only policy version, age range, parent/adult
  acknowledgement, and acceptance time. No exact birth date is requested.

## Data map

| Data | Storage or destination | Trigger |
| --- | --- | --- |
| Profiles, assignments, progress, sessions, reflections | Private Room database | Parent setup and normal practice |
| Parent PIN hash, file revisions, and migration flags | Private SharedPreferences | Parent setup and local file saves |
| Practice videos | App-managed internal or app-specific external storage | Student or parent records a take |
| Attached sheet music | App-managed private internal storage under `files/Sheet Music/` | Parent selects a supported PDF or image |
| Workbook URI and filename | Private Room database | Parent selects or creates a local workbook |
| Excel workbook content | Parent-selected document provider | Parent explicitly saves or loads |
| Exported ZIP/CSV/video | Parent-selected destination or Android Share target | Parent explicitly exports or shares |
| YouTube network data | YouTube/Google embedded player provided by `android-youtube-player` | Assigned YouTube reference player is opened or used |

## Manifest and dependency audit

- Permissions are limited to `INTERNET`, `CAMERA`, and `RECORD_AUDIO`.
- Camera and microphone permissions are requested at runtime only from the
  recording UI.
- `INTERNET` is required only for assigned YouTube references. The app uses
  `android-youtube-player` core `13.0.0`, which owns its internal WebView and
  packaged iframe integration. The exact packaged player HTML loads
  `https://www.youtube.com/iframe_api`; the app therefore does not claim a
  `youtube-nocookie.com` host or app-controlled cookie suppression.
- LK Practice configures the player with autoplay off and exposes it only for an
  assigned reference. The privacy policy discloses that opening or using the
  player may send network, device, IP-address, and playback information to
  YouTube or Google. Cleartext HTTP traffic remains blocked by the manifest.
- No location, contacts, phone, SMS, broad photo-library, or advertising-ID
  permission is declared.
- Room is used only for the app's private local database. No analytics,
  advertising, crash-reporting, account, cloud-storage, or social SDK
  dependency is present.
- `allowBackup` is false. Cloud backup and device transfer exclude root, files,
  databases, SharedPreferences, and external app data.
- The `FileProvider` is not exported and grants URI access only when the app
  deliberately shares a managed recording or opens an attached sheet-music
  copy in another app.

## Legacy recording directory

The app intentionally retains the internal/app-specific directory name
`Practice Partner` in `RecordingManager` and `file_paths.xml`. Existing
installations may already have session records that point to videos under that
directory. Renaming it without a transactional file-and-database migration
would make those recordings unavailable. The legacy directory is not a
user-facing product name; a future rename must migrate both internal and
app-specific external recordings before updating stored paths.

## Deletion

Parent Space > Privacy & data > Delete all data requires typing `DELETE`.
The action calls Android's `ActivityManager.clearApplicationUserData()`, which
atomically removes application preferences, databases, caches, app-managed
recordings and sheet music, app-specific external files, and the consent record,
then closes the app.

Files saved through the Android file picker, exported archives, and videos
already shared to another app are outside LK Practice's application data and
must be deleted from their destination separately. The UI and policy disclose
this boundary.

## Store assets

No Play Store screenshot asset directory currently exists in this project.
Future screenshots must use fictional profiles such as `Maya`, `Avery`, or
`Bailey`; never use a real child's name, school information, recordings, sheet
music, filenames, or workbook locations.

Before public release, publish a web-hosted copy of the privacy policy, use its
URL in the Play Console, complete the current Data safety and Target audience
forms consistently with this audit, and perform a final real-device permission,
consent, deletion, and TalkBack test.
