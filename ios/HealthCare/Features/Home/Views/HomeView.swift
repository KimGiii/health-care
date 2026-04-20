import SwiftUI

struct HomeView: View {
    @StateObject private var viewModel = HomeViewModel()

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 0) {
                // ── Hero Header (dark green wave) ──────────────────────
                HeroHeaderView()

                // ── Body content ──────────────────────────────────────
                VStack(alignment: .leading, spacing: 28) {
                    HomeSectionView(title: "나의 플랜") {
                        PlanCardView()
                    }
                    HomeSectionView(title: "최근 식단") {
                        RecentMealsRowView()
                    }
                    HomeSectionView(title: "운동 루틴") {
                        WorkoutRoutineCardView()
                    }
                }
                .padding(.vertical, 24)
                .background(Color.surfacePrimary)
            }
        }
        .ignoresSafeArea(edges: .top)
        .background(Color.surfacePrimary)
        .task { await viewModel.loadDashboard() }
    }
}

// MARK: - Hero Header
private struct HeroHeaderView: View {
    var body: some View {
        ZStack(alignment: .top) {
            // Wave background
            WaveBackground()
                .frame(height: 380)

            VStack(spacing: 0) {
                // App title
                Text("VITALITY")
                    .font(.system(size: 22, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
                    .padding(.top, 60)

                // Snapshot + CTA row
                HStack(alignment: .center, spacing: 14) {
                    SnapshotCard()
                    LogButton()
                }
                .padding(.horizontal, 20)
                .padding(.top, 20)
            }
        }
    }
}

// MARK: - Wave Background Shape
private struct WaveBackground: View {
    var body: some View {
        GeometryReader { geo in
            ZStack {
                Color.brandPrimary
                // Organic blob — upper right
                Ellipse()
                    .fill(Color.brandSecondary.opacity(0.55))
                    .frame(width: geo.size.width * 0.75, height: geo.size.height * 0.65)
                    .offset(x: geo.size.width * 0.25, y: -geo.size.height * 0.12)
                    .rotationEffect(.degrees(-18))

                // Wavy bottom curve
                WaveCurve()
                    .fill(Color.surfacePrimary)
                    .frame(height: 80)
                    .frame(maxWidth: .infinity)
                    .offset(y: geo.size.height - 40)
            }
        }
    }
}

private struct WaveCurve: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 0, y: rect.height))
        path.addCurve(
            to: CGPoint(x: rect.width, y: rect.height),
            control1: CGPoint(x: rect.width * 0.25, y: 0),
            control2: CGPoint(x: rect.width * 0.75, y: rect.height * 0.6)
        )
        path.addLine(to: CGPoint(x: rect.width, y: rect.height))
        path.addLine(to: CGPoint(x: 0, y: rect.height))
        path.closeSubpath()
        return path
    }
}

// MARK: - Today's Snapshot Card
private struct SnapshotCard: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("오늘의 요약")
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(Color.textPrimary)

            HStack(spacing: 20) {
                CircularRingView(label: "칼로리", progress: 0.62, color: Color.brandAccent)
                CircularRingView(label: "활동", progress: 0.81, color: Color.brandPrimary)
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(Color.surfacePrimary)
                .shadow(color: .black.opacity(0.10), radius: 12, x: 0, y: 4)
        )
    }
}

// MARK: - Circular Ring
private struct CircularRingView: View {
    let label: String
    let progress: Double
    let color: Color

    var body: some View {
        ZStack {
            Circle()
                .stroke(color.opacity(0.18), lineWidth: 9)
            Circle()
                .trim(from: 0, to: progress)
                .stroke(color, style: StrokeStyle(lineWidth: 9, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(.easeInOut(duration: 0.8), value: progress)

            Text(label)
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(Color.textPrimary)
        }
        .frame(width: 80, height: 80)
    }
}

// MARK: - Log Workout / Meal CTA
private struct LogButton: View {
    var body: some View {
        NavigationLink(destination: RecordHubView()) {
            VStack(spacing: 0) {
                Spacer()
                Text("운동/식단\n기록하기")
                    .font(.system(size: 18, weight: .bold))
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.white)
                    .lineSpacing(4)
                Spacer()
            }
            .frame(maxWidth: .infinity)
            .frame(height: 130)
            .background(
                RoundedRectangle(cornerRadius: 20)
                    .fill(Color.brandPrimary)
                    .shadow(color: Color.brandPrimary.opacity(0.35), radius: 12, x: 0, y: 6)
            )
        }
    }
}

// MARK: - Section wrapper
private struct HomeSectionView<Content: View>: View {
    let title: String
    let content: () -> Content

