import SwiftUI

// VITALITY — Home
// Premium Dark × Nature. Editorial scale contrast, aurora mesh,
// paper-toned body with layered depth. Avoids generic health-card slop.

struct HomeView: View {
    @StateObject private var viewModel = HomeViewModel()
    @EnvironmentObject private var container: AppContainer

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 0) {
                HomeHeroSection(viewModel: viewModel)

                HomeBody(viewModel: viewModel)
                    .padding(.top, -36) // pull body up to overlap hero
            }
        }
        .background(Color.brandBone.ignoresSafeArea())
        .ignoresSafeArea(edges: .top)
        .task { await viewModel.loadDashboard(apiClient: container.apiClient) }
        .refreshable { await viewModel.loadDashboard(apiClient: container.apiClient) }
    }
}

// MARK: - Hero Section

private struct HomeHeroSection: View {
    @ObservedObject var viewModel: HomeViewModel
    @State private var appeared = false

    var body: some View {
        ZStack(alignment: .top) {
            AuroraForestBackdrop()
                .frame(height: 440)

            VStack(alignment: .leading, spacing: 0) {
                HeroChromeBar()
                    .padding(.top, 54)
                    .padding(.horizontal, 24)

                HeroTitleBlock()
                    .padding(.top, 28)
                    .padding(.horizontal, 24)

                Spacer(minLength: 20)

                VitalSnapshotPanel(
                    calorieProgress: viewModel.calorieProgress,
                    activityProgress: viewModel.activityProgress
                )
                .padding(.horizontal, 20)
                .padding(.bottom, 60)
                .opacity(appeared ? 1 : 0)
                .offset(y: appeared ? 0 : 12)
                .animation(.easeOut(duration: 0.7).delay(0.15), value: appeared)
            }
        }
        .onAppear { appeared = true }
    }
}

// MARK: - Aurora Forest Backdrop (layered, non-clichéd gradient)

private struct AuroraForestBackdrop: View {
    var body: some View {
        GeometryReader { geo in
            ZStack {
                // Base forest
                LinearGradient.forestHero

                // Aurora glow (top-right)
                RadialGradient(
                    colors: [Color.brandAccent.opacity(0.50), .clear],
                    center: UnitPoint(x: 0.85, y: 0.15),
                    startRadius: 0,
                    endRadius: geo.size.width * 0.9
                )
                .blendMode(.screen)

                // Moss glow (bottom-left)
                RadialGradient(
                    colors: [Color.brandMoss.opacity(0.45), .clear],
                    center: UnitPoint(x: 0.0, y: 0.85),
                    startRadius: 0,
                    endRadius: geo.size.width * 0.7
                )
                .blendMode(.plusLighter)

                // Fine grain — subtle texture, not a slop gradient
                GrainOverlay(density: 70)
                    .opacity(0.14)
                    .blendMode(.overlay)

                // Sliver horizon line
                Rectangle()
                    .fill(Color.brandAccentGlow.opacity(0.35))
                    .frame(height: 1)
                    .frame(maxWidth: .infinity)
                    .offset(y: geo.size.height * 0.22)
                    .blur(radius: 1.2)

                // Bottom paper-transition — curved shoulder instead of crude wave
                VStack(spacing: 0) {
                    Spacer()
                    PaperShoulder()
                        .fill(Color.brandBone)
                        .frame(height: 90)
                }
            }
        }
    }
}

private struct PaperShoulder: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 0, y: rect.maxY))
        p.addLine(to: CGPoint(x: 0, y: rect.midY))
        p.addCurve(
            to: CGPoint(x: rect.maxX, y: rect.midY),
            control1: CGPoint(x: rect.width * 0.35, y: rect.minY + 6),
            control2: CGPoint(x: rect.width * 0.65, y: rect.minY + 22)
        )
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        p.closeSubpath()
        return p
    }
}

/// Lightweight procedural grain — random dots, seeded by geometry.
private struct GrainOverlay: View {
    let density: Int

