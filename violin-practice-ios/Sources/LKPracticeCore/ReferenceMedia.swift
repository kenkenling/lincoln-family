import Foundation

public enum YouTubeReference {
    public static func videoID(from value: String) -> String? {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            return nil
        }
        if isVideoID(trimmed) {
            return trimmed
        }

        let normalized = trimmed.contains("://") ? trimmed : "https://\(trimmed)"
        guard
            let components = URLComponents(string: normalized),
            let rawHost = components.host?.lowercased()
        else {
            return nil
        }

        let host = rawHost.hasPrefix("www.") ? String(rawHost.dropFirst(4)) : rawHost
        let pathParts = components.path
            .split(separator: "/", omittingEmptySubsequences: true)
            .map(String.init)

        let candidate: String?
        switch host {
        case "youtu.be":
            candidate = pathParts.first
        case "youtube.com", "m.youtube.com", "music.youtube.com":
            if components.path == "/watch" {
                candidate = components.queryItems?.first(where: { $0.name == "v" })?.value
            } else if
                let first = pathParts.first,
                ["embed", "shorts", "live"].contains(first),
                pathParts.count > 1
            {
                candidate = pathParts[1]
            } else {
                candidate = nil
            }
        case "youtube-nocookie.com":
            candidate = pathParts.first == "embed" && pathParts.count > 1
                ? pathParts[1]
                : nil
        default:
            candidate = nil
        }

        guard let candidate else {
            return nil
        }
        let cleanID = candidate
            .split(whereSeparator: { $0 == "?" || $0 == "&" || $0 == "#" })
            .first
            .map(String.init) ?? ""
        return isVideoID(cleanID) ? cleanID : nil
    }

    private static func isVideoID(_ value: String) -> Bool {
        guard value.count == 11 else {
            return false
        }
        return value.unicodeScalars.allSatisfy {
            CharacterSet.alphanumerics.contains($0) || $0 == "_" || $0 == "-"
        }
    }
}
