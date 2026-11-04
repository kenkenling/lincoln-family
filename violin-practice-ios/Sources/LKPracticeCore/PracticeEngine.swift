import Foundation

public enum PracticeEngineError: Error, Equatable, LocalizedError {
    case noActiveProfile
    case profileNotFound
    case assignmentNotFound
    case assignmentBelongsToAnotherProfile
    case assignmentNotAvailableToday
    case modeNotEnabled
    case sessionAlreadyActive
    case noActiveSession
    case sessionNotFound
    case invalidProfile

    public var errorDescription: String? {
        switch self {
        case .noActiveProfile: "Choose a student profile first."
        case .profileNotFound: "The student profile could not be found."
        case .assignmentNotFound: "The assignment could not be found."
        case .assignmentBelongsToAnotherProfile: "This assignment belongs to another student."
        case .assignmentNotAvailableToday: "This assignment is not available today."
        case .modeNotEnabled: "That practice mode is not enabled for this assignment."
        case .sessionAlreadyActive: "Finish the current practice session first."
        case .noActiveSession: "There is no active practice session."
        case .sessionNotFound: "The practice session could not be found."
        case .invalidProfile: "Enter a student name and instrument."
        }
    }
}

public final class PracticeEngine {
    public private(set) var household: Household
    public private(set) var snapshotsByProfileID: [String: PracticeSnapshot]
    public private(set) var currentDate: DateKey
    public private(set) var activeSessionID: String?
    public private(set) var selectedMode: PracticeMode?

    private let nowMillis: () -> Int64
    private let makeID: () -> String

    public init(
        household: Household,
        snapshotsByProfileID: [String: PracticeSnapshot] = [:],
        currentDate: DateKey = .today(),
        nowMillis: @escaping () -> Int64 = {
            Int64(Date().timeIntervalSince1970 * 1_000)
        },
        makeID: @escaping () -> String = {
            UUID().uuidString.lowercased()
        }
    ) {
        self.household = household
        self.snapshotsByProfileID = snapshotsByProfileID
        self.currentDate = currentDate
        self.nowMillis = nowMillis
        self.makeID = makeID

        for profile in household.childProfiles where self.snapshotsByProfileID[profile.id] == nil {
            self.snapshotsByProfileID[profile.id] = PracticeSnapshot()
        }
    }

    public var activeProfile: ChildProfile? {
        guard let activeID = household.activeChildProfileID else {
            return nil
        }
        return household.childProfiles.first { $0.id == activeID }
    }

    public var activeSnapshot: PracticeSnapshot? {
        guard let profileID = household.activeChildProfileID else {
            return nil
        }
        return snapshotsByProfileID[profileID]
    }

    public var activeSession: PracticeSession? {
        guard
            let sessionID = activeSessionID,
            let snapshot = activeSnapshot
        else {
            return nil
        }
        return snapshot.sessions.first { $0.id == sessionID }
    }

    public func selectProfile(_ profileID: String) throws {
        guard household.childProfiles.contains(where: { $0.id == profileID }) else {
            throw PracticeEngineError.profileNotFound
        }
        if activeSessionID != nil {
            try interruptActiveSession()
        }
        household.activeChildProfileID = profileID
        snapshotsByProfileID[profileID, default: PracticeSnapshot()] = snapshotsByProfileID[profileID] ?? PracticeSnapshot()
    }

    @discardableResult
    public func addProfile(name: String, instrument: String) throws -> ChildProfile {
        let cleanName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanInstrument = instrument.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanName.isEmpty, !cleanInstrument.isEmpty else {
            throw PracticeEngineError.invalidProfile
        }

        let profile = ChildProfile(
            id: makeID(),
            name: cleanName,
            instrument: cleanInstrument,
            createdAtMillis: nowMillis()
        )
        household.childProfiles.append(profile)
        household.activeChildProfileID = profile.id
        snapshotsByProfileID[profile.id] = PracticeSnapshot()
        return profile
    }

    public func updateProfile(_ profile: ChildProfile) throws {
        let cleanName = profile.name.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanInstrument = profile.instrument.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanName.isEmpty, !cleanInstrument.isEmpty else {
            throw PracticeEngineError.invalidProfile
        }
        guard let index = household.childProfiles.firstIndex(where: { $0.id == profile.id }) else {
            throw PracticeEngineError.profileNotFound
        }
        var updated = profile
        updated.name = cleanName
        updated.instrument = cleanInstrument
        household.childProfiles[index] = updated
    }

