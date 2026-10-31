# Local Excel file setup

Practice Partner 0.8.0 keeps its profile, practice history, and recordings local. It can also create and update one local Excel `.xlsx` file for assignments and readable snapshots.

## Configure the file

1. Open the small settings button, then choose **Profile & local file**.
2. Save the student name and instrument.
3. Tap **Create local Excel file**.
4. Choose a device location such as **Downloads** or **Documents**, then save the suggested name.
5. Return to the app and confirm **Local Excel file ready**.
6. Optionally tap **Open local Excel file** to inspect it.

To reuse a file created by Practice Partner, choose **Choose existing Practice Partner file**. The app checks its sheet names and template markers before writing and rejects unrelated workbooks.

## Assignment updates

Assignments work in both directions:

- Add or edit an assignment in the app, and Practice Partner saves it to the configured local file after editing settles.
- Add or edit rows in the workbook, then use **Load assignments from local file**.
- Use **Save changes to local file** for an immediate app-to-file update.

Profile changes also update the workbook. Practice timing, repetitions, completion, and event details remain local in the app and are included as snapshots when the workbook is saved.

The workbook contains:

- `Profile`
- `Assignments`
- `Daily Progress`
- `Daily Summary`
- `Practice Log`

The exact first-row headings are:

- `Profile`: `Key`, `Value`
- `Assignments`: `Assignment ID`, `Assigned Date`, `Title`, `Reference Type`, `Reference Link`, `Clip Start Seconds`, `Clip End Seconds`, `Target Repetitions`, `Teacher Note`, `Why Important`, `Sheet Music Link`, `Has Local Sheet Music`
- `Daily Progress`: `Date`, `Assignment ID`, `Assignment Title`, `Repetitions Completed`, `Target Repetitions`, `Completed`
- `Daily Summary`: `Date`, `Total Practice Seconds`, `Assignments Completed`, `Assignments Planned`, `Repetitions Completed`
- `Practice Log`: `Event ID`, `Date`, `Assignment ID`, `Assignment Title`, `Event`, `Timestamp UTC`, `Recording Saved`, `Recording Filename`

## Privacy

- No Google Drive or YouTube account is connected.
- Android grants access only to the file selected by the parent.
- Other Excel files are not overwritten.
- Video content, private device paths, and private content URIs are excluded from Excel.
- The safe recording filename is included so a take can be matched with a manually shared or uploaded copy.
- Practice recordings remain in app-managed storage until shared or deleted.
