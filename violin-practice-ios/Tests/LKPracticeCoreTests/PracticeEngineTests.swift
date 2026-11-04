import Foundation
import Testing
@testable import LKPracticeCore

@Suite("Practice engine")
struct PracticeEngineTests {
    @Test
    func startRepetitionsModeSwitchAndFinish() throws {
        let date = try DateKey("2026-11-03")
        let engine = makeEngine(date: date)
        try engine.addAssignment(
            makeAssignment(
                id: "tremolo",
                profileID: "student-a",
                start: date,
                end: date,
                modes: [.listen, .playAlong, .playSolo]
            )
        )

        let started = try engine.startPractice(assignmentID: "tremolo", mode: .listen)
        try engine.recordRepetition()
        try engine.switchMode(to: .playAlong)
        try engine.recordRepetition()
        try engine.switchMode(to: .playSolo)
        try engine.recordRepetition()
        let finished = try engine.finishPractice(elapsedSeconds: 180)

        #expect(started.status == .active)
        #expect(finished.status == .completed)
        #expect(finished.repetitions == 3)
        #expect(finished.modesUsed == [.listen, .playAlong, .playSolo])
        #expect(engine.activeSessionID == nil)

        let snapshot = try engine.snapshot(for: "student-a")
        #expect(snapshot.progress(on: date).repetitionsByTaskID["tremolo"] == 3)
        #expect(snapshot.progress(on: date).completedTaskIDs.contains("tremolo"))
        #expect(snapshot.events.contains { $0.message == "Switched to Play along" })
    }

    @Test
    func progressIsIndependentForEachDayInRange() throws {
        let monday = try DateKey("2026-11-02")
        let tuesday = try DateKey("2026-11-03")
        let engine = makeEngine(date: monday)
        try engine.addAssignment(
            makeAssignment(
                id: "scale",
                profileID: "student-a",
                start: monday,
                end: tuesday
            )
        )

        _ = try engine.startPractice(assignmentID: "scale", mode: .playSolo)
        try engine.recordRepetition()
        _ = try engine.finishPractice(elapsedSeconds: 45)
        try engine.setCurrentDate(tuesday)

        let tuesdayProgress = try engine.snapshot(for: "student-a").progress(on: tuesday)
        #expect(tuesdayProgress.repetitionsByTaskID["scale", default: 0] == 0)
        #expect(!tuesdayProgress.completedTaskIDs.contains("scale"))
        #expect(try engine.todayAssignments().map(\.id) == ["scale"])
    }

    @Test
    func studentInitiatedPracticeUsesTodayOutsideOriginalRange() throws {
        let originalDate = try DateKey("2026-11-01")
        let today = try DateKey("2026-11-03")
        let engine = makeEngine(date: today)
        try engine.addAssignment(
            makeAssignment(
                id: "old-piece",
                profileID: "student-a",
                start: originalDate,
                end: originalDate
            )
        )

        #expect(throws: PracticeEngineError.self) {
            try engine.startPractice(
                assignmentID: "old-piece",
                mode: .playSolo
            )
        }

