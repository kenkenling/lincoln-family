import Foundation
import Combine
import LKPracticeCore

enum MainArea: String, CaseIterable, Identifiable {
    case today
    case parent

    var id: String { rawValue }
}

enum ParentSection: String, CaseIterable, Identifiable {
    case assignments
    case summary
    case profiles
    case privacy

    var id: String { rawValue }

    var title: String {
        switch self {
        case .assignments: "Assignments"
        case .summary: "Summary"
        case .profiles: "Profiles"
        case .privacy: "Privacy & Data"
        }
    }

    var systemImage: String {
        switch self {
        case .assignments: "music.note.list"
        case .summary: "chart.bar.xaxis"
        case .profiles: "person.2.fill"
        case .privacy: "hand.raised.fill"
        }
    }
}

struct AssignmentDraft: Equatable {
    var editingID: String?
    var title = ""
    var details = ""
    var type: AssignmentType = .technique
    var startDate: DateKey
    var endDate: DateKey
    var goalType: PracticeGoalType = .repetitions
    var targetRepetitions = 3
    var targetMinutes = 5
    var enabledModes: Set<PracticeMode> = [.playSolo]
    var sourceType: SourceType = .none
    var referenceURL: String?
    var referenceStartSeconds = 0
    var sheetMusicReference: String?

    static func new(on date: DateKey) -> AssignmentDraft {
        AssignmentDraft(startDate: date, endDate: date)
    }

    init(
        editingID: String? = nil,
        title: String = "",
        details: String = "",
        type: AssignmentType = .technique,
        startDate: DateKey,
        endDate: DateKey,
        goalType: PracticeGoalType = .repetitions,
        targetRepetitions: Int = 3,
        targetMinutes: Int = 5,
        enabledModes: Set<PracticeMode> = [.playSolo],
        sourceType: SourceType = .none,
        referenceURL: String? = nil,
        referenceStartSeconds: Int = 0,
        sheetMusicReference: String? = nil
    ) {
        self.editingID = editingID
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
    }

    init(assignment: Assignment) {
        editingID = assignment.id
        title = assignment.title
        details = assignment.details
        type = assignment.type
        startDate = assignment.startDate
        endDate = assignment.endDate
        goalType = assignment.goalType
        targetRepetitions = assignment.targetRepetitions
        targetMinutes = assignment.targetMinutes
        enabledModes = assignment.enabledModes
        sourceType = assignment.sourceType
        referenceURL = assignment.referenceURL
        referenceStartSeconds = assignment.referenceStartSeconds
        sheetMusicReference = assignment.sheetMusicReference
    }

    var hasValidReferenceVideo: Bool {
        switch sourceType {
        case .none:
            return true
        case .youtube:
            return referenceURL.flatMap(YouTubeReference.videoID(from:)) != nil
        case .localVideo:
            return !(referenceURL ?? "")
                .trimmingCharacters(in: .whitespacesAndNewlines)
                .isEmpty
        }
    }
}

@MainActor
final class AppModel: ObservableObject {
    @Published private(set) var consentAccepted: Bool
    @Published private(set) var isLoading: Bool
    @Published private(set) var engine: PracticeEngine?
    @Published private(set) var stateRevision = 0
    @Published var mainArea: MainArea = .today
    @Published var parentSection: ParentSection = .assignments
    @Published var showingParentGate = false
    @Published private(set) var parentUnlocked = false
    @Published private(set) var activeAssignmentID: String?
    @Published private(set) var practiceStartedAt: Date?
    @Published private(set) var accumulatedPracticeSeconds = 0
    @Published private(set) var practicePaused = false
    @Published private(set) var pendingReflectionSessionID: String?
    @Published var errorMessage: String?

    private let consentStore: PrivacyConsentStore
    private let pinStore: ParentPINStore
    private let archiveStore: LocalPracticeArchiveStore
    private let mediaStore: LocalMediaStore
    private let recordingStore: LocalRecordingStore
    private var persistenceTask: Task<Void, Never>?