    var body: some View {
        Canvas { ctx, size in
            var gen = SystemRandomNumberGenerator()
            for _ in 0..<Int(size.width * size.height / CGFloat(max(density, 20))) {
                let x = CGFloat.random(in: 0...size.width, using: &gen)
                let y = CGFloat.random(in: 0...size.height, using: &gen)
                let r = CGFloat.random(in: 0.3...0.9, using: &gen)
                let a = Double.random(in: 0.05...0.22, using: &gen)
                let rect = CGRect(x: x, y: y, width: r, height: r)
                ctx.fill(Path(ellipseIn: rect), with: .color(.white.opacity(a)))
            }
        }
        .allowsHitTesting(false)
    }
}

// MARK: - Hero Chrome

private struct HeroChromeBar: View {
    var body: some View {
        HStack {
            HStack(spacing: 8) {
                Circle()
                    .fill(LinearGradient.mintGlow)
                    .frame(width: 8, height: 8)
                    .overlay(
                        Circle().stroke(Color.brandAccentGlow, lineWidth: 0.6)
                    )
                Text("VITALITY")
                    .font(.system(size: 13, weight: .heavy, design: .rounded))
                    .tracking(3.0)
                    .foregroundStyle(.white.opacity(0.92))
            }
            Spacer()
            HStack(spacing: 10) {
                ChromeIcon(system: "bell")
                ChromeIcon(system: "person.crop.circle")
            }
        }
    }
}

private struct ChromeIcon: View {
    let system: String
    var body: some View {
        Image(systemName: system)
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(.white.opacity(0.88))
            .frame(width: 38, height: 38)
            .background(
                Circle()
                    .fill(Color.glassLight)
                    .overlay(Circle().stroke(Color.glassEdge, lineWidth: 0.7))
            )
    }
}

// MARK: - Hero Title Block (editorial)

