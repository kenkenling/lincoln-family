import Foundation
import LKPracticeCore

struct RecordingDestination: Equatable, Sendable {
    let reference: String
    let fileURL: URL
    let displayName: String
}

enum LocalRecordingStoreError: Error, LocalizedError {
    case invalidRecordingReference
    case emptyRecording

    var errorDescription: String? {
        switch self {
        case .invalidRecordingReference:
            return "LK Practice could not create a safe recording location."
        case .emptyRecording:
            return "The recording did not contain any video."
        }
    }
}

struct LocalRecordingStore {
    let rootURL: URL

    private static let referenceScheme = "lkpractice-recording"
    private static let referenceHost = "takes"

    init(rootURL: URL = LocalRecordingStore.defaultRootURL) {
        self.rootURL = rootURL
    }

    func makeDestination(
        profileID: String,
        assignmentTitle: String,
        date: DateKey,
        sessionID: String,
        now: Date = Date()
    ) throws -> RecordingDestination {
        let safeProfileID = safeComponent(profileID, fallback: "student")
        let safeSessionID = safeComponent(sessionID, fallback: UUID().uuidString.lowercased())
        let safeTitle = String(
            safeComponent(assignmentTitle, fallback: "practice")
                .prefix(48)
        )
        let timeLabel = Self.timeFormatter.string(from: now)
        let shortSessionID = String(safeSessionID.prefix(8))
        let displayName = "\(date.value)_\(timeLabel)_\(safeTitle)_\(shortSessionID).mov"
        let directory = rootURL
            .appendingPathComponent(safeProfileID, isDirectory: true)
            .appendingPathComponent(safeSessionID, isDirectory: true)
        let fileURL = directory.appendingPathComponent(displayName, isDirectory: false)

        try FileManager.default.createDirectory(
            at: directory,
            withIntermediateDirectories: true
        )

        var components = URLComponents()
        components.scheme = Self.referenceScheme
        components.host = Self.referenceHost
        components.path = "/\(safeProfileID)/\(safeSessionID)/\(displayName)"
        guard let reference = components.string else {
            throw LocalRecordingStoreError.invalidRecordingReference
        }

        return RecordingDestination(
            reference: reference,
            fileURL: fileURL,
            displayName: displayName
        )
    }

    func finalize(_ destination: RecordingDestination) throws {
        let values = try destination.fileURL.resourceValues(forKeys: [.fileSizeKey])
        guard values.fileSize ?? 0 > 0 else {
            try? FileManager.default.removeItem(at: destination.fileURL)
            throw LocalRecordingStoreError.emptyRecording
        }

        #if os(iOS)
        try? FileManager.default.setAttributes(
            [.protectionKey: FileProtectionType.complete],
            ofItemAtPath: destination.fileURL.path
        )
        #endif
    }

    func url(for reference: String?) -> URL? {
        guard
            let reference,
            let components = URLComponents(string: reference),
            components.scheme == Self.referenceScheme,
            components.host == Self.referenceHost
        else {
            return nil
        }

        let pathParts = components.path
            .split(separator: "/", omittingEmptySubsequences: true)
            .map(String.init)
        guard pathParts.count == 3 else {
            return nil
        }

        let candidate = rootURL
            .appendingPathComponent(pathParts[0], isDirectory: true)
            .appendingPathComponent(pathParts[1], isDirectory: true)
            .appendingPathComponent(pathParts[2], isDirectory: false)
            .standardizedFileURL
        let rootPath = rootURL.standardizedFileURL.path + "/"
        guard candidate.path.hasPrefix(rootPath) else {
            return nil
        }
        return candidate
    }

    func displayName(for reference: String?) -> String? {
        url(for: reference)?.lastPathComponent
    }

    func fileExists(for reference: String?) -> Bool {
        guard let fileURL = url(for: reference) else {
            return false
        }
        return FileManager.default.fileExists(atPath: fileURL.path)
    }

    func delete(_ reference: String?) {
        guard let fileURL = url(for: reference) else {
            return
        }
        try? FileManager.default.removeItem(at: fileURL)
        removeEmptyParents(startingAt: fileURL.deletingLastPathComponent())
    }

    func deleteProfile(_ profileID: String) {
        let safeProfileID = safeComponent(profileID, fallback: "student")
        let directory = rootURL.appendingPathComponent(safeProfileID, isDirectory: true)
        try? FileManager.default.removeItem(at: directory)
    }

    func prune(keeping references: Set<String>) {
        guard FileManager.default.fileExists(atPath: rootURL.path) else {
            return
        }
        let keptPaths = Set(
            references.compactMap { url(for: $0)?.standardizedFileURL.path }
        )
        guard let enumerator = FileManager.default.enumerator(
            at: rootURL,
            includingPropertiesForKeys: [.isRegularFileKey],
            options: [.skipsHiddenFiles]
        ) else {
            return
        }

        var parentDirectories = Set<URL>()
        for case let fileURL as URL in enumerator {
            let values = try? fileURL.resourceValues(forKeys: [.isRegularFileKey])
            guard values?.isRegularFile == true else {
                continue
            }
            if !keptPaths.contains(fileURL.standardizedFileURL.path) {
                try? FileManager.default.removeItem(at: fileURL)
                parentDirectories.insert(fileURL.deletingLastPathComponent())
            }
        }
        for directory in parentDirectories {
            removeEmptyParents(startingAt: directory)
        }
    }

    func deleteAll() {
        try? FileManager.default.removeItem(at: rootURL)
    }

    private func removeEmptyParents(startingAt directory: URL) {
        var candidate = directory.standardizedFileURL
        let root = rootURL.standardizedFileURL
        while candidate.path.hasPrefix(root.path + "/"), candidate != root {
            let contents = try? FileManager.default.contentsOfDirectory(
                at: candidate,
                includingPropertiesForKeys: nil
            )
            guard contents?.isEmpty == true else {
                return
            }
            try? FileManager.default.removeItem(at: candidate)
            candidate.deleteLastPathComponent()
        }
    }

    private func safeComponent(_ value: String, fallback: String) -> String {
        let allowed = CharacterSet.alphanumerics.union(
            CharacterSet(charactersIn: "-_")
        )
        let mapped = value.unicodeScalars.map {
            allowed.contains($0) ? Character(String($0)) : "-"
        }
        let cleaned = String(mapped)
            .replacingOccurrences(of: "--", with: "-")
            .trimmingCharacters(in: CharacterSet(charactersIn: "-_"))
        return cleaned.isEmpty ? fallback : cleaned
    }

    private static let timeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = .current
        formatter.dateFormat = "HH-mm-ss"
        return formatter
    }()

    private static var defaultRootURL: URL {
        FileManager.default.urls(
            for: .applicationSupportDirectory,
            in: .userDomainMask
        ).first!
            .appendingPathComponent("LKPractice", isDirectory: true)
            .appendingPathComponent("Recordings", isDirectory: true)
    }
}
