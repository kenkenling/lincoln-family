# Excel backup and assignment sync

Practice Partner 0.6.0 creates and updates a real Excel `.xlsx` workbook through Android's system document picker.

## Connect a workbook

1. Install the latest APK and open **Profile & connections**.
2. Enter the exact Google account email that should own the workbook.
3. Tap **Create Excel in Google Drive**.
4. In the picker, open **Locations** and choose **Google Drive**.
5. Confirm the same Google account inside Drive.
6. Select a private folder and save the suggested file name.
7. Return to the app and confirm **Google Drive workbook connected**, **Storage: Google Drive**, and the verified account.
8. Optionally tap **Open Excel workbook** to inspect it.

To reuse a file created by Practice Partner, choose **Reconnect Practice Partner workbook**. The app checks its sheet names and template markers before writing. It rejects unrelated workbooks.

## Automatic backup

Assignments, profile updates, repetitions, elapsed time, and practice-log events mark the workbook as unsynced. Practice Partner waits until activity settles, then updates the connected workbook automatically. During an active timer, the delay resets so the app does not rewrite Drive every few seconds.

Use **Save changes now** for an immediate write. A green profile dot means the current local revision was saved through the Drive provider. Amber means changes are waiting or the connection needs verification. Red means the last attempt failed.

The workbook contains:

- `Profile`
- `Assignments`
- `Daily Progress`
- `Daily Summary`
- `Practice Log`

`Daily Summary` stores total practice seconds once per date instead of repeating the same daily total on every assignment row.

## Load assignment edits from Excel

Use **Load assignments from Excel** to import additions or edits from the `Assignments` worksheet.

- Newer unsaved device changes must be saved first; loading is blocked while the app is dirty.
- Assignment IDs identify matching rows.
- Invalid and duplicate rows are skipped and reported.
- YouTube references transfer normally.
- Device videos and local sheet music are not portable. A local-video row loads as a placeholder and must be reattached in the Assign tab on that device.
- Practice progress and recordings are not replaced by assignment import.

## Verification and privacy

- The official Drive provider package and selected Drive account must both match before writing.
- The workbook is generated in memory, checked, written, reopened, and checked again.
- If verification fails, the previous workbook bytes are restored when possible.
- Android grants access only to the selected document.
- No Google password, OAuth access token, broad Drive permission, or sharing change is used.
- Recordings and private local URIs are excluded from Excel.
- Google Drive controls final network synchronization. Practice Partner verifies the provider write, not server-side upload completion.

If Drive is absent from the picker, install or enable the Google Drive app and sign in. Do not save while the picker still shows Downloads or another device folder; the app rejects non-Drive destinations.