private struct HeroTitleBlock: View {
    private var dateString: String {
        let f = DateFormatter()
        f.dateFormat = "EEEE, MMM d"
        f.locale = Locale(identifier: "en_US")
        return f.string(from: Date()).uppercased()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(dateString)
                .eyebrowStyle(Color.brandAccentGlow.opacity(0.85))

            (Text("오늘도 ")
                .foregroundColor(.white)
             + Text("단단한")
                .foregroundColor(Color.brandAccentGlow)
                .italic()
             + Text("\n하루를 쌓아요")
                .foregroundColor(.white))
                .font(.displayLarge)
                .heroTracking()
                .lineSpacing(-4)

            Text("깊은 숲처럼, 조용히 꾸준히.")
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(.white.opacity(0.55))
                .padding(.top, 4)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - Vital Snapshot Panel (glass card)

private struct VitalSnapshotPanel: View {
    let calorieProgress: Double
    let activityProgress: Double

    var body: some View {
        VStack(spacing: 0) {
            // Top — rings
            HStack(alignment: .center, spacing: 18) {
                VitalRing(
                    progress: calorieProgress,
                    gradient: LinearGradient.ringCalorie,
                    track: Color.white.opacity(0.12),
                    glyph: "flame.fill"
                )
                VitalRing(
                    progress: activityProgress,
                    gradient: LinearGradient.ringActivity,
                    track: Color.white.opacity(0.12),
                    glyph: "bolt.heart.fill"
                )

                Spacer(minLength: 4)

                VStack(alignment: .trailing, spacing: 4) {
                    Text("TODAY")
                        .eyebrowStyle(Color.brandAccentGlow.opacity(0.8))
                    Text(Date(), format: .dateTime.day().month(.abbreviated))
                        .font(.numeralMedium)
                        .foregroundStyle(.white)
                    Text(weekdayKo())
                        .font(.system(size: 11))
                        .foregroundStyle(.white.opacity(0.5))
                }
            }
            .padding(.horizontal, 22)
            .padding(.top, 20)
            .padding(.bottom, 16)

            // Divider hairline
            Rectangle()
                .fill(Color.white.opacity(0.08))
                .frame(height: 1)
                .padding(.horizontal, 22)

            // Bottom — metrics row
            HStack(spacing: 0) {
                MetricColumn(label: "칼로리", value: "\(Int(calorieProgress * 100))", unit: "%")
                Divider().frame(width: 1, height: 28).overlay(Color.white.opacity(0.1))
                MetricColumn(label: "활동", value: "\(Int(activityProgress * 100))", unit: "%")
                Divider().frame(width: 1, height: 28).overlay(Color.white.opacity(0.1))
                MetricColumn(label: "연속", value: "7", unit: "일")
            }
            .padding(.vertical, 14)
            .padding(.horizontal, 8)
        }
        .background(
            ZStack {
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .fill(Color.black.opacity(0.28))
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .fill(.ultraThinMaterial.opacity(0.0)) // placeholder to keep compile-safe
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .stroke(Color.white.opacity(0.14), lineWidth: 0.8)
            }
        )
        .overlay(alignment: .topTrailing) {
            // subtle corner glow
            Circle()
                .fill(LinearGradient.mintGlow)
                .frame(width: 120, height: 120)
                .blur(radius: 28)
                .offset(x: 30, y: -30)
                .allowsHitTesting(false)
                .mask(RoundedRectangle(cornerRadius: 28, style: .continuous))
        }
        .elevation(.forest)
    }

    private func weekdayKo() -> String {
        let f = DateFormatter()
        f.dateFormat = "EEEE"
        f.locale = Locale(identifier: "ko_KR")
        return f.string(from: Date())
    }
}

private struct VitalRing: View {
    let progress: Double
    let gradient: LinearGradient
    let track: Color
    let glyph: String

    var body: some View {
        ZStack {
            Circle().stroke(track, lineWidth: 8)
            Circle()
                .trim(from: 0, to: max(0.005, min(progress, 1)))
                .stroke(gradient, style: StrokeStyle(lineWidth: 8, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(.spring(response: 0.9, dampingFraction: 0.85), value: progress)

            // Inner glyph
            Image(systemName: glyph)
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(.white.opacity(0.85))
        }
        .frame(width: 56, height: 56)
    }
}

private struct MetricColumn: View {
    let label: String
    let value: String
    let unit: String

    var body: some View {
        VStack(alignment: .center, spacing: 4) {
            HStack(alignment: .lastTextBaseline, spacing: 2) {
                Text(value)
                    .font(.numeralLarge)
                    .foregroundStyle(.white)
                Text(unit)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.55))
            }
            Text(label)
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(.white.opacity(0.55))
                .tracking(0.8)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Body Section

private struct HomeBody: View {
    @ObservedObject var viewModel: HomeViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 28) {
            LogCTASection()
                .padding(.horizontal, 20)
                .padding(.top, 52) // space under hero-overlap

            PlanSection(goal: viewModel.activeGoal)

            MealsSection(logs: viewModel.todayDietLogs)

            WorkoutSection(session: viewModel.recentSessions.first)

            Spacer(minLength: 60)
        }
        .background(
            Color.brandBone
                .clipShape(RoundedCorner(radius: 36, corners: [.topLeft, .topRight]))
        )
    }
}

private struct RoundedCorner: Shape {
    var radius: CGFloat
    var corners: UIRectCorner

    func path(in rect: CGRect) -> Path {
        Path(UIBezierPath(roundedRect: rect,
                          byRoundingCorners: corners,
                          cornerRadii: CGSize(width: radius, height: radius)).cgPath)
    }
}

// MARK: - Log CTA (premium button)

private struct LogCTASection: View {
    var body: some View {
        NavigationLink(destination: RecordHubView()) {
            HStack(spacing: 16) {
                ZStack {
                    Circle()
                        .fill(LinearGradient.sunrise)
                        .frame(width: 46, height: 46)
                    Image(systemName: "plus")
                        .font(.system(size: 20, weight: .heavy))
                        .foregroundStyle(.white)
                }
                .shadow(color: Color.brandEmber.opacity(0.4), radius: 10, x: 0, y: 4)

                VStack(alignment: .leading, spacing: 3) {
                    Text("오늘의 기록을 시작하세요")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(Color.brandDusk)
                    Text("운동 · 식단 · 신체 변화")
                        .font(.system(size: 12))
                        .foregroundStyle(Color.textSecondary)
                }

                Spacer()

                Image(systemName: "arrow.up.right")
                    .font(.system(size: 14, weight: .heavy))
                    .foregroundStyle(Color.brandDusk.opacity(0.6))
                    .frame(width: 36, height: 36)
                    .background(
                        Circle().fill(Color.brandBone)
                            .overlay(Circle().stroke(Color.brandDusk.opacity(0.1), lineWidth: 1))
                    )
            }
            .padding(16)
            .background(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .fill(Color.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: 22, style: .continuous)
                            .stroke(Color.brandDusk.opacity(0.06), lineWidth: 1)
                    )
            )
            .elevation(.low)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Editorial Section wrapper

private struct EditorialSectionHeader: View {
    let eyebrow: String
    let title: String
    var subtitle: String? = nil

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 14) {
            Rectangle()
                .fill(LinearGradient.sunrise)
                .frame(width: 22, height: 2)
                .offset(y: -4)

            VStack(alignment: .leading, spacing: 2) {
                Text(eyebrow).eyebrowStyle(Color.textTertiary)
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(title)
                        .font(.system(size: 22, weight: .bold, design: .serif))
                        .foregroundStyle(Color.brandDusk)
                    if let subtitle {
                        Text(subtitle)
                            .font(.system(size: 13, weight: .medium))
                            .foregroundStyle(Color.textTertiary)
                    }
                }
            }
            Spacer()
        }
        .padding(.horizontal, 20)
    }
}

// MARK: - Plan Section

private struct PlanSection: View {
    let goal: GoalSummary?

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            EditorialSectionHeader(eyebrow: "CHAPTER 01", title: "나의 플랜", subtitle: "in progress")
            PlanCardView(goal: goal)
                .padding(.horizontal, 20)
        }
    }
}

