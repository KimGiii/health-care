import SwiftUI

/// 화면/큰 영역에서 표시하는 표준 빈 상태.
///
/// 사용:
/// ```
/// EmptyState(
///     icon: "fork.knife.circle",
///     title: "오늘 식단 기록이 아직 없어요",
///     message: "오늘 먹은 음식을 기록해 보세요\n영양 목표 달성을 도와드려요",
///     action: .init(label: "식단 기록하기") { vm.startNewLog() }
/// )
/// ```
///
/// 카피 규칙(`docs/COPY.md` §4):
/// - 제목: `{대상} 기록이 아직 없어요` 패턴
/// - CTA: 가능하면 항상 제공 (다음 행동 명시)
/// - 메시지: 1~2줄, 왜 비어있는지가 아니라 무엇을 할 수 있는지
struct EmptyState: View {
    let icon: String
    let title: String
    let message: String?
    let action: Action?

    init(
        icon: String,
        title: String,
        message: String? = nil,
        action: Action? = nil
    ) {
        self.icon = icon
        self.title = title
        self.message = message
        self.action = action
    }

    struct Action {
        let label: String
        let perform: () -> Void
    }

    var body: some View {
        VStack(spacing: Spacing.lg) {
            Image(systemName: icon)
                .font(.system(size: 48)) // design-lint:ignore — SF Symbol size
                .foregroundStyle(Color.textSecondary.opacity(0.5))

            VStack(spacing: Spacing.sm) {
                Text(title)
                    .font(.headingSmall)
                    .foregroundStyle(Color.textPrimary)
                    .multilineTextAlignment(.center)

                if let message {
                    Text(message)
                        .font(.bodySmall)
                        .foregroundStyle(Color.textSecondary)
                        .multilineTextAlignment(.center)
                        .lineSpacing(4)
                }
            }

            if let action {
                Button(action: action.perform) {
                    Label(action.label, systemImage: "plus")
                        .font(.cta)
                        .foregroundStyle(.white)
                        .padding(.horizontal, Spacing.xxl)
                        .padding(.vertical, Spacing.md)
                        .background(Color.brandPrimary)
                        .clipShape(Capsule())
                }
                .padding(.top, Spacing.xs)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Spacing.xxl)
    }
}

#Preview {
    VStack(spacing: 40) {
        EmptyState(
            icon: "fork.knife.circle",
            title: "오늘 식단 기록이 아직 없어요",
            message: "오늘 먹은 음식을 기록해 보세요\n영양 목표 달성을 도와드려요",
            action: .init(label: "식단 기록하기") {}
        )
        Divider()
        EmptyState(
            icon: "chart.bar.doc.horizontal",
            title: "이 주에는 기록이 아직 없어요"
        )
    }
    .background(Color.backgroundPage)
}
