import Foundation
import LKPracticeCore

enum CheckFailure: Error, CustomStringConvertible {
    case failed(String)

    var description: String {
        switch self {
        case let .failed(message): "CHECK FAILED: \(message)"
        }
    }
}

func require(_ condition: @autoclosure () -> Bool, _ message: String) throws {
    guard condition() else {
        throw CheckFailure.failed(message)
    }
}

func assignment(
    id: String,
    profileID: String,
    start: DateKey,
    end: DateKey,
    modes: Set<PracticeMode> = [.playSolo]
) throws -> Assignment {
    try Assignment(
        id: id,
        childProfileID: profileID,
        title: "Practice \(id)",
        startDate: start,
        endDate: end,
        targetRepetitions: 3,
        targetMinutes: 1,
        enabledModes: modes,
        createdAtMillis: 1,
        updatedAtMillis: 1
    )
}

func runChecks() throws {
    let monday = try DateKey("2026-11-02")
    let tuesday = try DateKey("2026-11-03")
    try require(tuesday.mondayOfWeek == monday, "week should begin Monday")
    var pacificCalendar = Calendar(identifier: .gregorian)
    pacificCalendar.timeZone = TimeZone(identifier: "America/Los_Angeles")!
    let pacificLateEvening = pacificCalendar.date(
        from: DateComponents(
            year: 2026,
            month: 11,
            day: 3,
            hour: 23,
            minute: 30
        )
    )!
    let pacificDateKey = DateKey(date: pacificLateEvening, calendar: pacificCalendar)
    try require(
        pacificDateKey.value == "2026-11-03" &&
            pacificDateKey.adding(days: 1).value == "2026-11-04",
        "Pacific-time date arithmetic must not shift at UTC midnight"
    )
    try require(
        AssignmentDates.allDates(from: monday, to: tuesday).count == 2,
        "multi-day range should include both endpoints"
    )
    try require(
        !AssignmentDates.isValid(from: monday, to: monday.adding(days: 30)),
        "assignments must be capped at 30 inclusive days"
    )

    var nextID = 0
    let household = Household(
        id: "household",
        childProfiles: [
            ChildProfile(id: "student-a", name: "Student", createdAtMillis: 1),
            ChildProfile(id: "student-b", name: "Second Student", createdAtMillis: 2),
        ],
        activeChildProfileID: "student-a"
    )
    let engine = PracticeEngine(
        household: household,
        currentDate: monday,
        nowMillis: { 1_730_000_000_000 },
        makeID: {
            nextID += 1
            return "id-\(nextID)"
        }
    )
    try engine.addAssignment(
        assignment(
            id: "tremolo",
            profileID: "student-a",
            start: monday,
            end: tuesday,
            modes: [.listen, .playAlong, .playSolo]
        )
    )

    let session = try engine.startPractice(assignmentID: "tremolo", mode: .listen)
    try engine.recordRepetition()
    try engine.switchMode(to: .playAlong)
    try engine.recordRepetition()
    try engine.switchMode(to: .playSolo)
    try engine.recordRepetition()
    let finished = try engine.finishPractice(elapsedSeconds: 95)

    try require(finished.status == .completed, "three repetitions should complete the task")
    try require(
        finished.masteryStatus == .notAssessed,
        "completion must not imply mastery"
    )
    try require(
        finished.modesUsed == [.listen, .playAlong, .playSolo],
        "mode changes should be retained"
    )
    try engine.submitReflection(
        sessionID: session.id,
        reflection: .easy,
        studentFeelsConfident: true
    )
    let reflected = try engine.snapshot(for: "student-a").sessions.first { $0.id == session.id }
    try require(
        reflected?.masteryStatus == .studentConfident,
        "student confidence should be stored separately"
    )

    try engine.setCurrentDate(tuesday)
    let tuesdayProgress = try engine.snapshot(for: "student-a").progress(on: tuesday)
    try require(
        tuesdayProgress.repetitionsByTaskID["tremolo", default: 0] == 0,
        "each date in a range needs independent progress"
    )

    var editedAssignment = try engine.snapshot(for: "student-a").assignments.first!
    editedAssignment.title = "Updated tremolo"
    try engine.addAssignment(editedAssignment)
    let editedSnapshot = try engine.snapshot(for: "student-a")
    try require(
        editedSnapshot.assignments.first?.revision == 2 &&
            editedSnapshot.assignmentHistory.count == 1,
        "editing an assignment should retain the previous revision"
    )
    try engine.archiveAssignment("tremolo")
    let archivedSnapshot = try engine.snapshot(for: "student-a")
    try require(
        archivedSnapshot.assignments.first?.isArchived == true,
        "archiving should hide an assignment without deleting its history"
    )
    try engine.restoreAssignment("tremolo")
    let restoredSnapshot = try engine.snapshot(for: "student-a")
    try require(
        restoredSnapshot.assignments.first?.isArchived == false,
        "an archived assignment should be restorable"
    )

    try engine.selectProfile("student-b")
    let secondStudentAssignments = try engine.todayAssignments()
    try require(
        secondStudentAssignments.isEmpty,
        "profiles must not leak assignments"
    )
    let newProfile = try engine.addProfile(name: "Third Student", instrument: "Piano")
    try require(engine.activeProfile?.id == newProfile.id, "new profiles should become active")
    try engine.deleteProfile(newProfile.id)
    try require(
        !engine.household.childProfiles.contains(where: { $0.id == newProfile.id }),
        "deleting a profile should remove its scoped data"
    )

    let archive = PracticeArchive(
        household: engine.household,
        snapshotsByProfileID: engine.snapshotsByProfileID
    )
    let data = try JSONEncoder().encode(archive)
    let decoded = try JSONDecoder().decode(PracticeArchive.self, from: data)
    try require(decoded == archive, "local archive should round-trip")

    let week = try engine.weeklyPractice()
    try require(week.count == 7, "weekly pattern should contain seven days")
}

do {
    try runChecks()
    print("LK Practice core checks passed.")
} catch {
    fputs("\(error)\n", stderr)
    exit(1)
}
