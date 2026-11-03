import Testing
@testable import LKPracticeCore

@Suite("Assignment date ranges")
struct AssignmentDatesTests {
    @Test
    func singleDayAssignmentIsValid() throws {
        let date = try DateKey("2026-11-03")
        #expect(AssignmentDates.isValid(from: date, to: date))
        #expect(AssignmentDates.allDates(from: date, to: date) == [date])
    }

    @Test
    func sevenDayAssignmentContainsEveryDate() throws {
        let start = try DateKey("2026-11-02")
        let end = try DateKey("2026-11-08")
        let dates = AssignmentDates.allDates(from: start, to: end)

        #expect(dates.count == 7)
        #expect(dates.first?.value == "2026-11-02")
        #expect(dates.last?.value == "2026-11-08")
    }

    @Test
    func rangesLongerThanThirtyDaysAreRejected() throws {
        let start = try DateKey("2026-11-01")
        let end = start.adding(days: 30)
        #expect(!AssignmentDates.isValid(from: start, to: end))
        #expect(AssignmentDates.allDates(from: start, to: end).isEmpty)
    }
}
