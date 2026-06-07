import Foundation

// MARK: - NotificationItem
//
// 백엔드 NotificationResponse DTO와 1:1 매칭.
// type은 NotificationType.* enum으로 매핑돼 푸시 라우팅과 같은 분기에 사용.

struct NotificationItem: Decodable, Identifiable, Equatable, Hashable {
    let id: Int
    let type: String
    let title: String
    let body: String
    let sentAt: Date
    let read: Bool
    let readAt: Date?
}

extension NotificationItem {
    /// PushRouter / handlePushRoute에서 사용하는 같은 type 문자열.
    enum Kind: String {
        case weeklySummary          = "WEEKLY_SUMMARY"
        case dailyLogReminder       = "DAILY_LOG_REMINDER"
        case mealBreakfastReminder  = "MEAL_BREAKFAST_REMINDER"
        case mealLunchReminder      = "MEAL_LUNCH_REMINDER"
    }

    var kind: Kind? { Kind(rawValue: type) }
}

// MARK: - Page<NotificationItem>
//
// Spring Data Page<T> 직렬화 형태와 매핑. 클라이언트는 content/totalElements만 사용.

struct NotificationPage: Decodable {
    let content: [NotificationItem]
    let totalElements: Int
    let totalPages: Int
    let number: Int
    let size: Int

    /// 다음 페이지가 남았는지 (totalPages 기준).
    var hasNext: Bool { number + 1 < totalPages }
}

// MARK: - Helpers

struct NotificationUnreadCountResponse: Decodable {
    let count: Int
}

struct NotificationMarkAllReadResponse: Decodable {
    let updated: Int
}
