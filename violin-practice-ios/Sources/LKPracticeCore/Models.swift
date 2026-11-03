import Foundation

public enum AppPage: String, Codable, CaseIterable, Sendable {
    case today = "TODAY"
    case assignments = "ASSIGNMENTS"
    case practice = "PRACTICE"
    case summary = "SUMMARY"
}

public enum PracticeMode: String, Codable, CaseIterable, Sendable {
    case listen = "LISTEN"
    case playAlong = "PLAY_ALONG"
    case playSolo = "PLAY_SOLO"

    public var title: String {
        switch self {
        case .listen: "Listen first"
        case .playAlong: "Play along"
        case .playSolo: "Play solo"
        }
    }
}

public enum AssignmentType: String, Codable, CaseIterable, Sendable {
    case technique = "TECHNIQUE"
    case scales = "SCALES"
    case repertoire = "REPERTOIRE"
    case sightReading = "SIGHT_READING"
    case other = "OTHER"
}

public enum PracticeGoalType: String, Codable, CaseIterable, Sendable {
    case repetitions = "REPETITIONS"
    case minutes = "MINUTES"
    case repetitionsAndMinutes = "REPETITIONS_AND_MINUTES"
}

public enum PracticeReflection: String, Codable, CaseIterable, Sendable {
    case easy = "EASY"
    case okay = "OKAY"
    case tricky = "TRICKY"
}

public enum MasteryStatus: String, Codable, CaseIterable, Sendable {
    case notAssessed = "NOT_ASSESSED"
    case studentConfident = "STUDENT_CONFIDENT"
    case teacherApproved = "TEACHER_APPROVED"
}

public enum SourceType: String, Codable, CaseIterable, Sendable {
    case none = "NONE"
    case youtube = "YOUTUBE"
    case localVideo = "LOCAL_VIDEO"
}

public enum SessionStatus: String, Codable, CaseIterable, Sendable {
    case active = "ACTIVE"
    case completed = "COMPLETED"
    case ended = "ENDED"
    case interrupted = "INTERRUPTED"
}

public struct ChildProfile: Codable, Hashable, Identifiable, Sendable {
    public var id: String
    public var name: String
    public var instrument: String
    public var createdAtMillis: Int64

    public init(
        id: String,
        name: String,
        instrument: String = "Violin",
        createdAtMillis: Int64
    ) {
        self.id = id
        self.name = name
        self.instrument = instrument
        self.createdAtMillis = createdAtMillis
    }
}

public struct Household: Codable, Equatable, Sendable {
    public var id: String
    public var childProfiles: [ChildProfile]
    public var activeChildProfileID: String?

    public init(
        id: String,
        childProfiles: [ChildProfile] = [],
        activeChildProfileID: String? = nil
    ) {
        self.id = id
        self.childProfiles = childProfiles
        self.activeChildProfileID = activeChildProfileID
    }
}

/// Phase D model only. A teacher has no access until a parent creates an active
/// StudentTeacherLink. Revoking the link must stop future access without
/// deleting historical attribution.
public struct TeacherProfile: Codable, Hashable, Identifiable, Sendable {
    public var id: String
    public var name: String
    public var email: String

    public init(id: String, name: String, email: String) {
        self.id = id
        self.name = name
        self.email = email
    }
}

/// Parent-granted, child-scoped authorization. A teacher must never infer access
/// to another child in the same household from this link.
public struct StudentTeacherLink: Codable, Hashable, Sendable {
    public var teacherID: String
    public var childProfileID: String
    public var grantedByParentID: String
    public var grantedAtMillis: Int64
    public var revokedAtMillis: Int64?

    public init(
        teacherID: String,
        childProfileID: String,
        grantedByParentID: String,
        grantedAtMillis: Int64,
        revokedAtMillis: Int64? = nil
    ) {
        self.teacherID = teacherID
        self.childProfileID = childProfileID
        self.grantedByParentID = grantedByParentID
        self.grantedAtMillis = grantedAtMillis
        self.revokedAtMillis = revokedAtMillis
    }
}

public struct TeacherFeedback: Codable, Hashable, Identifiable, Sendable {
    public var id: String
    public var sessionID: String
    public var teacherID: String
    public var comment: String
    public var atMillis: Int64

    public init(
        id: String,
        sessionID: String,
        teacherID: String,
        comment: String,
        atMillis: Int64
    ) {
        self.id = id
        self.sessionID = sessionID
        self.teacherID = teacherID
        self.comment = comment
        self.atMillis = atMillis
    }
}

public enum AssignmentValidationError: Error, Equatable, LocalizedError {
    case invalidDateRange
    case noPracticeModes
    case invalidGoal

