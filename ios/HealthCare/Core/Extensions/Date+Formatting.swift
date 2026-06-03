import Foundation

extension Array {
    func uniqued<T: Hashable>(by keyPath: KeyPath<Element, T>) -> [Element] {
        var seen = Set<T>()
        return filter { seen.insert($0[keyPath: keyPath]).inserted }
    }
}

extension Date {
    func formatted(_ format: String) -> String {
        let formatter = DateFormatter()
        formatter.locale = LocaleManager.resolvedLocale
        formatter.dateFormat = format
        return formatter.string(from: self)
    }

    var apiDateString: String { formatted(Constants.DateFormat.apiDate) }
    var displayDateString: String { formatted(Constants.DateFormat.displayDate) }
    var displayFullString: String { formatted(Constants.DateFormat.displayFull) }

    var isToday: Bool { Calendar.current.isDateInToday(self) }
    var isYesterday: Bool { Calendar.current.isDateInYesterday(self) }
}
