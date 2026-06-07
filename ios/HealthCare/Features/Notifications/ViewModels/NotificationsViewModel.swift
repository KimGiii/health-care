import Foundation

@MainActor
final class NotificationsViewModel: ObservableObject {
    @Published private(set) var items: [NotificationItem] = []
    @Published private(set) var isLoading = false
    @Published private(set) var hasNext = false
    @Published var errorMessage: String?

    private var nextPage = 0
    private let pageSize = 30

    // MARK: - Load

    func loadFirstPage(apiClient: APIClient) async {
        nextPage = 0
        items = []
        hasNext = false
        await loadMore(apiClient: apiClient)
    }

    func loadMore(apiClient: APIClient) async {
        guard !isLoading else { return }
        isLoading = true
        defer { isLoading = false }
        do {
            let page: NotificationPage = try await apiClient.request(
                .getNotifications(page: nextPage, size: pageSize)
            )
            items.append(contentsOf: page.content)
            hasNext = page.hasNext
            nextPage = page.number + 1
        } catch {
            errorMessage = friendlyMessage(error)
        }
    }

    // MARK: - Read / Delete

    /// 단건 읽음 처리 후 라우팅 type 반환 (호출자가 화면 이동에 사용).
    @discardableResult
    func markRead(_ item: NotificationItem, apiClient: APIClient) async -> String? {
        // 낙관적 업데이트 — 즉시 read=true로 갱신, 실패 시 롤백.
        guard let idx = items.firstIndex(where: { $0.id == item.id }) else { return item.type }
        if !items[idx].read {
            items[idx] = NotificationItem(
                id: item.id, type: item.type, title: item.title, body: item.body,
                sentAt: item.sentAt, read: true, readAt: Date()
            )
            do {
                let _: NotificationItem = try await apiClient.request(.markNotificationRead(id: item.id))
            } catch {
                // 롤백
                items[idx] = item
                errorMessage = friendlyMessage(error)
                return nil
            }
        }
        return item.type
    }

    func markAllRead(apiClient: APIClient) async {
        let backup = items
        // 낙관적 업데이트
        items = items.map {
            $0.read ? $0 : NotificationItem(
                id: $0.id, type: $0.type, title: $0.title, body: $0.body,
                sentAt: $0.sentAt, read: true, readAt: Date()
            )
        }
        do {
            let _: NotificationMarkAllReadResponse = try await apiClient.request(.markAllNotificationsRead)
        } catch {
            items = backup
            errorMessage = friendlyMessage(error)
        }
    }

    func delete(_ item: NotificationItem, apiClient: APIClient) async {
        let backup = items
        items.removeAll { $0.id == item.id }
        do {
            try await apiClient.requestVoid(.deleteNotification(id: item.id))
        } catch {
            items = backup
            errorMessage = friendlyMessage(error)
        }
    }

    // MARK: - Helpers

    var unreadCount: Int { items.filter { !$0.read }.count }

    private func friendlyMessage(_ error: Error) -> String {
        if let apiError = error as? APIError,
           case .serverError(_, _, let message) = apiError,
           let message {
            return message
        }
        return String(localized: "notifications.error.load")
    }
}
