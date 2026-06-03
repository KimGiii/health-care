import SwiftUI

// MARK: - Main View

struct GoalProgressView: View {
    @StateObject private var viewModel: GoalProgressViewModel
    @EnvironmentObject private var container: AppContainer
    @Environment(\.dismiss) private var dismiss
    @State private var showEditSheet = false

    init(goalId: Int) {
        _viewModel = StateObject(wrappedValue: GoalProgressViewModel(goalId: goalId))
    }

    var body: some View {
        ZStack(alignment: .top) {
            Color.backgroundPage.ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(spacing: 0) {
                    ProgressHeroSection(
                        progress: viewModel.progress,
                        isLoading: viewModel.isLoading,
                        onDismiss: { dismiss() },
                        onEdit: viewModel.progress?.isOnTrack != nil ? { showEditSheet = true } : nil
                    )

                    if let p = viewModel.progress {
                        VStack(spacing: 16) {
                            TrackingStatusCard(progress: p)
                            ValueProgressCard(progress: p)
                            TimelineCard(progress: p)
                            if let checkpoints = p.checkpoints, !checkpoints.isEmpty {
                                CheckpointHistoryCard(checkpoints: checkpoints)
                            }
                        }
                        .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing
                        .padding(.top, Spacing.xl) // design-lint:ignore — micro/hero spacing
                        .padding(.bottom, Spacing.xxxl) // design-lint:ignore — micro/hero spacing
                    } else if !viewModel.isLoading {
                        EmptyProgressState()
                            .padding(.top, 60) // design-lint:ignore — micro/hero spacing
                    }
                }
            }
            .ignoresSafeArea(edges: .top)
            .refreshable {
                await viewModel.load(apiClient: container.apiClient)
                viewModel.errorMessage = nil
            }
        }
        .navigationBarHidden(true)
        .sheet(isPresented: $showEditSheet) {
            if let progress = viewModel.progress {
                EditGoalView(progress: progress) {
                    Task { await viewModel.load(apiClient: container.apiClient) }
                }
            }
        }
        .alert(Text("오류"), isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button(String(localized: "common.ok"), role: .cancel) {}
        } message: {
            Text(viewModel.errorMessage ?? "")
        }
        .task { await viewModel.load(apiClient: container.apiClient) }
    }
}

// MARK: - Hero Section

private struct ProgressHeroSection: View {
    let progress: GoalProgressResponse?
    let isLoading: Bool
    let onDismiss: () -> Void
    let onEdit: (() -> Void)?

    var body: some View {
        ZStack(alignment: .top) {
            ProgressWaveBackground().frame(height: 320)

            VStack(spacing: 0) {
                HStack {
                    Button(action: onDismiss) {
                        Image(systemName: "chevron.left")
                            .font(.cta)
                            .foregroundStyle(.white)
                            .padding(Spacing.md) // design-lint:ignore — micro/hero spacing
                            .background(.white.opacity(0.15))
                            .clipShape(Circle())
                    }
                    Spacer()
                    Text(String(localized: "goal.progress.title"))
                        .font(.numeralMedium).fontWeight(.bold)
                        .foregroundStyle(.white)
                    Spacer()
                    if let onEdit {
                        Button(action: onEdit) {
                            Image(systemName: "pencil")
                                .font(.headingSmall)
                                .foregroundStyle(.white)
                                .padding(Spacing.md) // design-lint:ignore — micro/hero spacing
                                .background(.white.opacity(0.15))
                                .clipShape(Circle())
                        }
                    } else {
                        Color.clear.frame(width: 40, height: 40)
                    }
                }
                .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing
                .padding(.top, 56) // design-lint:ignore — micro/hero spacing

                if isLoading {
                    ProgressView().tint(.white).padding(.top, 50) // design-lint:ignore — micro/hero spacing
                } else if let p = progress {
                    HeroProgressRing(progress: p).padding(.top, Spacing.xl)
                }
            }
        }
    }
}

