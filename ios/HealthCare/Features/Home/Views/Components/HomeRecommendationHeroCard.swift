import SwiftUI

// MARK: - Home Recommendation Hero Card (#94)
//
// 차별화 자산(맞춤 식단 추천)을 홈 fold 위로 끌어올린다. 대시보드와 분리된
// 논블로킹 프리페치(HomeViewModel.loadRecommendationPreview)의 상태를 4가지로 렌더한다:
//   1) 프로필 없음 → 설정 유도 CTA
//   2) 로딩 중     → 스켈레톤
//   3) 프리뷰 있음 → 다음 끼니 미리보기(끼니·칼로리·식품·단백질·알러지 제외)
//   4) 실패/후보없음 → 추천받기 CTA로 degrade
// 카드 전체가 HomeDestination.recommendation으로 향하는 진입점이다.

struct HomeRecommendationHeroCard: View {
    let preview: HomeRecommendationPreview?
    let isLoading: Bool
    let unavailable: Bool
    let hasProfile: Bool

    var body: some View {
        NavigationLink(value: HomeDestination.recommendation) {
            content
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
                .background(Color.surfaceCard)
                .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
                .shadow(color: .black.opacity(0.05), radius: 8, x: 0, y: 2)
        }
        .buttonStyle(.plain)
        .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel(accessibilityLabel)
    }

    // MARK: - Content by state

    @ViewBuilder
    private var content: some View {
        if !hasProfile {
            ctaBody(
                title: String(localized: "home.reco.setup.title", defaultValue: "오늘 뭘 먹을지 정해드려요"),
                subtitle: String(localized: "home.reco.setup.subtitle", defaultValue: "키와 몸무게를 알려주시면 오늘 식단을 맞춰 드려요"),
                cta: String(localized: "home.reco.setup.cta", defaultValue: "설정하고 추천받기")
            )
        } else if let preview {
            previewBody(preview)
        } else if isLoading {
            loadingBody
        } else {
            // unavailable 또는 초기 상태 — 추천받기 CTA로 degrade
            ctaBody(
                title: String(localized: "home.reco.cta.title", defaultValue: "오늘 뭘 먹을지 추천받기"),
                subtitle: String(localized: "home.reco.cta.subtitle", defaultValue: "지금 눌러 오늘 식단을 받아보세요"),
                cta: String(localized: "home.reco.cta.action", defaultValue: "추천받기")
            )
        }
    }

    // MARK: - Preview body

    private func previewBody(_ preview: HomeRecommendationPreview) -> some View {
        let meal = preview.featuredMeal
        return VStack(alignment: .leading, spacing: 8) {
            eyebrow(meal.mealType.displayName)

            Text(String(
                localized: "home.reco.preview.headline",
                defaultValue: "약 \(Int(meal.totalCalories.rounded())) kcal에 맞췄어요"
            ))
            .font(.headingSmall).fontWeight(.bold)
            .foregroundStyle(Color.textPrimary)

            Text(foodPreviewText(meal))
                .font(.bodySmall)
                .foregroundStyle(Color.textSecondary)
                .lineLimit(1)

            Text(metaText(meal, restrictionCount: preview.restrictionCount))
                .font(.labelSmall)
                .foregroundStyle(Color.textTertiary)

            ctaChip(String(localized: "home.reco.preview.cta", defaultValue: "이 식단 보기"))
                .padding(.top, 4) // design-lint:ignore — micro/hero spacing
        }
    }

    // MARK: - CTA body

    private func ctaBody(title: String, subtitle: String, cta: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            eyebrow(String(localized: "home.reco.eyebrow", defaultValue: "오늘의 추천"))
            Text(title)
                .font(.headingSmall).fontWeight(.bold)
                .foregroundStyle(Color.textPrimary)
            Text(subtitle)
                .font(.bodySmall)
                .foregroundStyle(Color.textSecondary)
            ctaChip(cta)
                .padding(.top, 4) // design-lint:ignore — micro/hero spacing
        }
    }

    // MARK: - Loading body

    private var loadingBody: some View {
        VStack(alignment: .leading, spacing: 10) {
            eyebrow(String(localized: "home.reco.eyebrow", defaultValue: "오늘의 추천"))
            skeletonLine(width: 0.7)
            skeletonLine(width: 0.9)
            skeletonLine(width: 0.5)
        }
        .redacted(reason: .placeholder)
    }

    // MARK: - Pieces

    private func eyebrow(_ text: String) -> some View {
        HStack(spacing: 6) {
            Image(systemName: "fork.knife")
                .font(.captionBold)
                .foregroundStyle(Color.brandPrimary)
            Text(text)
                .font(.labelSmall)
                .foregroundStyle(Color.brandPrimary)
                .textCase(.uppercase)
                .tracking(0.5)
        }
    }

    private func ctaChip(_ text: String) -> some View {
        HStack(spacing: 4) {
            Text(text)
                .font(.bodySmall).fontWeight(.semibold)
            Image(systemName: "chevron.right")
                .font(.captionBold)
        }
        .foregroundStyle(Color.brandPrimary)
    }

    private func skeletonLine(width: CGFloat) -> some View {
        RoundedRectangle(cornerRadius: Radius.sm)
            .fill(Color.textTertiary.opacity(0.25))
            .frame(height: 12) // design-lint:ignore — micro/hero spacing
            .frame(maxWidth: .infinity, alignment: .leading)
            .scaleEffect(x: width, anchor: .leading)
    }

    // MARK: - Text builders

    private func foodPreviewText(_ meal: RecommendedMeal) -> String {
        meal.items.prefix(3).map(\.displayName).joined(separator: " · ")
    }

    private func metaText(_ meal: RecommendedMeal, restrictionCount: Int) -> String {
        let protein = String(
            localized: "home.reco.preview.protein",
            defaultValue: "단백질 \(Int(meal.totalProteinG.rounded()))g"
        )
        guard restrictionCount > 0 else { return protein }
        let excluded = String(
            localized: "home.reco.preview.excluded",
            defaultValue: "알러지 \(restrictionCount)건 제외"
        )
        return "\(protein) · \(excluded)"
    }

    private var accessibilityLabel: String {
        if !hasProfile {
            return String(localized: "home.reco.a11y.setup", defaultValue: "오늘의 추천. 프로필을 설정하면 식단을 맞춰 드려요. 이중 탭하면 열려요.")
        }
        if let preview {
            let meal = preview.featuredMeal
            return String(
                localized: "home.reco.a11y.preview",
                defaultValue: "오늘의 추천 식단. \(meal.mealType.displayName), 약 \(Int(meal.totalCalories.rounded()))킬로칼로리, 단백질 \(Int(meal.totalProteinG.rounded()))그램. 이중 탭하면 열려요."
            )
        }
        return String(localized: "home.reco.a11y.cta", defaultValue: "오늘의 추천 식단을 받아보세요. 이중 탭하면 열려요.")
    }
}