    public func deleteProfile(_ profileID: String) throws {
        guard household.childProfiles.contains(where: { $0.id == profileID }) else {
            throw PracticeEngineError.profileNotFound
        }
        if household.activeChildProfileID == profileID, activeSessionID != nil {
            try interruptActiveSession()
        }
        household.childProfiles.removeAll { $0.id == profileID }
        snapshotsByProfileID.removeValue(forKey: profileID)
        if household.activeChildProfileID == profileID {
            household.activeChildProfileID = household.childProfiles.first?.id
        }
    }

    public func setCurrentDate(_ date: DateKey) throws {
        if date != currentDate, activeSessionID != nil {
            try interruptActiveSession()
        }
        currentDate = date
    }

    public func addAssignment(_ assignment: Assignment) throws {
        try assignment.validate()
        guard let profile = activeProfile else {
            throw PracticeEngineError.noActiveProfile
        }
        guard assignment.childProfileID == profile.id else {
            throw PracticeEngineError.assignmentBelongsToAnotherProfile
        }

        var snapshot = snapshotsByProfileID[profile.id] ?? PracticeSnapshot()
        if let index = snapshot.assignments.firstIndex(where: { $0.id == assignment.id }) {
            let previous = snapshot.assignments[index]
            guard previous != assignment else {
                return
            }
            snapshot.assignmentHistory.append(
                AssignmentHistory(
                    id: makeID(),
                    assignmentID: previous.id,
                    revision: previous.revision,
                    changedAtMillis: nowMillis(),
                    previousAssignment: previous
                )
            )
            var revised = assignment
            revised.revision = previous.revision + 1
            revised.updatedAtMillis = nowMillis()
            snapshot.assignments[index] = revised
        } else {
            snapshot.assignments.append(assignment)
        }
        snapshotsByProfileID[profile.id] = snapshot
    }

    public func archiveAssignment(_ assignmentID: String) throws {
        guard let profile = activeProfile else {
            throw PracticeEngineError.noActiveProfile
        }
        var snapshot = snapshotsByProfileID[profile.id] ?? PracticeSnapshot()
        guard let index = snapshot.assignments.firstIndex(where: { $0.id == assignmentID }) else {
            throw PracticeEngineError.assignmentNotFound
        }
        let previous = snapshot.assignments[index]
        snapshot.assignmentHistory.append(
            AssignmentHistory(
                id: makeID(),
                assignmentID: previous.id,
                revision: previous.revision,
                changedAtMillis: nowMillis(),
                previousAssignment: previous
            )
        )
        snapshot.assignments[index].archivedAtMillis = nowMillis()
        snapshot.assignments[index].revision += 1
        snapshot.assignments[index].updatedAtMillis = nowMillis()
        snapshotsByProfileID[profile.id] = snapshot
    }

    public func restoreAssignment(_ assignmentID: String) throws {
        guard let profile = activeProfile else {
            throw PracticeEngineError.noActiveProfile
        }
        var snapshot = snapshotsByProfileID[profile.id] ?? PracticeSnapshot()
        guard let index = snapshot.assignments.firstIndex(where: { $0.id == assignmentID }) else {
            throw PracticeEngineError.assignmentNotFound
        }
        let previous = snapshot.assignments[index]
        snapshot.assignmentHistory.append(
            AssignmentHistory(
                id: makeID(),
                assignmentID: previous.id,
                revision: previous.revision,
                changedAtMillis: nowMillis(),
                previousAssignment: previous
            )
        )
        snapshot.assignments[index].archivedAtMillis = nil
        snapshot.assignments[index].revision += 1
        snapshot.assignments[index].updatedAtMillis = nowMillis()
        snapshotsByProfileID[profile.id] = snapshot
    }

    public func todayAssignments() throws -> [Assignment] {
        guard let profile = activeProfile else {
            throw PracticeEngineError.noActiveProfile
        }
        let snapshot = snapshotsByProfileID[profile.id] ?? PracticeSnapshot()
        return snapshot.assignments
            .filter { $0.childProfileID == profile.id && $0.isScheduled(on: currentDate) }
            .sorted {
                if $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedSame {
                    return $0.id < $1.id
                }
                return $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending
            }
    }

