import SwiftUI

struct MedicalSourcesView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    introCard

                    sourceSection(
                        title: "BMI(체질량지수) 계산식",
                        body: "BMI = 체중(kg) ÷ 키(m)²",
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
                        title: "BMI 분류 기준 (성인)",
                        body: "본 앱은 WHO 국제 기준을 사용합니다.\n• 저체중: < 18.5\n• 정상: 18.5–24.9\n• 과체중: 25.0–29.9\n• 비만: ≥ 30.0\n\n한국인은 대한비만학회 기준(정상 18.5–22.9, 과체중 23.0–24.9, 비만 ≥ 25.0)도 함께 참고하실 수 있습니다.",
                        sources: [
                            SourceLink(
                                label: "WHO – BMI 분류",
                                url: "https://www.who.int/europe/news-room/fact-sheets/item/a-healthy-lifestyle---who-recommendations"
                            ),
                            SourceLink(
                                label: "대한비만학회 진료지침",
                                url: "https://general.kosso.or.kr/html/?pmode=obesityDisease"
                            )
                        ]
                    )

                    sourceSection(
                        title: "영양 성분 정보 (칼로리/단백질/탄수화물/지방)",
                        body: "본 앱의 식품 영양 성분 데이터는 다음 공공 데이터베이스를 참고하여 제공됩니다.",
                        sources: [
                            SourceLink(
                                label: "식품의약품안전처 – 식품영양성분 DB",
                                url: "https://various.foodsafetykorea.go.kr/nutrient/"
                            ),
                            SourceLink(
                                label: "USDA FoodData Central",
                                url: "https://fdc.nal.usda.gov/"
                            )
                        ]
                    )

                    sourceSection(
                        title: "운동·신체 활동 권장량",
                        body: "주 150분 이상의 중강도 유산소 운동, 주 2회 이상 근력 운동 권장은 WHO 신체활동 가이드라인을 따릅니다.",
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
            .navigationTitle("의학 정보 출처")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("닫기") { dismiss() }
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
                Text("정보 출처 안내")
                    .font(.bodyLarge).fontWeight(.bold)
                    .foregroundStyle(Color.textPrimary)
            }
            Text("본 앱이 제공하는 BMI, 영양 정보, 운동 권장량 등의 의학·건강 정보는 아래 공인 기관 자료를 근거로 합니다. 각 항목의 링크를 통해 원본 출처를 확인하실 수 있습니다.")
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
                Text("의료 면책 고지")
                    .font(.headingSmall).fontWeight(.bold)
                    .foregroundStyle(Color.textPrimary)
            }
            Text("본 앱이 제공하는 정보는 일반적인 건강 관리 참고용이며, 의학적 진단·치료·처방을 대체하지 않습니다. 건강 관련 결정은 반드시 의료 전문가와 상의하시기 바랍니다.")
                .font(.bodySmall)
                .foregroundStyle(Color.textSecondary)
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