private struct PlanCardView: View {
    let goal: GoalSummary?

    var body: some View {
        NavigationLink(destination: GoalSettingView()) {
            if let goal {
                PlanActiveCard(goal: goal)
            } else {
                PlanEmptyCard()
            }
        }
        .buttonStyle(.plain)
    }
}

private struct PlanActiveCard: View {
    let goal: GoalSummary

    var body: some View {
        ZStack {
            // Layered base
            RoundedRectangle(cornerRadius: 26, style: .continuous)
                .fill(Color.white)
                .overlay(
                    RoundedRectangle(cornerRadius: 26, style: .continuous)
                        .stroke(Color.brandDusk.opacity(0.06), lineWidth: 1)
                )

            // Corner motif — subtle forest quadrant
            RoundedRectangle(cornerRadius: 26, style: .continuous)
                .fill(
                    RadialGradient(
                        colors: [Color.brandAccent.opacity(0.20), .clear],
                        center: .bottomTrailing, startRadius: 10, endRadius: 220
                    )
                )

            HStack(alignment: .top, spacing: 16) {
                // Progress gauge
                ZStack {
                    Circle().stroke(Color.brandSurface, lineWidth: 6)
                    Circle()
                        .trim(from: 0, to: max(0.01, min(goal.progressRatio, 1)))
                        .stroke(LinearGradient.ringCalorie, style: StrokeStyle(lineWidth: 6, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                    Text("\(Int(goal.progressRatio * 100))")
                        .font(.system(size: 18, weight: .heavy, design: .rounded))
                        .foregroundStyle(Color.brandDusk)
                }
                .frame(width: 68, height: 68)

                VStack(alignment: .leading, spacing: 6) {
                    Text("GOAL · \(goal.goalType.displayName.uppercased())")
                        .eyebrowStyle()
                    Text("\(goal.goalType.emoji)  \(goal.goalType.displayName)")
                        .font(.system(size: 18, weight: .bold, design: .serif))
                        .foregroundStyle(Color.brandDusk)
                    if let days = goal.daysRemaining {
                        HStack(spacing: 6) {
                            Capsule()
                                .fill(Color.brandDusk)
                                .frame(width: 52, height: 22)
                                .overlay(
                                    Text("D-\(days)")
                                        .font(.system(size: 11, weight: .heavy, design: .rounded))
                                        .foregroundStyle(.white)
                                )
                            Text("\(String(format: "%.0f", goal.progressRatio * 100))% 완료")
                                .font(.system(size: 12, weight: .medium))
                                .foregroundStyle(Color.textSecondary)
                        }
                        .padding(.top, 2)
                    }
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Color.brandDusk.opacity(0.35))
                    .padding(.top, 4)
            }
            .padding(20)
        }
        .frame(height: 140)
        .elevation(.low)
    }
}

private struct PlanEmptyCard: View {
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 26, style: .continuous)
                .fill(Color.brandDusk)
            // subtle mesh
            RoundedRectangle(cornerRadius: 26, style: .continuous)
                .fill(
                    RadialGradient(
                        colors: [Color.brandAccent.opacity(0.35), .clear],
                        center: .topLeading, startRadius: 4, endRadius: 220
                    )
                )

            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("NO ACTIVE PLAN")
                        .eyebrowStyle(Color.brandAccentGlow)
                    Text("목표를 세우고\n여정을 시작하세요")
                        .font(.system(size: 20, weight: .bold, design: .serif))
                        .foregroundStyle(.white)
                        .lineSpacing(2)
                }
                Spacer()
                ZStack {
                    Circle().fill(Color.brandAccent)
                    Image(systemName: "arrow.up.right")
                        .font(.system(size: 16, weight: .heavy))
                        .foregroundStyle(Color.brandDusk)
                }
                .frame(width: 50, height: 50)
            }
            .padding(22)
        }
        .frame(height: 140)
        .elevation(.forest)
    }
}