private struct ProgressWaveBackground: View {
    var body: some View {
        GeometryReader { geo in
            ZStack {
                Color.brandPrimary
                Ellipse()
                    .fill(Color.brandSecondary.opacity(0.45))
                    .frame(width: geo.size.width * 0.65, height: geo.size.height * 0.5)
                    .offset(x: geo.size.width * 0.28, y: -geo.size.height * 0.08)
                    .rotationEffect(.degrees(-18))
                GoalWaveCurveShape()
                    .fill(Color.backgroundPage)
                    .frame(height: 64)
                    .frame(maxWidth: .infinity)
                    .offset(y: geo.size.height - 32)
            }
        }
    }
}

private struct GoalWaveCurveShape: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 0, y: rect.height))
        p.addCurve(
            to: CGPoint(x: rect.width, y: rect.height),
            control1: CGPoint(x: rect.width * 0.3, y: 0),
            control2: CGPoint(x: rect.width * 0.7, y: rect.height * 0.5)
        )
        p.addLine(to: CGPoint(x: rect.width, y: rect.height))
        p.addLine(to: CGPoint(x: 0, y: rect.height))
        p.closeSubpath()
        return p
    }
}

private struct HeroProgressRing: View {
    let progress: GoalProgressResponse

    private var ringColor: Color {
        switch progress.trackingStatus {
        case "BEHIND":          return Color.brandDanger
        case "SLIGHTLY_BEHIND": return Color.brandWarning
        default:                return Color.brandAccent
        }
    }

    var body: some View {
        HStack(spacing: 28) {
            ZStack {
                Circle()
                    .stroke(Color.white.opacity(0.18), lineWidth: 14)
                    .frame(width: 120, height: 120)
                Circle()
                    .trim(from: 0, to: progress.progressRatio)
                    .stroke(ringColor, style: StrokeStyle(lineWidth: 14, lineCap: .round))
                    .frame(width: 120, height: 120)
                    .rotationEffect(.degrees(-90))
                    .animation(.easeInOut(duration: 1.0), value: progress.progressRatio)

                VStack(spacing: 2) {
                    Text(String(format: "%.0f%%", (progress.percentComplete ?? 0)))
                        .font(.numeralLarge)
                        .foregroundStyle(.white)
                    Text(String(localized: "goal.summary.achieved"))
                        .font(.captionXSmall)
                        .foregroundStyle(.white.opacity(0.65))
                }
            }

            VStack(alignment: .leading, spacing: 10) {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Image(systemName: progress.goalType.icon)
                        Text(progress.goalType.displayName)
                    }
                    .font(.bodyLarge).fontWeight(.bold)
                    .foregroundStyle(.white)
                    Text(String(format: String(localized: "goal.progress.target"), progress.formattedValue(progress.targetValue)))
                        .font(.bodySmall)
                        .foregroundStyle(.white.opacity(0.75))
                }

                HStack(spacing: 5) {
                    Image(systemName: progress.trackingIcon)
                        .font(.captionXSmall)
                    Text(progress.trackingStatusLabel)
                        .font(.captionBold)
                }
                .foregroundStyle(ringColor)
                .padding(.horizontal, Spacing.md) // design-lint:ignore — micro/hero spacing
                .padding(.vertical, 5) // design-lint:ignore — micro/hero spacing
                .background(.white.opacity(0.15))
                .clipShape(Capsule())

                if let days = progress.daysRemaining, days >= 0 {
                    HStack(spacing: 4) {
                        Image(systemName: "calendar").font(.captionXSmall)
                        Text("D-\(days)").font(.captionXSmall).fontWeight(.semibold)
                    }
                    .foregroundStyle(.white.opacity(0.8))
                }
            }
        }
        .padding(.horizontal, Spacing.xxl) // design-lint:ignore — micro/hero spacing
    }
}

// MARK: - Tracking Status Card

private struct TrackingStatusCard: View {
    let progress: GoalProgressResponse

