import Foundation
import Testing
@testable import LKPracticeCore

@Suite("Date keys")
struct DateKeyTests {
    @Test
    func validDateRoundTrips() throws {
        let date = try DateKey("2026-11-03")
        #expect(date.value == "2026-11-03")
        #expect(date.adding(days: 7).value == "2026-11-10")
    }

    @Test
    func invalidCalendarDateIsRejected() {
        #expect(throws: DateKeyError.self) {
            try DateKey("2026-02-30")
        }
        #expect(throws: DateKeyError.self) {
            try DateKey("11/03/2026")
        }
    }

    @Test
    func mondayOfWeekUsesMondayStart() throws {
        let tuesday = try DateKey("2026-11-03")
        #expect(tuesday.mondayOfWeek.value == "2026-11-02")
    }

    @Test
    func codableUsesAPlainDateString() throws {
        let date = try DateKey("2026-11-03")
        let data = try JSONEncoder().encode(date)
        #expect(String(decoding: data, as: UTF8.self) == "\"2026-11-03\"")
        #expect(try JSONDecoder().decode(DateKey.self, from: data) == date)
    }
}