    init(title: String, @ViewBuilder content: @escaping () -> Content) {
        self.title = title
        self.content = content
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(Color.textPrimary)
                .padding(.horizontal, 20)

            content()
        }
    }
}

// MARK: - Plan Card

private struct PlanCardView: View {
    var body: some View {
        NavigationLink(destination: GoalSettingView()) {
            ZStack(alignment: .bottomTrailing) {
                RoundedRectangle(cornerRadius: 20)
                    .fill(Color.brandSurface)
                    .frame(height: 140)

                // Organic blob decoration
                Circle()
                    .fill(Color.brandAccent.opacity(0.3))
                    .frame(width: 120, height: 120)
                    .offset(x: 20, y: 30)

                HStack {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("목표 관리")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundStyle(Color.brandPrimary)
                        Text("목표를 설정하고 달성률을 확인하세요")
                            .font(.system(size: 13))
                            .foregroundStyle(Color.textSecondary)
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Color.brandPrimary.opacity(0.5))
                }
                .padding(20)
            }
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 20)
        .clipped()
    }
}


// MARK: - Recent Meals Row
private struct RecentMealsRowView: View {
    private let meals: [(name: String, kcal: String, icon: String)] = [
        ("닭가슴살 샐러드", "420 kcal", "fork.knife"),
        ("그릭 요거트", "180 kcal", "cup.and.saucer.fill"),
        ("현미밥 + 된장국", "510 kcal", "fork.knife.circle.fill"),
    ]

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 14) {
                ForEach(meals, id: \.name) { meal in
                    MealCardView(name: meal.name, kcal: meal.kcal, icon: meal.icon)
                }
                AddMealCard()
            }
            .padding(.horizontal, 20)
        }
    }
}

private struct MealCardView: View {
    let name: String
    let kcal: String
    let icon: String

    var body: some View {
        VStack(spacing: 0) {
            // Placeholder image area
            ZStack {
                RoundedRectangle(cornerRadius: 14)
                    .fill(Color.brandSurface)
                    .frame(width: 130, height: 110)

                Image(systemName: icon)
                    .font(.system(size: 30))
                    .foregroundStyle(Color.brandSecondary)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(name)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Color.textPrimary)
                    .lineLimit(1)
                Text(kcal)
                    .font(.system(size: 11))
                    .foregroundStyle(Color.textSecondary)
            }
            .frame(width: 130, alignment: .leading)
            .padding(.top, 8)
        }
    }
}

private struct AddMealCard: View {
    var body: some View {
        VStack {
            ZStack {
                RoundedRectangle(cornerRadius: 14)
                    .stroke(Color.brandAccent.opacity(0.5), style: StrokeStyle(lineWidth: 1.5, dash: [5]))
                    .frame(width: 130, height: 110)

                VStack(spacing: 6) {
                    Image(systemName: "plus.circle.fill")
                        .font(.system(size: 28))
                        .foregroundStyle(Color.brandAccent)
                    Text("추가")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Color.brandAccent)
                }
            }
        }
    }
}

// MARK: - Workout Routine Card
private struct WorkoutRoutineCardView: View {
    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            RoundedRectangle(cornerRadius: 20)
                .fill(Color.brandPrimary)
                .frame(height: 140)

            // Decorative blob
            Ellipse()
                .fill(Color.brandSecondary.opacity(0.5))
                .frame(width: 150, height: 100)
                .rotationEffect(.degrees(-25))
                .offset(x: 40, y: 20)

            HStack {
                VStack(alignment: .leading, spacing: 6) {
                    Text("오늘의 운동")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(.white.opacity(0.75))
                    Text("상체 근력 루틴")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundStyle(.white)
                    Text("45분 · 6가지 동작")
                        .font(.system(size: 12))
                        .foregroundStyle(.white.opacity(0.65))
                }
                Spacer()
                PlayButton()
            }
            .padding(20)
        }
        .padding(.horizontal, 20)
        .clipped()
    }
}

private struct PlayButton: View {
    var body: some View {
        Circle()
            .fill(Color.white.opacity(0.15))
            .frame(width: 52, height: 52)
            .overlay(
                Image(systemName: "play.fill")
                    .font(.system(size: 20))
                    .foregroundStyle(.white)
                    .offset(x: 2)
            )
    }
}