    public var errorDescription: String? {
        switch self {
        case .invalidDateRange:
            return "Assignments must last between 1 and 30 days."
        case .noPracticeModes:
            return "Choose at least one practice mode."
        case .invalidGoal:
            return "The practice goal must be greater than zero."
        }
    }
}

public struct Assignment: Codable, Equatable, Identifiable, Sendable {
    public var id: String
    public var childProfileID: String
    public var title: String
    public var details: String
    public var type: AssignmentType
    public var startDate: DateKey
    public var endDate: DateKey
    public var goalType: PracticeGoalType
    public var targetRepetitions: Int
    public var targetMinutes: Int
    public var enabledModes: Set<PracticeMode>
    public var sourceType: SourceType
    public var referenceURL: String?
    public var referenceStartSeconds: Int
    public var sheetMusicReference: String?
    public var teacherID: String?
    public var revision: Int
    public var archivedAtMillis: Int64?
    public var createdAtMillis: Int64
    public var updatedAtMillis: Int64

    public init(
        id: String,
        childProfileID: String,
        title: String,
        details: String = "",
        type: AssignmentType = .other,
        startDate: DateKey,
        endDate: DateKey,
        goalType: PracticeGoalType = .repetitions,
        targetRepetitions: Int = 3,
        targetMinutes: Int = 5,
        enabledModes: Set<PracticeMode> = [.playSolo],
        sourceType: SourceType = .none,
        referenceURL: String? = nil,
        referenceStartSeconds: Int = 0,
        sheetMusicReference: String? = nil,
        teacherID: String? = nil,
        revision: Int = 1,
        archivedAtMillis: Int64? = nil,
        createdAtMillis: Int64,
        updatedAtMillis: Int64
    ) throws {
        self.id = id
        self.childProfileID = childProfileID
        self.title = title
        self.details = details
        self.type = type
        self.startDate = startDate
        self.endDate = endDate
        self.goalType = goalType
        self.targetRepetitions = targetRepetitions
        self.targetMinutes = targetMinutes
        self.enabledModes = enabledModes
        self.sourceType = sourceType
        self.referenceURL = referenceURL
        self.referenceStartSeconds = referenceStartSeconds
        self.sheetMusicReference = sheetMusicReference
        self.teacherID = teacherID
        self.revision = revision
        self.archivedAtMillis = archivedAtMillis
        self.createdAtMillis = createdAtMillis
        self.updatedAtMillis = updatedAtMillis
        try validate()
    }

    public var isArchived: Bool {
        archivedAtMillis != nil
    }

    public func isScheduled(on date: DateKey) -> Bool {
        !isArchived && AssignmentDates.contains(date, from: startDate, to: endDate)
    }

    public func validate() throws {
        guard AssignmentDates.isValid(from: startDate, to: endDate) else {
            throw AssignmentValidationError.invalidDateRange
        }
        guard !enabledModes.isEmpty else {
            throw AssignmentValidationError.noPracticeModes
        }

        let hasValidRepetitionGoal = targetRepetitions > 0
        let hasValidMinuteGoal = targetMinutes > 0
        let validGoal = switch goalType {
        case .repetitions: hasValidRepetitionGoal
        case .minutes: hasValidMinuteGoal
        case .repetitionsAndMinutes: hasValidRepetitionGoal && hasValidMinuteGoal
        }
        guard validGoal else {
            throw AssignmentValidationError.invalidGoal
        }
    }
}

public struct AssignmentHistory: Codable, Equatable, Identifiable, Sendable {
    public var id: String
    public var assignmentID: String
    public var revision: Int
    public var changedAtMillis: Int64
    public var previousAssignment: Assignment

    public init(
        id: String,
        assignmentID: String,
        revision: Int,
        changedAtMillis: Int64,
        previousAssignment: Assignment
    ) {
        self.id = id
        self.assignmentID = assignmentID
        self.revision = revision
        self.changedAtMillis = changedAtMillis
        self.previousAssignment = previousAssignment
    }
}

public struct DailyProgress: Codable, Equatable, Sendable {
    public var date: DateKey
    public var repetitionsByTaskID: [String: Int]
    public var secondsByTaskID: [String: Int]
    public var completedTaskIDs: Set<String>

    public init(
        date: DateKey,
        repetitionsByTaskID: [String: Int] = [:],
        secondsByTaskID: [String: Int] = [:],
        completedTaskIDs: Set<String> = []
    ) {
        self.date = date
        self.repetitionsByTaskID = repetitionsByTaskID
        self.secondsByTaskID = secondsByTaskID
        self.completedTaskIDs = completedTaskIDs
    }

    public mutating func recordRepetition(for assignmentID: String) {
        repetitionsByTaskID[assignmentID, default: 0] += 1
    }

    public mutating func addPracticeSeconds(_ seconds: Int, for assignmentID: String) {
        secondsByTaskID[assignmentID, default: 0] += max(0, seconds)
    }
}