    @discardableResult
    public func startPractice(
        assignmentID: String,
        mode: PracticeMode,
        studentInitiated: Bool = false
    ) throws -> PracticeSession {
        guard activeSessionID == nil else {
            throw PracticeEngineError.sessionAlreadyActive
        }
        let (profile, assignment, snapshot) = try activeAssignment(
            assignmentID: assignmentID,
            allowOutsideDateRange: studentInitiated
        )
        guard assignment.enabledModes.contains(mode) else {
            throw PracticeEngineError.modeNotEnabled
        }

        let session = PracticeSession(
            id: makeID(),
            childProfileID: profile.id,
            assignmentID: assignment.id,
            assignmentRevision: assignment.revision,
            date: currentDate,
            startedAtMillis: nowMillis(),
            goalType: assignment.goalType,
            targetRepetitions: assignment.targetRepetitions,
            targetMinutes: assignment.targetMinutes,
            modesUsed: [mode],
            studentInitiated: studentInitiated
        )

        var updated = snapshot
        updated.sessions.append(session)
        updated.events.append(
            PracticeEvent(
                id: makeID(),
                childProfileID: profile.id,
                assignmentID: assignment.id,
                date: currentDate,
                atMillis: nowMillis(),
                message: "Started \(mode.title)"
            )
        )
        snapshotsByProfileID[profile.id] = updated
        activeSessionID = session.id
        selectedMode = mode
        return session
    }

    public func switchMode(to mode: PracticeMode) throws {
        let (profileID, snapshot, sessionIndex, assignment) = try activeSessionContext()
        guard assignment.enabledModes.contains(mode) else {
            throw PracticeEngineError.modeNotEnabled
        }
        guard selectedMode != mode else {
            return
        }

        var updated = snapshot
        updated.sessions[sessionIndex].modesUsed.insert(mode)
        updated.events.append(
            PracticeEvent(
                id: makeID(),
                childProfileID: profileID,
                assignmentID: assignment.id,
                date: currentDate,
                atMillis: nowMillis(),
                message: "Switched to \(mode.title)"
            )
        )
        snapshotsByProfileID[profileID] = updated
        selectedMode = mode
    }

    public func recordRepetition() throws {
        let (profileID, snapshot, sessionIndex, assignment) = try activeSessionContext()
        var updated = snapshot
        updated.sessions[sessionIndex].repetitions += 1

        var progress = updated.progress(on: currentDate)
        progress.recordRepetition(for: assignment.id)
        updated.setProgress(progress)
        updated.events.append(
            PracticeEvent(
                id: makeID(),
                childProfileID: profileID,
                assignmentID: assignment.id,
                date: currentDate,
                atMillis: nowMillis(),
                message: "Recorded a repetition"
            )
        )
        snapshotsByProfileID[profileID] = updated
    }

    @discardableResult
    public func attachRecording(path: String) throws -> String? {
        let (profileID, snapshot, sessionIndex, assignment) = try activeSessionContext()
        var updated = snapshot
        let previousPath = updated.sessions[sessionIndex].recordingPath
        updated.sessions[sessionIndex].recordingPath = path
        updated.events.append(
            PracticeEvent(
                id: makeID(),
                childProfileID: profileID,
                assignmentID: assignment.id,
                date: currentDate,
                atMillis: nowMillis(),
                message: "Saved a private practice recording"
            )
        )
        snapshotsByProfileID[profileID] = updated
        return previousPath
    }

    @discardableResult
    public func finishPractice(elapsedSeconds: Int) throws -> PracticeSession {
        let (profileID, snapshot, sessionIndex, assignment) = try activeSessionContext()
        var updated = snapshot
        var session = updated.sessions[sessionIndex]
        session.elapsedSeconds = max(session.elapsedSeconds, elapsedSeconds)
        session.endedAtMillis = nowMillis()
        session.status = session.goalMet ? .completed : .ended
        updated.sessions[sessionIndex] = session

        var progress = updated.progress(on: currentDate)
        progress.addPracticeSeconds(session.elapsedSeconds, for: assignment.id)
        if session.goalMet {
            progress.completedTaskIDs.insert(assignment.id)
        }
        updated.setProgress(progress)
        updated.events.append(
            PracticeEvent(
                id: makeID(),
                childProfileID: profileID,
                assignmentID: assignment.id,
                date: currentDate,
                atMillis: nowMillis(),
                message: session.goalMet ? "Practice goal reached" : "Practice ended"
            )
        )
        snapshotsByProfileID[profileID] = updated
        activeSessionID = nil
        selectedMode = nil
        return session
    }

