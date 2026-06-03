import SwiftUI

// MARK: - QuickLogFAB
//
// 우측 하단 플로팅 액션 버튼.
// 기존 LogCTASection(전면 카드)을 대체해 기록 진입점을 FAB으로 강등.
//
// ## 메뉴 항목 범위 — 식단/운동만 노출, 신체 변화는 의도적 제외
//
// FAB은 "매일 반복되는 일일 로그"의 빠른 진입점으로 한정한다.
// - 포함: 식단 기록, 운동 기록 (하루 수 회 발생, 즉시성 중요)
// - 제외: 신체 측정(체중·둘레), 진행 사진 — 주간/이벤트성 기록이라
//   매일의 FAB 메뉴에 올리면 빈도 불균형으로 노이즈가 된다.
//
// 신체 변화 진입은 홈의 "신체 변화" 카드와 진행 사진 화면에서 별도로
// 제공한다. 새 항목을 추가하기 전에 이 분리 원칙을 먼저 점검할 것.

struct QuickLogFAB: View {
    @State private var isExpanded = false

    var body: some View {
        VStack(alignment: .trailing, spacing: 12) {
            // 확장 메뉴 (위쪽)
            if isExpanded {
                VStack(alignment: .trailing, spacing: 10) {
                    FABMenuItem(
                        label: String(localized: "home.fab.diet"),
                        icon: "fork.knife",
                        destination: AnyView(DietRecordView())
                    )
                    FABMenuItem(
                        label: String(localized: "home.fab.exercise"),
                        icon: "dumbbell.fill",
                        destination: AnyView(ExerciseRecordView())
                    )
                }
                .transition(.asymmetric(
                    insertion: .move(edge: .bottom).combined(with: .opacity),
                    removal: .opacity
                ))
            }

            // 메인 버튼
            Button {
                withAnimation(.spring(response: 0.4, dampingFraction: 0.75)) {
                    isExpanded.toggle()
                }
            } label: {
                ZStack {
                    Circle()
                        .fill(LinearGradient.sunrise)
                        .frame(width: 56, height: 56)
                        .shadow(color: Color.brandEmber.opacity(0.45), radius: 14, x: 0, y: 6)

                    Image(systemName: isExpanded ? "xmark" : "plus")
                        .font(.headingLarge).fontWeight(.heavy)
                        .foregroundStyle(.white)
                        .rotationEffect(.degrees(isExpanded ? 45 : 0))
                        .animation(.spring(response: 0.3, dampingFraction: 0.7), value: isExpanded)
                }
            }
        }
        .padding(.trailing, Spacing.xl) // design-lint:ignore — micro/hero spacing
        .padding(.bottom, Spacing.xl) // design-lint:ignore — micro/hero spacing
    }
}

// MARK: - FABMenuItem

private struct FABMenuItem: View {
    let label: String
    let icon: String
    let destination: AnyView

    var body: some View {
        NavigationLink(destination: destination) {
            HStack(spacing: 10) {
                Text(label)
                    .font(.labelSmall)
                    .foregroundStyle(Color.textHeadline)
                    .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
                    .padding(.vertical, 9) // design-lint:ignore — micro/hero spacing
                    .background(
                        Capsule()
                            .fill(Color.surfaceCard)
                            .shadow(color: Color.black.opacity(0.08), radius: 8, x: 0, y: 3)
                    )

                ZStack {
                    Circle()
                        .fill(Color.brandDusk)
                        .frame(width: 40, height: 40)
                    Image(systemName: icon)
                        .font(.bodyMedium).fontWeight(.bold)
                        .foregroundStyle(Color.brandAccentGlow)
                }
            }
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    ZStack(alignment: .bottomTrailing) {
        Color.brandBone.ignoresSafeArea()
        QuickLogFAB()
    }
}
