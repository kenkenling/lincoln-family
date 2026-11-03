import Foundation

public enum AssignmentDates {
    public static let maximumRangeDays = 30

    public static func inclusiveDayCount(from startDate: DateKey, to endDate: DateKey) -> Int {
        startDate.days(to: endDate) + 1
    }

    public static func isValid(from startDate: DateKey, to endDate: DateKey) -> Bool {
        let count = inclusiveDayCount(from: startDate, to: endDate)
        return count >= 1 && count <= maximumRangeDays
    }

    public static func contains(_ date: DateKey, from startDate: DateKey, to endDate: DateKey) -> Bool {
        date >= startDate && date <= endDate
    }

    public static func allDates(from startDate: DateKey, to endDate: DateKey) -> [DateKey] {
        guard isValid(from: startDate, to: endDate) else {
            return []
        }
        return (0..<inclusiveDayCount(from: startDate, to: endDate)).map(startDate.adding(days:))
    }
}