    private var statusColor: Color {
        switch progress.trackingStatus {
        case "AHEAD":           return .brandAccent
        case "ON_TRACK":        return .brandSuccess
        case "SLIGHTLY_BEHIND": return .brandWarning
        case "BEHIND":          return .brandDanger
        default:                return .brandAccent
        }
    }

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: progress.trackingIcon)
                .font(.system(size: 22)) // design-lint:ignore — SF Symbol or special
                .foregroundStyle(statusColor)
                .frame(width: 48, height: 48)
                .background(statusColor.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: Radius.md))

            VStack(alignment: .leading, spacing: 3) {
                Text(progress.trackingStatusLabel)
                    .font(.headingSmall).fontWeight(.bold)
                    .foregroundStyle(Color.textPrimary)
                Text(progress.isOnTrack
                     ? String(localized: "goal.progress.message.onTrack")
                     : String(localized: "goal.progress.message.behind"))
                    .font(.bodySmall)
                    .foregroundStyle(Color.textSecondary)
            }
            Spacer()
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .shadow(color: .black.opacity(0.06), radius: 8, x: 0, y: 3)
    }
}

// MARK: - Value Progress Card

private struct ValueProgressCard: View {
    let progress: GoalProgressResponse

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(String(localized: "goal.progress.section.values"))
                .font(.headingSmall).fontWeight(.bold)
                .foregroundStyle(Color.textPrimary)

            HStack(spacing: 0) {
                ValueColumn(
                    label: String(localized: "goal.progress.value.start"),
                    value: progress.formattedValue(progress.startValue),
                    color: Color.textSecondary
                )
                Spacer()
                Image(systemName: "arrow.right")
                    .font(.caption)
                    .foregroundStyle(Color.textSecondary)
                Spacer()
                ValueColumn(
                    label: String(localized: "goal.progress.value.current"),
                    value: progress.formattedValue(progress.currentValue),
                    color: .brandPrimary,
                    isBold: true
                )
                Spacer()
                Image(systemName: "arrow.right")
                    .font(.caption)
                    .foregroundStyle(Color.textSecondary)
                Spacer()
                ValueColumn(
                    label: String(localized: "goal.progress.value.target"),
                    value: progress.formattedValue(progress.targetValue),
                    color: Color.brandAccent
                )
            }

            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(Color.surfaceSecondary)
                        .frame(height: 8)
                    Capsule()
                        .fill(LinearGradient(
                            colors: [Color.brandPrimary, Color.brandAccent],
                            startPoint: .leading,
                            endPoint: .trailing
                        ))
                        .frame(width: geo.size.width * progress.progressRatio, height: 8)
                        .animation(.easeInOut(duration: 0.9), value: progress.progressRatio)
                }
            }
            .frame(height: 8)
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .shadow(color: .black.opacity(0.06), radius: 8, x: 0, y: 3)
    }
}

private struct ValueColumn: View {
    let label: String
    let value: String
    let color: Color
    var isBold: Bool = false

    var body: some View {
        VStack(spacing: 5) {
            Text(label)
                .font(.captionXSmall)
                .foregroundStyle(Color.textSecondary)
            Text(value)
                .font(.headingSmall)
                .fontWeight(isBold ? .bold : .semibold)
                .foregroundStyle(color)
        }
    }
}

// MARK: - Timeline Card

private struct TimelineCard: View {
    let progress: GoalProgressResponse

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(String(localized: "goal.progress.section.schedule"))
                .font(.headingSmall).fontWeight(.bold)
                .foregroundStyle(Color.textPrimary)

            HStack(spacing: 0) {
                TimelineItem(
                    icon: "calendar.badge.clock",
                    label: String(localized: "goal.progress.schedule.deadline"),
                    value: progress.formattedTargetDate,
                    color: Color.brandPrimary
                )
                Divider().frame(height: 44)
                TimelineItem(
                    icon: "clock.arrow.trianglehead.counterclockwise.rotate.90",
                    label: String(localized: "goal.progress.schedule.daysLeft"),
                    value: progress.daysRemaining.map { $0 >= 0 ? "D-\($0)" : String(localized: "goal.progress.timeOver") } ?? "-",
                    color: daysColor
                )
                Divider().frame(height: 44)
                TimelineItem(
                    icon: "flag.checkered",
                    label: String(localized: "goal.progress.schedule.estimated"),
                    value: progress.formattedProjectedDate,
                    color: Color.brandAccent
                )
            }
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .shadow(color: .black.opacity(0.06), radius: 8, x: 0, y: 3)
    }

    private var daysColor: Color {
        guard let days = progress.daysRemaining else { return .textPrimary }
        if days < 0 { return .brandDanger }
        if days < 14 { return .brandWarning }
        return .textPrimary
    }
}