    init(
        consentStore: PrivacyConsentStore = PrivacyConsentStore(),
        pinStore: ParentPINStore = ParentPINStore(),
        archiveStore: LocalPracticeArchiveStore? = nil,
        mediaStore: LocalMediaStore? = nil,
        recordingStore: LocalRecordingStore? = nil
    ) {
        self.consentStore = consentStore
        self.pinStore = pinStore
        self.archiveStore = archiveStore ?? LocalPracticeArchiveStore(
            fileURL: Self.defaultArchiveURL
        )
        self.mediaStore = mediaStore ?? LocalMediaStore()
        self.recordingStore = recordingStore ?? LocalRecordingStore()
        let accepted = consentStore.hasAcceptedCurrentPolicy
        consentAccepted = accepted
        isLoading = accepted

        if accepted {
            Task { await loadArchive() }
        }
    }

    var household: Household? {
        engine?.household
    }

    var activeProfile: ChildProfile? {
        engine?.activeProfile
    }

    var profiles: [ChildProfile] {
        household?.childProfiles ?? []
    }

    var currentDate: DateKey {
        engine?.currentDate ?? .today()
    }

    var todayAssignments: [Assignment] {
        (try? engine?.todayAssignments()) ?? []
    }

    var allAssignments: [Assignment] {
        engine?.activeSnapshot?.assignments.sorted {
            if $0.isArchived != $1.isArchived {
                return !$0.isArchived
            }
            if $0.startDate != $1.startDate {
                return $0.startDate > $1.startDate
            }
            return $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending
        } ?? []
    }

    var selfDirectedAssignments: [Assignment] {
        Array(
            allAssignments
                .filter { !$0.isArchived }
                .sorted {
                    if $0.updatedAtMillis != $1.updatedAtMillis {
                        return $0.updatedAtMillis > $1.updatedAtMillis
                    }
                    return $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending
                }
                .prefix(5)
        )
    }

    var allTodayAssignmentsComplete: Bool {
        !todayAssignments.isEmpty && todayAssignments.allSatisfy { goalMet(for: $0) }
    }

    var sessions: [PracticeSession] {
        engine?.activeSnapshot?.sessions.sorted {
            if $0.date != $1.date {
                return $0.date > $1.date
            }
            return $0.startedAtMillis > $1.startedAtMillis
        } ?? []
    }

    var assignmentHistory: [AssignmentHistory] {
        engine?.activeSnapshot?.assignmentHistory.sorted {
            $0.changedAtMillis > $1.changedAtMillis
        } ?? []
    }

    var weeklyPractice: [WeeklyPracticeDay] {
        (try? engine?.weeklyPractice()) ?? []
    }

    var activeAssignment: Assignment? {
        guard let activeAssignmentID else {
            return nil
        }
        return engine?.activeSnapshot?.assignments.first { $0.id == activeAssignmentID }
    }

    var activeSession: PracticeSession? {
        engine?.activeSession
    }

    var pinConfigured: Bool {
        pinStore.isConfigured
    }

    var daysSinceLastPractice: Int? {
        let priorDates = sessions
            .filter { $0.status != .active && $0.date <= currentDate }
            .map(\.date)
        guard let latest = priorDates.max() else {
            return nil
        }
        return latest.days(to: currentDate)
    }

    var greeting: String {
        let name = activeProfile?.name ?? "musician"
        guard let daysSinceLastPractice else {
            return "Hi, \(name)!"
        }
        switch daysSinceLastPractice {
        case 0:
            return "Hi, \(name)!"
        case 1:
            return "Welcome back, \(name)!"
        case 2...6:
            return "Welcome back! Let’s pick up where you left off."
        case 7...:
            return "It’s great to see you again! Ready for some music?"
        default:
            return "Hi, \(name)!"
        }
    }

    func acceptPrivacy(ageRange: StudentAgeRange) {
        consentStore.accept(ageRange: ageRange)
        consentAccepted = true
        engine = PracticeEngine(
            household: Household(id: UUID().uuidString.lowercased()),
            currentDate: .today()
        )
        isLoading = false
        refreshAndPersist(immediately: true)
    }