    public func submitReflection(
        sessionID: String,
        reflection: PracticeReflection,
        studentFeelsConfident: Bool
    ) throws {
        guard let profile = activeProfile else {
            throw PracticeEngineError.noActiveProfile
        }
        var snapshot = snapshotsByProfileID[profile.id] ?? PracticeSnapshot()
        guard let index = snapshot.sessions.firstIndex(where: { $0.id == sessionID }) else {
            throw PracticeEngineError.sessionNotFound
        }
        snapshot.sessions[index].reflection = reflection
        if studentFeelsConfident,
           snapshot.sessions[index].masteryStatus == .notAssessed {
            snapshot.sessions[index].masteryStatus = .studentConfident
        }
        snapshotsByProfileID[profile.id] = snapshot
    }

    public func weeklyPractice() throws -> [WeeklyPracticeDay] {
        guard let profile = activeProfile else {
            throw PracticeEngineError.noActiveProfile
        }
        let snapshot = snapshotsByProfileID[profile.id] ?? PracticeSnapshot()
        return (0..<7).map { offset in
            let date = currentDate.mondayOfWeek.adding(days: offset)
            let progress = snapshot.progress(on: date)
            let seconds = progress.secondsByTaskID.values.reduce(0, +)
            let repetitions = progress.repetitionsByTaskID.values.reduce(0, +)
            return WeeklyPracticeDay(
                date: date,
                practiced: seconds > 0 || repetitions > 0,
                seconds: seconds,
                repetitions: repetitions
            )
        }
    }

    public func snapshot(for profileID: String) throws -> PracticeSnapshot {
        guard household.childProfiles.contains(where: { $0.id == profileID }) else {
            throw PracticeEngineError.profileNotFound
        }
        return snapshotsByProfileID[profileID] ?? PracticeSnapshot()
    }

    private func activeAssignment(
        assignmentID: String,
        allowOutsideDateRange: Bool
    ) throws -> (ChildProfile, Assignment, PracticeSnapshot) {
        guard let profile = activeProfile else {
            throw PracticeEngineError.noActiveProfile
        }
        let snapshot = snapshotsByProfileID[profile.id] ?? PracticeSnapshot()
        guard let assignment = snapshot.assignments.first(where: { $0.id == assignmentID }) else {
            throw PracticeEngineError.assignmentNotFound
        }
        guard assignment.childProfileID == profile.id else {
            throw PracticeEngineError.assignmentBelongsToAnotherProfile
        }
        guard allowOutsideDateRange || assignment.isScheduled(on: currentDate) else {
            throw PracticeEngineError.assignmentNotAvailableToday
        }
        return (profile, assignment, snapshot)
    }

    private func activeSessionContext() throws -> (String, PracticeSnapshot, Int, Assignment) {
        guard let sessionID = activeSessionID else {
            throw PracticeEngineError.noActiveSession
        }
        guard let profile = activeProfile else {
            throw PracticeEngineError.noActiveProfile
        }
        let snapshot = snapshotsByProfileID[profile.id] ?? PracticeSnapshot()
        guard let sessionIndex = snapshot.sessions.firstIndex(where: { $0.id == sessionID }) else {
            throw PracticeEngineError.sessionNotFound
        }
        let assignmentID = snapshot.sessions[sessionIndex].assignmentID
        guard let assignment = snapshot.assignments.first(where: { $0.id == assignmentID }) else {
            throw PracticeEngineError.assignmentNotFound
        }
        return (profile.id, snapshot, sessionIndex, assignment)
    }

    private func interruptActiveSession() throws {
        guard activeSessionID != nil else {
            return
        }
        let (profileID, snapshot, sessionIndex, assignment) = try activeSessionContext()
        var updated = snapshot
        updated.sessions[sessionIndex].endedAtMillis = nowMillis()
        updated.sessions[sessionIndex].status = .interrupted
        updated.events.append(
            PracticeEvent(
                id: makeID(),
                childProfileID: profileID,
                assignmentID: assignment.id,
                date: currentDate,
                atMillis: nowMillis(),
                message: "Practice interrupted"
            )
        )
        snapshotsByProfileID[profileID] = updated
        activeSessionID = nil
        selectedMode = nil
    }
}
