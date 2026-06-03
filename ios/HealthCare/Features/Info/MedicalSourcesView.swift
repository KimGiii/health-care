import SwiftUI

struct MedicalSourcesView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    introCard

                    sourceSection(
                        title: String(localized: "medical.bmi.title"),
                        body: String(localized: "medical.bmi.body"),
                        sources: [
                            SourceLink(
                                label: "WHO – Body mass index (BMI)",
                                url: "https://www.who.int/data/gho/data/themes/topics/topic-details/GHO/body-mass-index"
                            ),
                            SourceLink(
                                label: "WHO – Obesity and overweight",
                                url: "https://www.who.int/news-room/fact-sheets/detail/obesity-and-overweight"
                            )
                        ]
                    )

                    sourceSection(
                        title: String(localized: "medical.bmiClass.title"),
                        body: String(localized: "medical.bmiClass.body"),
                        sources: [
                            SourceLink(
                                label: String(localized: "medical.bmi.link.who"),
                                url: "https://www.who.int/europe/news-room/fact-sheets/item/a-healthy-lifestyle---who-recommendations"
                            ),
                            SourceLink(
                                label: String(localized: "medical.bmi.link.kso"),
                                url: "https://general.kosso.or.kr/html/?pmode=obesityDisease"
                            )
                        ]
                    )

                    sourceSection(
                        title: String(localized: "medical.nutrition.title"),
                        body: String(localized: "medical.nutrition.body"),
                        sources: [
                            SourceLink(
                                label: String(localized: "medical.nutrition.link.mfds"),
                                url: "https://various.foodsafetykorea.go.kr/nutrient/"
                            ),
                            SourceLink(
                                label: "USDA FoodData Central",
                                url: "https://fdc.nal.usda.gov/"
                            )
                        ]
                    )

                    sourceSection(
                        title: String(localized: "medical.exercise.title"),
                        body: String(localized: "medical.exercise.body"),
                        sources: [
                            SourceLink(
                                label: "WHO – Physical activity guidelines",
                                url: "https://www.who.int/news-room/fact-sheets/detail/physical-activity"
                            )
                        ]
                    )

                    disclaimerCard

                    Spacer(minLength: 24)
                }
                .padding(Spacing.xl) // design-lint:ignore — micro/hero spacing
            }
            .background(Color.backgroundPage)
            .navigationTitle(Text("medical.title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(String(localized: "common.close")) { dismiss() }
                        .foregroundStyle(Color.brandPrimary)
                }
            }
        }
    }

    private var introCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "book.closed.fill")
                    .foregroundStyle(Color.brandPrimary)
                Text(String(localized: "medical.section.notice"))
                    .font(.bodyLarge).fontWeight(.bold)
                    .foregroundStyle(Color.textPrimary)
            }
            Text(String(localized: "medical.intro"))
                .font(.bodySmall)
                .foregroundStyle(Color.textSecondary)
                .lineSpacing(3)
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .shadow(color: .black.opacity(0.04), radius: 4, x: 0, y: 2)
    }

    private var disclaimerCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(Color(hex: "#D97706"))
                Text(String(localized: "medical.disclaimer.title"))
                    .font(.headingSmall).fontWeight(.bold)
                    .foregroundStyle(Color(hex: "#78350F"))
            }
            Text(String(localized: "medical.disclaimer.body"))
                .font(.bodySmall)
                .foregroundStyle(Color(hex: "#92400E"))
                .lineSpacing(3)
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(hex: "#FEF3C7"))
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
    }

    private func sourceSection(title: String, body: String, sources: [SourceLink]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.headingSmall)
                .foregroundStyle(Color.textPrimary)
            Text(body)
                .font(.bodySmall)
                .foregroundStyle(Color.textSecondary)
                .lineSpacing(3)
                .fixedSize(horizontal: false, vertical: true)
            VStack(alignment: .leading, spacing: 6) {
                ForEach(sources) { source in
                    if let url = URL(string: source.url) {
                        Link(destination: url) {
                            HStack(spacing: 6) {
                                Image(systemName: "link")
                                    .font(.captionXSmall)
                                Text(source.label)
                                    .font(.bodySmall).fontWeight(.medium)
                                    .underline()
                                Spacer(minLength: 0)
                            }
                            .foregroundStyle(Color.brandPrimary)
                        }
                    }
                }
            }
            .padding(.top, 2) // design-lint:ignore — micro/hero spacing
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .shadow(color: .black.opacity(0.04), radius: 4, x: 0, y: 2)
    }
}

private struct SourceLink: Identifiable {
    let id = UUID()
    let label: String
    let url: String
}
