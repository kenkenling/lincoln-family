# Excel backup and assignment sync

Practice Partner 0.6.1 creates and updates a real Excel `.xlsx` workbook through Android's system document picker.

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

## Assignment synchronization

Assignments are synchronized in both directions:

- Add or edit an assignment in the app, and Practice Partner writes it to the connected Drive workbook after editing settles.
- Add or edit rows in the workbook, then use **Load assignments from Excel** to bring them into the app.
- Use **Sync assignments to Excel** for an immediate app-to-Drive write.

Profile changes also trigger a workbook update. Practice timing, repetitions, completion, and event details are saved locally but do not trigger Drive writes. This prevents a normal practice session from overwriting teacher edits that have not yet been loaded.

A green profile dot means the current assignment/profile revision was saved through the Drive provider. Amber means changes are waiting or the connection needs verification. Red means the last attempt failed.

The workbook contains:

- `Profile`
- `Assignments`
- `Daily Progress`
- `Daily Summary`
- `Practice Log`

`Daily Summary` stores total practice seconds once per date instead of repeating the same daily total on every assignment row.

New workbook creation verifies every sheet and every first-row column heading before reporting success. The exact headings are:

- `Profile`: `Key`, `Value`
- `Assignments`: `Assignment ID`, `Assigned Date`, `Title`, `Reference Type`, `Reference Link`, `Clip Start Seconds`, `Clip End Seconds`, `Target Repetitions`, `Teacher Note`, `Why Important`, `Sheet Music Link`, `Has Local Sheet Music`
- `Daily Progress`: `Date`, `Assignment ID`, `Assignment Title`, `Repetitions Completed`, `Target Repetitions`, `Completed`
- `Daily Summary`: `Date`, `Total Practice Seconds`, `Assignments Completed`, `Assignments Planned`, `Repetitions Completed`
- `Practice Log`: `Event ID`, `Date`, `Assignment ID`, `Assignment Title`, `Event`, `Timestamp UTC`, `Recording Saved`

The practice-history sheets are snapshots for later reporting. They are not loaded back into the app in this version.

## Load assignment edits from Excel

Use **Load assignments from Excel** to import additions or edits from the `Assignments` worksheet.

- Newer unsaved device changes must be saved first; loading is blocked while the app is dirty.
- Assignment IDs identify matching rows. A teacher may leave the ID blank for a new row; the app generates an ID and writes it back to the workbook.
- Assigned dates may be entered as `YYYY-MM-DD` or as a normal Excel date cell.
- Invalid and duplicate rows are skipped and reported.
- YouTube references transfer normally.
- Device videos and local sheet music are not portable. A local-video row loads as a placeholder and must be reattached in the Assign tab on that device.
- Practice progress and recordings are not replaced by assignment import.

To avoid competing edits, load workbook changes before creating or editing assignments in the app. The app does not silently merge two simultaneously edited versions.

## Verification and privacy

- The official Drive provider package and selected Drive account must both match before writing.
- The workbook is generated in memory, checked, written, reopened, and checked again.
- If verification fails, the previous workbook bytes are restored when possible.
- Android grants access only to the selected document.
- No Google password, OAuth access token, broad Drive permission, or sharing change is used.
- Recordings and private local URIs are excluded from Excel.
- Google Drive controls final network synchronization. Practice Partner verifies the provider write, not server-side upload completion.

If Drive is absent from the picker, install or enable the Google Drive app and sign in. Do not save while the picker still shows Downloads or another device folder; the app rejects non-Drive destinations.
