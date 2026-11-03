import Foundation

public enum DateKeyError: Error, Equatable, LocalizedError {
    case invalidFormat(String)

    public var errorDescription: String? {
        switch self {
        case let .invalidFormat(value):
            return "Invalid date key '\(value)'. Expected YYYY-MM-DD."
        }
    }
}

public struct DateKey: Codable, Hashable, Comparable, Sendable, CustomStringConvertible {
    public let value: String

    public init(_ value: String) throws {
        guard Self.date(from: value) != nil else {
            throw DateKeyError.invalidFormat(value)
        }
        self.value = value
    }

    public init(date: Date, calendar: Calendar = DateKey.localCalendar) {
        let components = calendar.dateComponents([.year, .month, .day], from: date)
        self.value = String(
            format: "%04d-%02d-%02d",
            components.year ?? 0,
            components.month ?? 0,
            components.day ?? 0
        )
    }

    public static func today(
        now: Date = Date(),
        calendar: Calendar = DateKey.localCalendar
    ) -> DateKey {
        DateKey(date: now, calendar: calendar)
    }

    public var description: String {
        value
    }

    public var date: Date {
        // Construction validates the value, so this cannot fail.
        Self.date(from: value)!
    }

    public func date(in calendar: Calendar = DateKey.localCalendar) -> Date {
        let parts = value.split(separator: "-")
        var components = DateComponents()
        components.calendar = calendar
        components.timeZone = calendar.timeZone
        components.year = Int(parts[0])
        components.month = Int(parts[1])
        components.day = Int(parts[2])
        return calendar.date(from: components)!
    }

    public func adding(days: Int) -> DateKey {
        let date = Self.utcCalendar.date(byAdding: .day, value: days, to: date)!
        return DateKey(date: date, calendar: Self.utcCalendar)
    }

    public func days(to other: DateKey) -> Int {
        Self.utcCalendar.dateComponents([.day], from: date, to: other.date).day ?? 0
    }

    public var mondayOfWeek: DateKey {
        let weekday = Self.utcCalendar.component(.weekday, from: date)
        let daysSinceMonday = (weekday + 5) % 7
        return adding(days: -daysSinceMonday)
    }

    public static func < (lhs: DateKey, rhs: DateKey) -> Bool {
        lhs.value < rhs.value
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        try self.init(container.decode(String.self))
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(value)
    }

    public static var utcCalendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.locale = Locale(identifier: "en_US_POSIX")
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar
    }

    public static var localCalendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.locale = Locale(identifier: "en_US_POSIX")
        calendar.timeZone = .current
        return calendar
    }

    private static func date(from value: String) -> Date? {
        guard value.count == 10 else {
            return nil
        }

        let pieces = value.split(separator: "-", omittingEmptySubsequences: false)
        guard
            pieces.count == 3,
            pieces[0].count == 4,
            pieces[1].count == 2,
            pieces[2].count == 2,
            let year = Int(pieces[0]),
            let month = Int(pieces[1]),
            let day = Int(pieces[2])
        else {
            return nil
        }

        var components = DateComponents()
        components.calendar = utcCalendar
        components.timeZone = utcCalendar.timeZone
        components.year = year
        components.month = month
        components.day = day

        guard let date = utcCalendar.date(from: components) else {
            return nil
        }

        let roundTrip = utcCalendar.dateComponents([.year, .month, .day], from: date)
        guard
            roundTrip.year == year,
            roundTrip.month == month,
            roundTrip.day == day
        else {
            return nil
        }
        return date
    }
}
