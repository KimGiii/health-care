import SwiftUI

struct ExploreView: View {
    @EnvironmentObject private var container: AppContainer

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 24) {
                InsightSectionHeader()
                InsightMenuGrid(container: container)
            }
            .padding(.horizontal, 20)
            .padding(.top, 8)
            .padding(.bottom, 40)
        }
        .background(Color.surfaceGrouped)
        .navigationTitle("탐색")
        .navigationBarTitleDisplayMode(.large)
    }
}

// MARK: - Section Header

private struct InsightSectionHeader: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("인사이트")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Color.textSecondary)
                .textCase(.uppercase)
                .tracking(0.5)
            Text("나의 기록을 분석해보세요")
                .font(.system(size: 15))
                .foregroundStyle(Color.textSecondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - Menu Grid

private struct InsightMenuGrid: View {
    let container: AppContainer

    var body: some View {
        VStack(spacing: 12) {
            NavigationLink {
                WeeklyRetrospectiveView()
                    .environmentObject(container)
            } label: {
                InsightMenuCard(
                    icon: "chart.bar.doc.horizontal",
                    iconColor: Color.brandPrimary,
                    title: "주간 회고",
                    description: "이번 주 운동·식단·신체 변화를 한눈에"
                )
            }
            .buttonStyle(.plain)

            NavigationLink {
                ChangeAnalysisView()
                    .environmentObject(container)
            } label: {
                InsightMenuCard(
                    icon: "waveform.path.ecg",
                    iconColor: .purple,
                    title: "변화 분석",
                    description: "기간별 신체 지표 변화와 운동 통계"
                )
            }
            .buttonStyle(.plain)
        }
    }
}

// MARK: - Card

private struct InsightMenuCard: View {
    let icon: String
    let iconColor: Color
    let title: String
    let description: String

    var body: some View {
        HStack(spacing: 16) {
            Image(systemName: icon)
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(iconColor)
                .frame(width: 50, height: 50)
                .background(iconColor.opacity(0.1))
                .clipShape(RoundedRectangle(cornerRadius: 14))

            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(Color.textPrimary)
                Text(description)
                    .font(.system(size: 13))
                    .foregroundStyle(Color.textSecondary)
            }

            Spacer()

            Image(systemName: "chevron.right")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Color.textSecondary.opacity(0.5))
        }
        .padding(16)
        .background(Color.surfacePrimary)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .shadow(color: .black.opacity(0.05), radius: 8, x: 0, y: 2)
    }
}
