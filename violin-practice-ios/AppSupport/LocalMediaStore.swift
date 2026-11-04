import AVFoundation
import Foundation
import PDFKit
import UniformTypeIdentifiers

#if canImport(UIKit)
import UIKit
#elseif canImport(AppKit)
import AppKit
#endif

enum LocalMediaKind: String, CaseIterable, Sendable {
    case referenceVideo = "reference-videos"
    case sheetMusic = "sheet-music"
}

struct ImportedLocalMedia: Sendable {
    let reference: String
    let displayName: String
}

enum LocalMediaStoreError: Error, LocalizedError {
    case unsupportedVideo
    case unsupportedSheetMusic
    case fileTooLarge(String)
    case emptyFile
    case unreadableVideo
    case unreadablePDF
    case lockedPDF
    case unreadableImage
    case invalidManagedReference

    var errorDescription: String? {
        switch self {
        case .unsupportedVideo:
            return "Choose a readable movie file such as MP4, MOV, or M4V."
        case .unsupportedSheetMusic:
            return "Choose a PDF, PNG, JPG, HEIC, or WebP sheet-music file."
        case let .fileTooLarge(limit):
            return "This file is too large. Choose a file \(limit) or smaller."
        case .emptyFile:
            return "The selected file is empty."
        case .unreadableVideo:
            return "This video could not be played by LK Practice."
        case .unreadablePDF:
            return "This PDF could not be read or contains no pages."
        case .lockedPDF:
            return "Password-protected sheet-music PDFs are not supported."
        case .unreadableImage:
            return "This sheet-music image could not be read."
        case .invalidManagedReference:
            return "LK Practice could not create a safe private attachment reference."
        }
    }
}

