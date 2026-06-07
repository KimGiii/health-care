import SwiftUI

// MARK: - NotificationsView
//
// 인앱 알림 센터. 종 버튼 탭으로 진입.
// - 목록: 최신순, 무한 스크롤 (페이징)
// - 읽음: 알림 탭 시 자동 표시 + "모두 읽음" 버튼
// - 삭제: 스와이프 (또는 longPress 메뉴)
// - 라우팅: 알림 탭 → type별 적절한 화면으로 push (PushRouter와 같은 분기)

struct NotificationsView: View {
    @StateObject private var viewModel = NotificationsViewModel()
    @EnvironmentObject private var container: AppContainer
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        Group {
            if viewModel.isLoading && viewModel.items.isEmpty {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if viewModel.items.isEmpty {
                EmptyState(
                    icon: "bell.slash",
                    title: String(localized: "notifications.empty.title"),
                    message: String(localized: "notifications.empty.message")
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                list
            }
        }
        .background(Color.backgroundPage)
        .navigationTitle(Text("notifications.title"))
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            if viewModel.unreadCount > 0 {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(String(localized: "notifications.markAllRead")) {
                        Task { await viewModel.markAllRead(apiClient: container.apiClient) }
                    }
                    .font(.bodyMedium)
                    .foregroundStyle(Color.brandPrimary)
                }
            }
        }
        .alert(Text("오류"), isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button(String(localized: "common.ok"), role: .cancel) { viewModel.errorMessage = nil }
        } message: {
            Text(viewModel.errorMessage ?? "")
        }
        .task { await viewModel.loadFirstPage(apiClient: container.apiClient) }
        .refreshable {
            await viewModel.loadFirstPage(apiClient: container.apiClient)
        }
    }

    // MARK: - List

    private var list: some View {
        ScrollView {
            LazyVStack(spacing: Spacing.sm) {
                ForEach(viewModel.items) { item in
                    NotificationRow(item: item) {
                        Task { await handleTap(item) }
                    }
                    .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                        Button(role: .destructive) {
                            Task { await viewModel.delete(item, apiClient: container.apiClient) }
                        } label: {
                            Label(String(localized: "common.delete.button"), systemImage: "trash")
                        }
                    }
                    .contextMenu {
                        if !item.read {
                            Button {
                                Task { _ = await viewModel.markRead(item, apiClient: container.apiClient) }
                            } label: { Label(String(localized: "notifications.action.markRead"), systemImage: "envelope.open") }
                        }
                        Button(role: .destructive) {
                            Task { await viewModel.delete(item, apiClient: container.apiClient) }
                        } label: { Label(String(localized: "common.delete.button"), systemImage: "trash") }
                    }
                    .onAppear {
                        // 마지막 아이템 보일 때 다음 페이지 로드
                        if item == viewModel.items.last, viewModel.hasNext {
                            Task { await viewModel.loadMore(apiClient: container.apiClient) }
                        }
                    }
                }

                if viewModel.isLoading && !viewModel.items.isEmpty {
                    ProgressView().padding(.vertical, Spacing.lg) // design-lint:ignore — list footer
                }
            }
            .padding(.horizontal, Spacing.lg) // design-lint:ignore — list gutter
            .padding(.vertical, Spacing.md)   // design-lint:ignore — list gutter
        }
    }

    // MARK: - Tap handler
    //
    // 알림 탭 = 읽음 처리 + type별 화면 라우팅.
    // PushRouter와 동일한 분기 정책을 사용 (현재는 WEEKLY_SUMMARY만 라우팅).

    private func handleTap(_ item: NotificationItem) async {
        _ = await viewModel.markRead(item, apiClient: container.apiClient)
        guard let kind = item.kind else { return }
        switch kind {
        case .weeklySummary:
            // dismiss하고 PushRouter 흐름 재사용 — MainTabView가 receiver
            dismiss()
            PushRouter.shared.deliver(type: kind.rawValue)
        case .dailyLogReminder,
             .mealBreakfastReminder,
             .mealLunchReminder:
            // 리마인더 류는 라우팅 없이 읽음 처리만 — 어디로 가야 할지 사용자가 결정.
            // 향후 식단/기록 화면 deep link 필요해지면 PushRouter case 추가.
            break
        }
    }
}

// MARK: - Row

private struct NotificationRow: View {
    let item: NotificationItem
    let onTap: () -> Void

    private static let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = LocaleManager.resolvedLocale
        f.dateFormat = String(localized: "notifications.date.format")
        f.locale = LocaleManager.resolvedLocale
        return f
    }()

    private var iconName: String {
        switch item.kind {
        case .weeklySummary:         return "chart.bar.doc.horizontal"
        case .dailyLogReminder:      return "pencil.and.list.clipboard"
        case .mealBreakfastReminder: return "sun.max.fill"
        case .mealLunchReminder:     return "fork.knife"
        case nil:                    return "bell"
        }
    }

    var body: some View {
        Button(action: onTap) {
            HStack(alignment: .top, spacing: Spacing.md) {
                ZStack(alignment: .topTrailing) {
                    Image(systemName: iconName)
                        .font(.bodyLarge).fontWeight(.semibold)
                        .foregroundStyle(Color.brandPrimary)
                        .frame(width: 40, height: 40)
                        .background(Color.brandPrimary.opacity(0.10))
                        .clipShape(Circle())
                    if !item.read {
                        Circle()
                            .fill(Color.brandDanger)
                            .frame(width: 9, height: 9)
                            .overlay(Circle().stroke(Color.surfaceCard, lineWidth: 1.5))
                            .offset(x: 2, y: -2)
                    }
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text(item.title)
                        .font(.bodyMedium).fontWeight(item.read ? .semibold : .bold)
                        .foregroundStyle(Color.textPrimary)
                        .multilineTextAlignment(.leading)
                    Text(item.body)
                        .font(.bodySmall)
                        .foregroundStyle(Color.textSecondary)
                        .multilineTextAlignment(.leading)
                        .lineLimit(3)
                    Text(Self.dateFormatter.string(from: item.sentAt))
                        .font(.captionXSmall)
                        .foregroundStyle(Color.textTertiary)
                        .padding(.top, Spacing.xs) // design-lint:ignore — micro/hero spacing
                }
                Spacer(minLength: 0)
            }
            .padding(Spacing.lg) // design-lint:ignore — card padding
            .background(
                RoundedRectangle(cornerRadius: Radius.lg, style: .continuous)
                    .fill(Color.surfaceCard)
                    .overlay(
                        RoundedRectangle(cornerRadius: Radius.lg, style: .continuous)
                            .stroke(Color.cardStroke, lineWidth: 1)
                    )
            )
        }
        .buttonStyle(.plain)
    }
}
