import CryptoKit
import Foundation
import Security

enum StudentAgeRange: String, CaseIterable, Identifiable {
    case under13
    case age13To17
    case adult

    var id: String { rawValue }

    var title: String {
        switch self {
        case .under13: "Under 13"
        case .age13To17: "13–17"
        case .adult: "18 or older"
        }
    }
}

struct PrivacyPolicySection: Identifiable {
    let id: String
    let title: String
    let body: String
}

enum ApplePrivacyPolicy {
    static let version = "2026-11-03-v1"

    static let sections = [
        PrivacyPolicySection(
            id: "local-data",
            title: "Data stays on this device",
            body: "LK Practice stores student profiles, assignments, practice progress, reflections, and attached files in the app’s private storage. This first Apple version has no LK Practice account or cloud synchronization."
        ),
        PrivacyPolicySection(
            id: "no-tracking",
            title: "No advertising or tracking",
            body: "The app does not include advertising, analytics, or tracking SDKs. It does not sell personal information."
        ),
        PrivacyPolicySection(
            id: "parent-space",
            title: "Parent Space",
            body: "Assignments, profiles, summaries, exports, sharing, and destructive actions belong in Parent Space behind a local four-digit PIN."
        ),
        PrivacyPolicySection(
            id: "media",
            title: "Recordings and sheet music",
            body: "Media support will use files selected or recorded by the family. Recordings remain local unless a parent deliberately shares them."
        ),
        PrivacyPolicySection(
            id: "delete",
            title: "Delete all data",
            body: "A parent can erase consent, profiles, assignments, practice history, local settings, and app-managed files from Parent Space."
        ),
    ]
}

struct PrivacyConsentStore {
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    var hasAcceptedCurrentPolicy: Bool {
        defaults.string(forKey: Keys.policyVersion) == ApplePrivacyPolicy.version &&
            defaults.bool(forKey: Keys.parentConfirmed)
    }

    var acceptedAgeRange: StudentAgeRange? {
        defaults.string(forKey: Keys.ageRange).flatMap(StudentAgeRange.init(rawValue:))
    }

    func accept(ageRange: StudentAgeRange) {
        defaults.set(ApplePrivacyPolicy.version, forKey: Keys.policyVersion)
        defaults.set(ageRange.rawValue, forKey: Keys.ageRange)
        defaults.set(true, forKey: Keys.parentConfirmed)
    }

    func deleteAll() {
        defaults.removeObject(forKey: Keys.policyVersion)
        defaults.removeObject(forKey: Keys.ageRange)
        defaults.removeObject(forKey: Keys.parentConfirmed)
    }

    private enum Keys {
        static let policyVersion = "privacy.policy.version"
        static let ageRange = "privacy.student.age.range"
        static let parentConfirmed = "privacy.parent.confirmed"
    }
}

enum ParentPINError: Error, LocalizedError {
    case invalidPIN
    case randomGenerationFailed
    case keychain(OSStatus)

    var errorDescription: String? {
        switch self {
        case .invalidPIN:
            return "Use exactly four digits."
        case .randomGenerationFailed:
            return "The secure PIN salt could not be created."
        case let .keychain(status):
            return "The Parent Space PIN could not be saved securely (\(status))."
        }
    }
}

struct ParentPINStore {
    private let service = "com.lkeducation.lkpractice"
    private let account = "parent-pin-v1"

    var isConfigured: Bool {
        (try? storedValue()) != nil
    }

    func setPIN(_ pin: String) throws {
        guard Self.isValid(pin) else {
            throw ParentPINError.invalidPIN
        }

        let saltCount = 16
        var salt = Data(count: saltCount)
        let status = salt.withUnsafeMutableBytes { buffer -> OSStatus in
            guard let address = buffer.baseAddress else {
                return errSecParam
            }
            return SecRandomCopyBytes(kSecRandomDefault, saltCount, address)
        }
        guard status == errSecSuccess else {
            throw ParentPINError.randomGenerationFailed
        }

        var stored = Data([1])
        stored.append(salt)
        stored.append(Self.digest(pin: pin, salt: salt))
        try upsert(stored)
    }

    func verify(_ pin: String) -> Bool {
        guard
            Self.isValid(pin),
            let stored = try? storedValue(),
            stored.count == 49,
            stored.first == 1
        else {
            return false
        }

        let salt = stored.subdata(in: 1..<17)
        let expected = stored.subdata(in: 17..<49)
        return Self.constantTimeEqual(expected, Self.digest(pin: pin, salt: salt))
    }

    func delete() throws {
        let status = SecItemDelete(baseQuery as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw ParentPINError.keychain(status)
        }
    }

    static func isValid(_ pin: String) -> Bool {
        pin.count == 4 && pin.allSatisfy(\.isNumber)
    }

    private var baseQuery: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }

    private func storedValue() throws -> Data? {
        var query = baseQuery
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound {
            return nil
        }
        guard status == errSecSuccess else {
            throw ParentPINError.keychain(status)
        }
        return result as? Data
    }

    private func upsert(_ value: Data) throws {
        let updateStatus = SecItemUpdate(
            baseQuery as CFDictionary,
            [kSecValueData as String: value] as CFDictionary
        )
        if updateStatus == errSecSuccess {
            return
        }
        guard updateStatus == errSecItemNotFound else {
            throw ParentPINError.keychain(updateStatus)
        }

        var addQuery = baseQuery
        addQuery[kSecValueData as String] = value
        addQuery[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        let addStatus = SecItemAdd(addQuery as CFDictionary, nil)
        guard addStatus == errSecSuccess else {
            throw ParentPINError.keychain(addStatus)
        }
    }

    private static func digest(pin: String, salt: Data) -> Data {
        var material = salt
        material.append(contentsOf: pin.utf8)
        return Data(SHA256.hash(data: material))
    }

    private static func constantTimeEqual(_ lhs: Data, _ rhs: Data) -> Bool {
        guard lhs.count == rhs.count else {
            return false
        }
        return zip(lhs, rhs).reduce(UInt8(0)) { result, pair in
            result | (pair.0 ^ pair.1)
        } == 0
    }
}
