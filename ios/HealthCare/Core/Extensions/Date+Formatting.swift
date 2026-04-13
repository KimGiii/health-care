import Foundation

extension Date {
    func formatted(_ format: String) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.dateFormat = format
        return formatter.string(from: self)
    }

    var apiDateString: String { formatted(Constants.DateFormat.apiDate) }
    var displayDateString: String { formatted(Constants.DateFormat.displayDate) }
    var displayFullString: String { formatted(Constants.DateFormat.displayFull) }

    var isToday: Bool { Calendar.current.isDateInToday(self) }
    var isYesterday: Bool { Calendar.current.isDateInYesterday(self) }
}