actor LocalMediaStore {
    nonisolated let rootURL: URL

    private static let referenceScheme = "lkpractice-media"
    private static let maximumVideoBytes: Int64 = 750 * 1_024 * 1_024
    private static let maximumSheetMusicBytes: Int64 = 50 * 1_024 * 1_024

    init(rootURL: URL = LocalMediaStore.defaultRootURL) {
        self.rootURL = rootURL
    }

    func importReferenceVideo(from sourceURL: URL) async throws -> ImportedLocalMedia {
        let values = try sourceURL.resourceValues(
            forKeys: [.contentTypeKey, .fileSizeKey, .nameKey]
        )
        let contentType = values.contentType ??
            UTType(filenameExtension: sourceURL.pathExtension)
        guard contentType?.conforms(to: .movie) == true else {
            throw LocalMediaStoreError.unsupportedVideo
        }

        let imported = try copyIntoPrivateStorage(
            from: sourceURL,
            values: values,
            kind: .referenceVideo,
            maximumBytes: Self.maximumVideoBytes,
            sizeDescription: "750 MB"
        )
        do {
            let asset = AVURLAsset(url: imported.fileURL)
            guard try await asset.load(.isPlayable) else {
                throw LocalMediaStoreError.unreadableVideo
            }
            return ImportedLocalMedia(
                reference: imported.reference,
                displayName: imported.displayName
            )
        } catch {
            try? FileManager.default.removeItem(
                at: imported.fileURL.deletingLastPathComponent()
            )
            if error is LocalMediaStoreError {
                throw error
            }
            throw LocalMediaStoreError.unreadableVideo
        }
    }

    func importSheetMusic(from sourceURL: URL) throws -> ImportedLocalMedia {
        let values = try sourceURL.resourceValues(
            forKeys: [.contentTypeKey, .fileSizeKey, .nameKey]
        )
        let contentType = values.contentType ??
            UTType(filenameExtension: sourceURL.pathExtension)
        let isPDF = contentType?.conforms(to: .pdf) == true
        let isImage = contentType?.conforms(to: .image) == true
        guard isPDF || isImage else {
            throw LocalMediaStoreError.unsupportedSheetMusic
        }

        let imported = try copyIntoPrivateStorage(
            from: sourceURL,
            values: values,
            kind: .sheetMusic,
            maximumBytes: Self.maximumSheetMusicBytes,
            sizeDescription: "50 MB"
        )
        do {
            if isPDF {
                try validatePDF(at: imported.fileURL)
            } else {
                try validateImage(at: imported.fileURL)
            }
            return ImportedLocalMedia(
                reference: imported.reference,
                displayName: imported.displayName
            )
        } catch {
            try? FileManager.default.removeItem(
                at: imported.fileURL.deletingLastPathComponent()
            )
            throw error
        }
    }

    nonisolated func url(for reference: String?) -> URL? {
        guard
            let reference,
            let components = URLComponents(string: reference),
            components.scheme == Self.referenceScheme,
            let host = components.host,
            LocalMediaKind(rawValue: host) != nil
        else {
            return nil
        }

        let pathParts = components.path
            .split(separator: "/", omittingEmptySubsequences: true)
            .map(String.init)
        guard pathParts.count == 2 else {
            return nil
        }

        let candidate = rootURL
            .appendingPathComponent(host, isDirectory: true)
            .appendingPathComponent(pathParts[0], isDirectory: true)
            .appendingPathComponent(pathParts[1], isDirectory: false)
            .standardizedFileURL
        let rootPath = rootURL.standardizedFileURL.path + "/"
        guard candidate.path.hasPrefix(rootPath) else {
            return nil
        }
        return candidate
    }

    nonisolated func displayName(for reference: String?) -> String? {
        url(for: reference)?.lastPathComponent
    }

    nonisolated func fileExists(for reference: String?) -> Bool {
        guard let fileURL = url(for: reference) else {
            return false
        }
        return FileManager.default.fileExists(atPath: fileURL.path)
    }

    func prune(keeping references: Set<String>) throws {
        guard FileManager.default.fileExists(atPath: rootURL.path) else {
            return
        }
        let keptPaths = Set(
            references.compactMap { url(for: $0)?.standardizedFileURL.path }
        )

        for kind in LocalMediaKind.allCases {
            let kindDirectory = rootURL.appendingPathComponent(kind.rawValue, isDirectory: true)
            guard FileManager.default.fileExists(atPath: kindDirectory.path) else {
                continue
            }
            let attachmentDirectories = try FileManager.default.contentsOfDirectory(
                at: kindDirectory,
                includingPropertiesForKeys: [.isDirectoryKey],
                options: [.skipsHiddenFiles]
            )
            for directory in attachmentDirectories {
                let directoryPrefix = directory.standardizedFileURL.path + "/"
                if !keptPaths.contains(where: { $0.hasPrefix(directoryPrefix) }) {
                    try FileManager.default.removeItem(at: directory)
                }
            }
        }
    }

    func deleteAll() throws {
        guard FileManager.default.fileExists(atPath: rootURL.path) else {
            return
        }
        try FileManager.default.removeItem(at: rootURL)
    }

    private func copyIntoPrivateStorage(
        from sourceURL: URL,
        values: URLResourceValues,
        kind: LocalMediaKind,
        maximumBytes: Int64,
        sizeDescription: String
    ) throws -> (
        reference: String,
        displayName: String,
        fileURL: URL
    ) {
        if let fileSize = values.fileSize, Int64(fileSize) > maximumBytes {
            throw LocalMediaStoreError.fileTooLarge(sizeDescription)
        }

        let accessed = sourceURL.startAccessingSecurityScopedResource()
        defer {
            if accessed {
                sourceURL.stopAccessingSecurityScopedResource()
            }
        }

        let displayName = safeFileName(
            values.name ?? sourceURL.lastPathComponent,
            fallbackExtension: values.contentType?.preferredFilenameExtension
        )
        let attachmentID = UUID().uuidString.lowercased()
        let attachmentDirectory = rootURL
            .appendingPathComponent(kind.rawValue, isDirectory: true)
            .appendingPathComponent(attachmentID, isDirectory: true)
        let destinationURL = attachmentDirectory
            .appendingPathComponent(displayName, isDirectory: false)

        do {
            try FileManager.default.createDirectory(
                at: attachmentDirectory,
                withIntermediateDirectories: true
            )
            try FileManager.default.copyItem(at: sourceURL, to: destinationURL)
            let copiedSize = try destinationURL.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
            guard copiedSize > 0 else {
                throw LocalMediaStoreError.emptyFile
            }
            guard Int64(copiedSize) <= maximumBytes else {
                throw LocalMediaStoreError.fileTooLarge(sizeDescription)
            }

            #if os(iOS)
            try? FileManager.default.setAttributes(
                [.protectionKey: FileProtectionType.complete],
                ofItemAtPath: destinationURL.path
            )
            #endif

            var referenceComponents = URLComponents()
            referenceComponents.scheme = Self.referenceScheme
            referenceComponents.host = kind.rawValue
            referenceComponents.path = "/\(attachmentID)/\(displayName)"
            guard let reference = referenceComponents.string else {
                throw LocalMediaStoreError.invalidManagedReference
            }
            return (reference, displayName, destinationURL)
        } catch {
            try? FileManager.default.removeItem(at: attachmentDirectory)
            throw error
        }
    }

    private func validatePDF(at fileURL: URL) throws {
        guard let document = PDFDocument(url: fileURL) else {
            throw LocalMediaStoreError.unreadablePDF
        }
        guard !document.isLocked else {
            throw LocalMediaStoreError.lockedPDF
        }
        guard document.pageCount > 0 else {
            throw LocalMediaStoreError.unreadablePDF
        }
    }

    private func validateImage(at fileURL: URL) throws {
        #if canImport(UIKit)
        guard UIImage(contentsOfFile: fileURL.path) != nil else {
            throw LocalMediaStoreError.unreadableImage
        }
        #elseif canImport(AppKit)
        guard NSImage(contentsOf: fileURL) != nil else {
            throw LocalMediaStoreError.unreadableImage
        }
        #endif
    }

    private func safeFileName(
        _ proposedName: String,
        fallbackExtension: String?
    ) -> String {
        let rawName = proposedName.isEmpty ? "attachment" : proposedName
        let rawExtension = (rawName as NSString).pathExtension
        let baseName = (rawName as NSString).deletingPathExtension
        let safeBase = sanitizedComponent(baseName).prefix(90)
        let safeExtension = sanitizedComponent(
            rawExtension.isEmpty ? (fallbackExtension ?? "bin") : rawExtension
        )
        let finalExtension = safeExtension.isEmpty ? "bin" : safeExtension.lowercased()
        return "\(safeBase.isEmpty ? "attachment" : String(safeBase)).\(finalExtension)"
    }

    private func sanitizedComponent(_ value: String) -> String {
        let allowed = CharacterSet.alphanumerics.union(
            CharacterSet(charactersIn: "-_")
        )
        let mapped = value.unicodeScalars.map {
            allowed.contains($0) ? Character(String($0)) : "-"
        }
        return String(mapped)
            .replacingOccurrences(of: "--", with: "-")
            .trimmingCharacters(in: CharacterSet(charactersIn: "-_"))
    }

    private static var defaultRootURL: URL {
        FileManager.default.urls(
            for: .applicationSupportDirectory,
            in: .userDomainMask
        ).first!
            .appendingPathComponent("LKPractice", isDirectory: true)
            .appendingPathComponent("Media", isDirectory: true)
    }
}