// MARK: - Meals Section

private struct MealsSection: View {
    let logs: [DietLogSummary]

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            EditorialSectionHeader(eyebrow: "CHAPTER 02", title: "최근 식단", subtitle: "today")

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: 12) {
                    if logs.isEmpty {
                        EmptyMealPlaceholder()
                    } else {
                        ForEach(logs) { log in
                            NavigationLink(
                                destination: DietLogDetailView(
                                    logId: log.dietLogId,
                                    mealType: log.mealType,
                                    logDate: log.logDate
                                )
                            ) {
                                MealLogCard(log: log)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    NavigationLink(destination: DietRecordView()) {
                        AddMealCard()
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 20)
            }
        }
    }
}

private struct MealLogCard: View {
    let log: DietLogSummary

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(Color.brandLight)

                // editorial emoji anchor
                Text(log.mealType.emoji)
                    .font(.system(size: 56))
                    .offset(x: 18, y: 18)
                    .rotationEffect(.degrees(-4))
            }
            .frame(width: 148, height: 124)
            .overlay(alignment: .topTrailing) {
                Text(log.mealType.displayName)
                    .font(.system(size: 10, weight: .heavy))
                    .tracking(1.2)
                    .textCase(.uppercase)
                    .foregroundStyle(Color.brandDusk)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(
                        Capsule().fill(Color.white)
                    )
                    .padding(10)
            }

            VStack(alignment: .leading, spacing: 3) {
                HStack(alignment: .lastTextBaseline, spacing: 2) {
                    Text(String(format: "%.0f", log.totalCalories ?? 0))
                        .font(.system(size: 22, weight: .heavy, design: .rounded))
                        .foregroundStyle(Color.brandDusk)
                    Text("kcal")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Color.textSecondary)
                }

                if let p = log.totalProteinG, let c = log.totalCarbsG {
                    Text(String(format: "P %.0f  ·  C %.0f", p, c))
                        .font(.dataSmall)
                        .foregroundStyle(Color.textTertiary)
                }
            }
            .frame(width: 148, alignment: .leading)
            .padding(.top, 10)
        }
    }
}

private struct EmptyMealPlaceholder: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(Color.brandBone)
                    .overlay(
                        RoundedRectangle(cornerRadius: 18, style: .continuous)
                            .stroke(Color.brandDusk.opacity(0.08), style: StrokeStyle(lineWidth: 1, dash: [4]))
                    )
                VStack(spacing: 8) {
                    Text("🌱")
                        .font(.system(size: 34))
                        .opacity(0.5)
                    Text("아직 기록이 없어요")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(Color.textTertiary)
                }
            }
            .frame(width: 148, height: 124)

            Text("첫 식사를 기록해보세요")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Color.textSecondary)
                .frame(width: 148, alignment: .leading)
                .padding(.top, 10)
        }
    }
}