        let session = try engine.startPractice(
            assignmentID: "old-piece",
            mode: .playSolo,
            studentInitiated: true
        )
        #expect(session.studentInitiated)
        #expect(session.date == today)
    }

    @Test
    func profilesKeepAssignmentsAndPracticeSeparate() throws {
        let date = try DateKey("2026-11-03")
        let engine = makeEngine(date: date, includeSecondProfile: true)
        try engine.addAssignment(
            makeAssignment(
                id: "student-a-task",
                profileID: "student-a",
                start: date,
                end: date
            )
        )

        try engine.selectProfile("student-b")
        #expect(try engine.todayAssignments().isEmpty)
        try engine.addAssignment(
            makeAssignment(
                id: "student-b-task",
                profileID: "student-b",
                start: date,
                end: date
            )
        )

        #expect(try engine.todayAssignments().map(\.id) == ["student-b-task"])
        #expect(try engine.snapshot(for: "student-a").assignments.map(\.id) == ["student-a-task"])
        #expect(try engine.snapshot(for: "student-b").assignments.map(\.id) == ["student-b-task"])
    }

    @Test
    func dateRolloverInterruptsAnActiveSession() throws {
        let date = try DateKey("2026-11-03")
        let engine = makeEngine(date: date)
        try engine.addAssignment(
            makeAssignment(
                id: "piece",
                profileID: "student-a",
                start: date,
                end: date.adding(days: 1)
            )
        )
        let session = try engine.startPractice(assignmentID: "piece", mode: .playSolo)

        try engine.setCurrentDate(date.adding(days: 1))

        #expect(engine.activeSession == nil)
        let saved = try #require(
            try engine.snapshot(for: "student-a").sessions.first { $0.id == session.id }
        )
        #expect(saved.status == .interrupted)
    }

    @Test
    func completionAndMasteryRemainSeparate() throws {
        let date = try DateKey("2026-11-03")
        let engine = makeEngine(date: date)
        try engine.addAssignment(
            makeAssignment(
                id: "shifting",
                profileID: "student-a",
                start: date,
                end: date
            )
        )
        let session = try engine.startPractice(assignmentID: "shifting", mode: .playSolo)
        for _ in 0..<3 {
            try engine.recordRepetition()
        }
        let finished = try engine.finishPractice(elapsedSeconds: 60)

        #expect(finished.status == .completed)
        #expect(finished.masteryStatus == .notAssessed)

        try engine.submitReflection(
            sessionID: session.id,
            reflection: .easy,
            studentFeelsConfident: true
        )
        let reflected = try #require(
            try engine.snapshot(for: "student-a").sessions.first { $0.id == session.id }
        )
        #expect(reflected.masteryStatus == .studentConfident)
        #expect(reflected.reflection == .easy)
    }

    @Test
    func recordingIsAttachedToActiveSessionAndCanBeReplaced() throws {
        let date = try DateKey("2026-11-03")
        let engine = makeEngine(date: date)
        try engine.addAssignment(
            makeAssignment(
                id: "boreas",
                profileID: "student-a",
                start: date,
                end: date,
                modes: [.playAlong]
            )
        )
        _ = try engine.startPractice(assignmentID: "boreas", mode: .playAlong)

        let firstPrevious = try engine.attachRecording(
            path: "lkpractice-recording://takes/student-a/session/first.mov"
        )
        let secondPrevious = try engine.attachRecording(
            path: "lkpractice-recording://takes/student-a/session/second.mov"
        )

        #expect(firstPrevious == nil)
        #expect(secondPrevious == "lkpractice-recording://takes/student-a/session/first.mov")
        #expect(
            engine.activeSession?.recordingPath ==
                "lkpractice-recording://takes/student-a/session/second.mov"
        )
        let snapshot = try engine.snapshot(for: "student-a")
        #expect(
            snapshot.events.filter {
                $0.message == "Saved a private practice recording"
            }.count == 2
        )
    }

    @Test
    func weeklyPracticeReturnsSevenGentlePatternDays() throws {
        let date = try DateKey("2026-11-03")
        let engine = makeEngine(date: date)
        try engine.addAssignment(
            makeAssignment(
                id: "piece",
                profileID: "student-a",
                start: date,
                end: date
            )
        )
        _ = try engine.startPractice(assignmentID: "piece", mode: .playSolo)
        try engine.recordRepetition()
        _ = try engine.finishPractice(elapsedSeconds: 30)

        let week = try engine.weeklyPractice()
        #expect(week.count == 7)
        #expect(week.first?.date.value == "2026-11-02")
        #expect(week[1].practiced)
        #expect(week.filter(\.practiced).count == 1)
    }

    @Test
    func snapshotCodableRoundTrip() throws {
        let date = try DateKey("2026-11-03")
        let assignment = makeAssignment(
            id: "piece",
            profileID: "student-a",
            start: date,
            end: date.adding(days: 2)
        )
        var snapshot = PracticeSnapshot(assignments: [assignment])
        var progress = DailyProgress(date: date)
        progress.recordRepetition(for: assignment.id)
        snapshot.setProgress(progress)

        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        let data = try encoder.encode(snapshot)
        let decoded = try JSONDecoder().decode(PracticeSnapshot.self, from: data)

        #expect(decoded == snapshot)
    }

    private func makeEngine(
        date: DateKey,
        includeSecondProfile: Bool = false
    ) -> PracticeEngine {
        let profiles = [
            ChildProfile(
                id: "student-a",
                name: "Student",
                createdAtMillis: 1
            ),
        ] + (includeSecondProfile
            ? [
                ChildProfile(
                    id: "student-b",
                    name: "Second Student",
                    instrument: "Cello",
                    createdAtMillis: 2
                ),
            ]
            : [])
        let ids = TestIDSource()
        return PracticeEngine(
            household: Household(
                id: "household",
                childProfiles: profiles,
                activeChildProfileID: "student-a"
            ),
            currentDate: date,
            nowMillis: { 1_730_000_000_000 },
            makeID: { ids.next() }
        )
    }

    private func makeAssignment(
        id: String,
        profileID: String,
        start: DateKey,
        end: DateKey,
        modes: Set<PracticeMode> = [.playSolo]
    ) -> Assignment {
        try! Assignment(
            id: id,
            childProfileID: profileID,
            title: id,
            startDate: start,
            endDate: end,
            targetRepetitions: 3,
            targetMinutes: 1,
            enabledModes: modes,
            createdAtMillis: 1,
            updatedAtMillis: 1
        )
    }
}

private final class TestIDSource {
    private var value = 0

    func next() -> String {
        value += 1
        return "id-\(value)"
    }
}
