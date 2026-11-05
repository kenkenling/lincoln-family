# Local Excel file setup

LK Practice 1.0.0-beta.1 keeps household profiles, practice history, sessions, and
recordings local. Each child can optionally use a separate local Excel `.xlsx`
file for assignments and readable snapshots.

## Configure the file

1. Open **Parent Space** and enter the local parent PIN.
2. If necessary, use **Family profiles** to select the child.
3. Choose **Profile & local file**.
4. Save the student name and instrument.
5. Tap **Create local Excel file**.
6. Choose a device location such as **Downloads** or **Documents**, then save
   the suggested `LK Practice - Student Name.xlsx` file.
7. Return to the app and confirm **Local Excel file ready**.
8. Optionally tap **Open local Excel file** to inspect it.

To reuse a file created by LK Practice, choose **Choose existing LK Practice
file**. Compatible legacy Practice Partner workbooks are also accepted. The app
checks sheet names and template markers before writing and rejects unrelated
workbooks.

## Assignment updates

Assignments work in both directions:

- Add or edit an assignment in the app, and LK Practice saves it to the
  selected child's configured file after editing settles.
- Archive an assignment to hide it from Today without deleting its sessions or
  summary history. Restoring it makes it available again. The archive state is
  saved in the workbook.
- Add or edit rows in the workbook, then use **Load assignments from local file**.
- Use **Save changes to local file** for an immediate app-to-file update.

Profile changes also update the workbook. Practice timing, repetitions, completion, and event details remain local in the app and are included as snapshots when the workbook is saved.

The workbook contains:

- `Profile`
- `Assignments`
- `Daily Progress`
- `Daily Summary`
- `Practice Sessions`
- `Practice Log`

The exact first-row headings are:

- `Profile`: `Key`, `Value`
- `Assignments`: `Assignment ID`, `Student ID`, `Instrument`, `Assigned Date`,
  `Title`, `Reference Type`, `Reference Link`, `Clip Start Seconds`,
  `Clip End Seconds`, `Target Repetitions`, `Teacher Note`, `Why Important`,
  `Sheet Music Link`, `Has Local Sheet Music`, `Assignment Type`, `Goal Type`,
  `Target Minutes`, `Practice Steps`, `Revision`, `Archived UTC`
- `Daily Progress`: `Date`, `Student ID`, `Instrument`, `Assignment ID`,
  `Assignment Title`, `Repetitions Completed`, `Target Repetitions`,
  `Practice Seconds`, `Target Practice Seconds`, `Completed`
- `Daily Summary`: `Date`, `Student ID`, `Instrument`,
  `Total Practice Seconds`, `Assignments Completed`, `Assignments Planned`,
  `Repetitions Completed`, `Sessions`, `Reflections`
- `Practice Sessions`: `Session ID`, `Date`, `Student ID`, `Instrument`,
  `Assignment ID`, `Assignment Revision`, `Assignment Title`, `Assignment Type`,
  `Goal Type`, `Target Repetitions`, `Target Practice Seconds`, `Mode`,
  `Started UTC`, `Ended UTC`, `Practice Seconds`, `Repetitions Completed`,
  `Status`, `Reflection`
- `Practice Log`: `Event ID`, `Date`, `Student ID`, `Instrument`, `Session ID`,
  `Assignment ID`, `Assignment Title`, `Event`, `Timestamp UTC`,
  `Recording Saved`, `Recording Filename`

## Privacy

- No Google Drive or YouTube account is connected.
- A separate workbook is configured for each child profile.
- Android grants access only to the file selected by the parent.
- Other Excel files are not overwritten.
- Video content, private device paths, and private content URIs are excluded from Excel.
- The safe recording filename is included so a take can be matched with a manually shared or uploaded copy.
- Practice recordings remain in app-managed storage until shared or deleted.
- The family ZIP export is separate from Excel. It contains readable profile,
  assignment, and practice-history files plus available app-managed recordings
  for only the selected child.
- Deleting a child leaves the external workbook itself in place and releases
  LK Practice's saved access when no remaining profile uses that file.