    func addProfile(name: String, instrument: String) -> Bool {
        guard let engine else {
            return false
        }
        do {
            try engine.addProfile(name: name, instrument: instrument)
            refreshAndPersist(immediately: true)
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }

    func updateProfile(_ profile: ChildProfile) -> Bool {
        guard let engine else {
            return false
        }
        do {
            try engine.updateProfile(profile)
            refreshAndPersist()
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }

    func selectProfile(_ profileID: String) {
        do {
            try engine?.selectProfile(profileID)
            mainArea = .today
            parentUnlocked = false
            refreshAndPersist()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func deleteProfile(_ profileID: String) {
        do {
            try engine?.deleteProfile(profileID)
            recordingStore.deleteProfile(profileID)
            refreshAndPersist(immediately: true)
            cleanupUnreferencedMedia()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func importReferenceVideo(from sourceURL: URL) async -> String? {
        do {
            let imported = try await mediaStore.importReferenceVideo(from: sourceURL)
            return imported.reference
        } catch {
            errorMessage = error.localizedDescription
            return nil
        }
    }

    func importSheetMusic(from sourceURL: URL) async -> String? {
        do {
            let imported = try await mediaStore.importSheetMusic(from: sourceURL)
            return imported.reference
        } catch {
            errorMessage = error.localizedDescription
            return nil
        }
    }

    func localMediaURL(for reference: String?) -> URL? {
        mediaStore.url(for: reference)
    }

    func localMediaDisplayName(for reference: String?) -> String {
        mediaStore.displayName(for: reference) ?? "Attached file"
    }

    func localMediaExists(for reference: String?) -> Bool {
        mediaStore.fileExists(for: reference)
    }

    func makeRecordingDestination() -> RecordingDestination? {
        guard
            let profile = activeProfile,
            let assignment = activeAssignment,
            let session = activeSession
        else {
            errorMessage = "Start a practice session before recording a take."
            return nil
        }

        do {
            return try recordingStore.makeDestination(
                profileID: profile.id,
                assignmentTitle: assignment.title,
                date: session.date,
                sessionID: session.id
            )
        } catch {
            errorMessage = error.localizedDescription
            return nil
        }
    }

    func recordingSaved(_ destination: RecordingDestination) {
        do {
            try recordingStore.finalize(destination)
            guard let engine else {
                recordingStore.delete(destination.reference)
                return
            }
            let previousReference = try engine.attachRecording(
                path: destination.reference
            )
            if previousReference != destination.reference {
                recordingStore.delete(previousReference)
            }
            refreshAndPersist(immediately: true)
        } catch {
            recordingStore.delete(destination.reference)
            errorMessage = "The practice take could not be saved: \(error.localizedDescription)"
        }
    }

    func recordingFailed(_ destination: RecordingDestination) {
        recordingStore.delete(destination.reference)
    }

    func recordingURL(for reference: String?) -> URL? {
        recordingStore.url(for: reference)
    }

    func recordingDisplayName(for reference: String?) -> String {
        recordingStore.displayName(for: reference) ?? "Practice take"
    }

    func recordingExists(for reference: String?) -> Bool {
        recordingStore.fileExists(for: reference)
    }

    func cleanupUnreferencedMedia() {
        let references = managedMediaReferences()
        Task {
            try? await mediaStore.prune(keeping: references)
        }
    }

    func saveAssignment(_ draft: AssignmentDraft) -> Bool {
        guard
            let engine,
            let profile = activeProfile
        else {
            return false
        }
        let now = Int64(Date().timeIntervalSince1970 * 1_000)
        let existing = draft.editingID.flatMap { id in
            engine.activeSnapshot?.assignments.first { $0.id == id }
        }
        let cleanReference = draft.referenceURL?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let savedReference = draft.sourceType == .none ||
            cleanReference?.isEmpty != false
            ? nil
            : cleanReference

        do {
            let assignment = try Assignment(
                id: existing?.id ?? UUID().uuidString.lowercased(),
                childProfileID: profile.id,
                title: draft.title.trimmingCharacters(in: .whitespacesAndNewlines),
                details: draft.details.trimmingCharacters(in: .whitespacesAndNewlines),
                type: draft.type,
                startDate: draft.startDate,
                endDate: draft.endDate,
                goalType: draft.goalType,
                targetRepetitions: draft.targetRepetitions,
                targetMinutes: draft.targetMinutes,
                enabledModes: draft.enabledModes,
                sourceType: draft.sourceType,
                referenceURL: savedReference,
                referenceStartSeconds: max(0, draft.referenceStartSeconds),
                sheetMusicReference: draft.sheetMusicReference,
                teacherID: existing?.teacherID,
                revision: existing?.revision ?? 1,
                archivedAtMillis: existing?.archivedAtMillis,
                createdAtMillis: existing?.createdAtMillis ?? now,
                updatedAtMillis: now
            )
            try engine.addAssignment(assignment)
            refreshAndPersist()
            cleanupUnreferencedMedia()
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }

    func archiveAssignment(_ assignmentID: String) {
        do {
            try engine?.archiveAssignment(assignmentID)
            refreshAndPersist()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func restoreAssignment(_ assignmentID: String) {
        do {
            try engine?.restoreAssignment(assignmentID)
            refreshAndPersist()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func progress(for assignment: Assignment, on date: DateKey? = nil) -> DailyProgress {
        engine?.activeSnapshot?.progress(on: date ?? currentDate) ??
            DailyProgress(date: date ?? currentDate)
    }

    func goalMet(for assignment: Assignment, on date: DateKey? = nil) -> Bool {
        let progress = progress(for: assignment, on: date)
        let repetitions = progress.repetitionsByTaskID[assignment.id, default: 0]
        let elapsed = progress.secondsByTaskID[assignment.id, default: 0]
        return PracticeSession.goalMet(
            goalType: assignment.goalType,
            repetitions: repetitions,
            elapsedSeconds: elapsed,
            targetRepetitions: assignment.targetRepetitions,
            targetMinutes: assignment.targetMinutes
        )
    }

    func startPractice(
        _ assignment: Assignment,
        mode: PracticeMode,
        studentInitiated: Bool = false
    ) {
        do {
            _ = try engine?.startPractice(
                assignmentID: assignment.id,
                mode: mode,
                studentInitiated: studentInitiated
            )
            activeAssignmentID = assignment.id
            practiceStartedAt = Date()
            accumulatedPracticeSeconds = 0
            practicePaused = false
            refreshAndPersist()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func switchMode(_ mode: PracticeMode) {
        do {
            try engine?.switchMode(to: mode)
            refreshAndPersist()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func recordRepetition() {
        do {
            try engine?.recordRepetition()
            refreshAndPersist()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func elapsedPracticeSeconds(at now: Date = Date()) -> Int {
        guard !practicePaused, let practiceStartedAt else {
            return accumulatedPracticeSeconds
        }
        return accumulatedPracticeSeconds +
            max(0, Int(now.timeIntervalSince(practiceStartedAt)))
    }

    func pausePractice() {
        guard !practicePaused, practiceStartedAt != nil else {
            return
        }
        accumulatedPracticeSeconds = elapsedPracticeSeconds()
        practiceStartedAt = nil
        practicePaused = true
    }

    func resumePractice() {
        guard practicePaused, activeAssignmentID != nil else {
            return
        }
        practiceStartedAt = Date()
        practicePaused = false
    }

    func finishPractice() {
        do {
            let session = try engine?.finishPractice(
                elapsedSeconds: elapsedPracticeSeconds()
            )
            pendingReflectionSessionID = session?.id
            activeAssignmentID = nil
            practiceStartedAt = nil
            accumulatedPracticeSeconds = 0
            practicePaused = false
            refreshAndPersist(immediately: true)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func submitReflection(
        reflection: PracticeReflection,
        studentFeelsConfident: Bool
    ) {
        guard let sessionID = pendingReflectionSessionID else {
            return
        }
        do {
            try engine?.submitReflection(
                sessionID: sessionID,
                reflection: reflection,
                studentFeelsConfident: studentFeelsConfident
            )
            pendingReflectionSessionID = nil
            refreshAndPersist()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func skipReflection() {
        pendingReflectionSessionID = nil
    }

    func openParentSpace() {
        if parentUnlocked {
            mainArea = .parent
        } else {
            showingParentGate = true
        }
    }

    func createParentPIN(_ pin: String, confirmation: String) -> Bool {
        guard pin == confirmation else {
            errorMessage = "The two PIN entries do not match."
            return false
        }
        do {
            try pinStore.setPIN(pin)
            parentUnlocked = true
            showingParentGate = false
            mainArea = .parent
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }

    func unlockParentSpace(_ pin: String) -> Bool {
        guard pinStore.verify(pin) else {
            errorMessage = "That PIN did not unlock Parent Space."
            return false
        }
        parentUnlocked = true
        showingParentGate = false
        mainArea = .parent
        return true
    }

    func lockParentSpace() {
        parentUnlocked = false
        mainArea = .today
    }

    func deleteAllData() async {
        persistenceTask?.cancel()
        do {
            try await archiveStore.delete()
            try await mediaStore.deleteAll()
            recordingStore.deleteAll()
            consentStore.deleteAll()
            try pinStore.delete()
            engine = nil
            consentAccepted = false
            parentUnlocked = false
            mainArea = .today
            pendingReflectionSessionID = nil
            activeAssignmentID = nil
            stateRevision += 1
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func handleAppBecameInactive() {
        pausePractice()
        persist(immediately: true)
    }

    private func loadArchive() async {
        do {
            if let archive = try await archiveStore.load() {
                engine = PracticeEngine(
                    household: archive.household,
                    snapshotsByProfileID: archive.snapshotsByProfileID,
                    currentDate: .today()
                )
            } else {
                engine = PracticeEngine(
                    household: Household(id: UUID().uuidString.lowercased()),
                    currentDate: .today()
                )
                persist(immediately: true)
            }
        } catch {
            errorMessage = "Your local practice data could not be loaded: \(error.localizedDescription)"
            engine = PracticeEngine(
                household: Household(id: UUID().uuidString.lowercased()),
                currentDate: .today()
            )
        }
        try? await mediaStore.prune(keeping: managedMediaReferences())
        recordingStore.prune(keeping: managedRecordingReferences())
        isLoading = false
        stateRevision += 1
    }

    private func refreshAndPersist(immediately: Bool = false) {
        stateRevision += 1
        persist(immediately: immediately)
    }

    private func persist(immediately: Bool) {
        guard let engine else {
            return
        }
        let archive = PracticeArchive(
            household: engine.household,
            snapshotsByProfileID: engine.snapshotsByProfileID
        )
        persistenceTask?.cancel()
        persistenceTask = Task {
            if !immediately {
                try? await Task.sleep(for: .milliseconds(120))
            }
            guard !Task.isCancelled else {
                return
            }
            do {
                try await archiveStore.save(archive)
            } catch {
                errorMessage = "Practice changes could not be saved: \(error.localizedDescription)"
            }
        }
    }

    private func managedMediaReferences() -> Set<String> {
        guard let engine else {
            return []
        }
        var references = Set<String>()
        for snapshot in engine.snapshotsByProfileID.values {
            for assignment in snapshot.assignments {
                if let referenceURL = assignment.referenceURL {
                    references.insert(referenceURL)
                }
                if let sheetMusicReference = assignment.sheetMusicReference {
                    references.insert(sheetMusicReference)
                }
            }
            for history in snapshot.assignmentHistory {
                if let referenceURL = history.previousAssignment.referenceURL {
                    references.insert(referenceURL)
                }
                if let sheetMusicReference = history.previousAssignment.sheetMusicReference {
                    references.insert(sheetMusicReference)
                }
            }
        }
        return references
    }

    private func managedRecordingReferences() -> Set<String> {
        guard let engine else {
            return []
        }
        return Set(
            engine.snapshotsByProfileID.values.flatMap { snapshot in
                snapshot.sessions.compactMap(\.recordingPath)
            }
        )
    }

    private static var defaultArchiveURL: URL {
        let applicationSupport = FileManager.default.urls(
            for: .applicationSupportDirectory,
            in: .userDomainMask
        ).first!
        return applicationSupport
            .appendingPathComponent("LKPractice", isDirectory: true)
            .appendingPathComponent("practice-archive.json")
    }
}