private struct TimelineItem: View {
    let icon: String
    let label: String
    let value: String
    let color: Color

    var body: some View {
        VStack(spacing: 6) {
            Image(systemName: icon)
                .font(.bodyLarge)
                .foregroundStyle(color)
            Text(value)
                .font(.captionBold).fontWeight(.bold)
                .foregroundStyle(color)
                .multilineTextAlignment(.center)
            Text(label)
                .font(.captionXSmall)
                .foregroundStyle(Color.textSecondary)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Checkpoint History Card

private struct CheckpointHistoryCard: View {
    let checkpoints: [GoalCheckpointItem]

    private var sorted: [GoalCheckpointItem] {
        checkpoints.sorted { $0.checkpointDate > $1.checkpointDate }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text(String(localized: "goal.progress.section.history"))
                    .font(.headingSmall).fontWeight(.bold)
                    .foregroundStyle(Color.textPrimary)
                Spacer()
                Text(String(format: String(localized: "goal.progress.checkpoint.count"), checkpoints.count))
                    .font(.caption).fontWeight(.medium)
                    .foregroundStyle(Color.textSecondary)
            }

            VStack(spacing: 1) {
                ForEach(Array(sorted.enumerated()), id: \.offset) { _, cp in
                    CheckpointRow(checkpoint: cp)
                    if cp.checkpointDate != sorted.last?.checkpointDate {
                        Divider().padding(.leading, 44) // design-lint:ignore — micro/hero spacing
                    }
                }
            }
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .shadow(color: .black.opacity(0.06), radius: 8, x: 0, y: 3)
    }
}

private struct CheckpointRow: View {
    let checkpoint: GoalCheckpointItem

    private var iconName: String {
        if checkpoint.isStartingPoint { return "flag.fill" }
        return (checkpoint.isOnTrack == true) ? "checkmark.circle.fill" : "exclamationmark.circle.fill"
    }

    private var iconColor: Color {
        if checkpoint.isStartingPoint { return .brandPrimary }
        return (checkpoint.isOnTrack == true) ? .brandSuccess : .brandWarning
    }

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: iconName)
                .font(.headingMedium).fontWeight(.regular)
                .foregroundStyle(iconColor)
                .frame(width: 32)

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(checkpoint.formattedDate)
                        .font(.labelSmall)
                        .foregroundStyle(Color.textPrimary)
                    if checkpoint.isStartingPoint {
                        Text(String(localized: "goal.progress.checkpoint.start"))
                            .font(.captionXSmall).fontWeight(.bold)
                            .foregroundStyle(Color.brandPrimary)
                            .padding(.horizontal, Spacing.sm) // design-lint:ignore — micro/hero spacing
                            .padding(.vertical, 2) // design-lint:ignore — micro/hero spacing
                            .background(Color.brandPrimary.opacity(0.12))
                            .clipShape(Capsule())
                    }
                }
                if !checkpoint.isStartingPoint, let projected = checkpoint.projectedValue {
                    Text(String(format: String(localized: "goal.progress.checkpoint.projected"), String(format: "%.1f", projected)))
                        .font(.captionXSmall)
                        .foregroundStyle(Color.textSecondary)
                }
            }

            Spacer()

            if let actual = checkpoint.actualValue {
                Text(String(format: "%.1f", actual))
                    .font(.headingSmall).fontWeight(.bold)
                    .foregroundStyle(Color.textPrimary)
            }
        }
        .padding(.vertical, Spacing.md) // design-lint:ignore — micro/hero spacing
    }
}

// MARK: - Empty State

private struct EmptyProgressState: View {
    var body: some View {
        EmptyState(
            icon: "chart.line.uptrend.xyaxis",
            title: String(localized: "goal.progress.empty.title"),
            message: String(localized: "goal.progress.empty.message")
        )
    }
}