private struct AddMealCard: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(Color.brandDusk)
                VStack(spacing: 10) {
                    Image(systemName: "plus")
                        .font(.system(size: 18, weight: .heavy))
                        .foregroundStyle(Color.brandDusk)
                        .frame(width: 40, height: 40)
                        .background(Circle().fill(Color.brandAccentGlow))
                    Text("기록 추가")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(.white)
                }
            }
            .frame(width: 148, height: 124)

            Text("+ 새 식단")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Color.brandDusk)
                .frame(width: 148, alignment: .leading)
                .padding(.top, 10)
        }
    }
}

// MARK: - Workout Section

private struct WorkoutSection: View {
    let session: SessionSummary?

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            EditorialSectionHeader(eyebrow: "CHAPTER 03", title: "최근 운동", subtitle: "recent")
            WorkoutHero(session: session)
                .padding(.horizontal, 20)
        }
    }
}

private struct WorkoutHero: View {
    let session: SessionSummary?

    var body: some View {
        NavigationLink(destination: ExerciseRecordView()) {
            ZStack {
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .fill(LinearGradient.forestHero)

                // glow
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .fill(
                        RadialGradient(
                            colors: [Color.brandAccent.opacity(0.35), .clear],
                            center: UnitPoint(x: 0.9, y: 0.1),
                            startRadius: 10, endRadius: 260
                        )
                    )

                // grain
                GrainOverlay(density: 140)
                    .opacity(0.1)
                    .blendMode(.overlay)
                    .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))

                HStack(alignment: .top) {
                    if let session {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("LAST SESSION")
                                .eyebrowStyle(Color.brandAccentGlow)
                            Text(session.formattedDate)
                                .font(.system(size: 22, weight: .bold, design: .serif))
                                .foregroundStyle(.white)

                            HStack(spacing: 10) {
                                if let vol = session.totalVolumeKg {
                                    WorkoutStat(icon: "dumbbell.fill", value: String(format: "%.0f", vol), unit: "kg")
                                }
                                if let cal = session.caloriesBurned {
                                    WorkoutStat(icon: "flame.fill", value: String(format: "%.0f", cal), unit: "kcal")
                                }
                                if let dur = session.durationMinutes {
                                    WorkoutStat(icon: "clock.fill", value: "\(dur)", unit: "분")
                                }
                            }
                            .padding(.top, 4)
                        }
                    } else {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("RESTING")
                                .eyebrowStyle(Color.brandAccentGlow)
                            Text("오늘의 운동을\n시작해보세요")
                                .font(.system(size: 22, weight: .bold, design: .serif))
                                .foregroundStyle(.white)
                                .lineSpacing(2)
                        }
                    }

                    Spacer()

                    ZStack {
                        Circle()
                            .fill(Color.brandAccentGlow)
                            .frame(width: 54, height: 54)
                        Image(systemName: "arrow.up.right")
                            .font(.system(size: 18, weight: .heavy))
                            .foregroundStyle(Color.brandDusk)
                    }
                }
                .padding(22)
            }
            .frame(height: 170)
            .elevation(.forest)
        }
        .buttonStyle(.plain)
    }
}

private struct WorkoutStat: View {
    let icon: String
    let value: String
    let unit: String

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
                .font(.system(size: 10, weight: .bold))
                .foregroundStyle(Color.brandAccentGlow)
            HStack(alignment: .lastTextBaseline, spacing: 4) {
                Text(value)
                    .font(.system(size: 14, weight: .heavy, design: .rounded))
                    .monospacedDigit()
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
                Text(unit)
                    .font(.system(size: 10, weight: .medium))
                    .foregroundStyle(.white.opacity(0.6))
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
            }
            .fixedSize(horizontal: true, vertical: false)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(
            Capsule().fill(Color.white.opacity(0.08))
                .overlay(Capsule().stroke(Color.white.opacity(0.14), lineWidth: 0.7))
        )
        .fixedSize(horizontal: true, vertical: false)
    }
}
