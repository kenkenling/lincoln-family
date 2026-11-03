import Foundation

public struct PracticeArchive: Codable, Equatable, Sendable {
    public static let currentSchemaVersion = 1

    public var schemaVersion: Int
    public var household: Household
    public var snapshotsByProfileID: [String: PracticeSnapshot]

    public init(
        schemaVersion: Int = PracticeArchive.currentSchemaVersion,
        household: Household,
        snapshotsByProfileID: [String: PracticeSnapshot]
    ) {
        self.schemaVersion = schemaVersion
        self.household = household
        self.snapshotsByProfileID = snapshotsByProfileID
    }
}

public enum LocalPracticeArchiveError: Error, LocalizedError {
    case unsupportedSchemaVersion(Int)

    public var errorDescription: String? {
        switch self {
        case let .unsupportedSchemaVersion(version):
            return "This practice file uses unsupported schema version \(version)."
        }
    }
}

/// Local-only persistence for the first Apple milestone. The store writes one
/// private app-container archive and never uploads it. A SwiftData adapter can
/// replace this implementation without changing the practice engine API.
public actor LocalPracticeArchiveStore {
    public let fileURL: URL

    public init(fileURL: URL) {
        self.fileURL = fileURL
    }

    public func load() throws -> PracticeArchive? {
        guard FileManager.default.fileExists(atPath: fileURL.path) else {
            return nil
        }
        let data = try Data(contentsOf: fileURL)
        let archive = try JSONDecoder().decode(PracticeArchive.self, from: data)
        guard archive.schemaVersion <= PracticeArchive.currentSchemaVersion else {
            throw LocalPracticeArchiveError.unsupportedSchemaVersion(archive.schemaVersion)
        }
        return archive
    }

    public func save(_ archive: PracticeArchive) throws {
        let directory = fileURL.deletingLastPathComponent()
        try FileManager.default.createDirectory(
            at: directory,
            withIntermediateDirectories: true
        )
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
        let data = try encoder.encode(archive)
        try data.write(to: fileURL, options: [.atomic, .completeFileProtection])
    }

    public func delete() throws {
        guard FileManager.default.fileExists(atPath: fileURL.path) else {
            return
        }
        try FileManager.default.removeItem(at: fileURL)
    }
}