public struct PracticeEvent: Codable, Equatable, Identifiable, Sendable {
    public var id: String
    public var childProfileID: String
    public var assignmentID: String
    public var date: DateKey
    public var atMillis: Int64
    public var message: String

    public init(
        id: String,
        childProfileID: String,
        assignmentID: String,
        date: DateKey,
        atMillis: Int64,
        message: String
    ) {
        self.id = id
        self.childProfileID = childProfileID
        self.assignmentID = assignmentID
        self.date = date
        self.atMillis = atMillis
        self.message = message
    }
}

public struct PracticeSession: Codable, Equatable, Identifiable, Sendable {
    public var id: String
    public var childProfileID: String
    public var assignmentID: String
    public var assignmentRevision: Int
    public var date: DateKey
    public var startedAtMillis: Int64
    public var endedAtMillis: Int64?
    public var elapsedSeconds: Int
    public var repetitions: Int
    public var goalType: PracticeGoalType
    public var targetRepetitions: Int
    public var targetMinutes: Int
    public var modesUsed: Set<PracticeMode>
    public var reflection: PracticeReflection?
    public var masteryStatus: MasteryStatus
    public var status: SessionStatus
    public var studentInitiated: Bool
    public var recordingPath: String?

    public init(
        id: String,
        childProfileID: String,
        assignmentID: String,
        assignmentRevision: Int,
        date: DateKey,
        startedAtMillis: Int64,
        endedAtMillis: Int64? = nil,
        elapsedSeconds: Int = 0,
        repetitions: Int = 0,
        goalType: PracticeGoalType,
        targetRepetitions: Int,
        targetMinutes: Int,
        modesUsed: Set<PracticeMode>,
        reflection: PracticeReflection? = nil,
        masteryStatus: MasteryStatus = .notAssessed,
        status: SessionStatus = .active,
        studentInitiated: Bool = false,
        recordingPath: String? = nil
    ) {
        self.id = id
        self.childProfileID = childProfileID
        self.assignmentID = assignmentID
        self.assignmentRevision = assignmentRevision
        self.date = date
        self.startedAtMillis = startedAtMillis
        self.endedAtMillis = endedAtMillis
        self.elapsedSeconds = elapsedSeconds
        self.repetitions = repetitions
        self.goalType = goalType
        self.targetRepetitions = targetRepetitions
        self.targetMinutes = targetMinutes
        self.modesUsed = modesUsed
        self.reflection = reflection
        self.masteryStatus = masteryStatus
        self.status = status
        self.studentInitiated = studentInitiated
        self.recordingPath = recordingPath
    }

    public var goalMet: Bool {
        Self.goalMet(
            goalType: goalType,
            repetitions: repetitions,
            elapsedSeconds: elapsedSeconds,
            targetRepetitions: targetRepetitions,
            targetMinutes: targetMinutes
        )
    }

    public static func goalMet(
        goalType: PracticeGoalType,
        repetitions: Int,
        elapsedSeconds: Int,
        targetRepetitions: Int,
        targetMinutes: Int
    ) -> Bool {
        let repetitionsMet = repetitions >= targetRepetitions
        let minutesMet = elapsedSeconds >= targetMinutes * 60
        return switch goalType {
        case .repetitions: repetitionsMet
        case .minutes: minutesMet
        case .repetitionsAndMinutes: repetitionsMet && minutesMet
        }
    }
}

public struct PracticeSnapshot: Codable, Equatable, Sendable {
    public var assignments: [Assignment]
    public var progressByDate: [String: DailyProgress]
    public var events: [PracticeEvent]
    public var sessions: [PracticeSession]
    public var assignmentHistory: [AssignmentHistory]

    public init(
        assignments: [Assignment] = [],
        progressByDate: [String: DailyProgress] = [:],
        events: [PracticeEvent] = [],
        sessions: [PracticeSession] = [],
        assignmentHistory: [AssignmentHistory] = []
    ) {
        self.assignments = assignments
        self.progressByDate = progressByDate
        self.events = events
        self.sessions = sessions
        self.assignmentHistory = assignmentHistory
    }

    public func progress(on date: DateKey) -> DailyProgress {
        progressByDate[date.value] ?? DailyProgress(date: date)
    }

    public mutating func setProgress(_ progress: DailyProgress) {
        progressByDate[progress.date.value] = progress
    }
}

public struct WeeklyPracticeDay: Equatable, Sendable {
    public var date: DateKey
    public var practiced: Bool
    public var seconds: Int
    public var repetitions: Int

    public init(date: DateKey, practiced: Bool, seconds: Int, repetitions: Int) {
        self.date = date
        self.practiced = practiced
        self.seconds = seconds
        self.repetitions = repetitions
    }
}
