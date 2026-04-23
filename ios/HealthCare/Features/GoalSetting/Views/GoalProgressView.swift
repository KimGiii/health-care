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
            Color.surfaceGrouped.ignoresSafeArea()

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
                        .padding(.horizontal, 20)
                        .padding(.top, 20)
                        .padding(.bottom, 40)
                    } else if !viewModel.isLoading {
                        EmptyProgressState()
                            .padding(.top, 60)
                    }
                }
            }
            .ignoresSafeArea(edges: .top)
            .refreshable { await viewModel.load(apiClient: container.apiClient) }
        }
        .navigationBarHidden(true)
        .sheet(isPresented: $showEditSheet) {
            if let progress = viewModel.progress {
                EditGoalView(progress: progress) {
                    Task { await viewModel.load(apiClient: container.apiClient) }
                }
            }
        }
        .alert("오류", isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button("확인", role: .cancel) {}
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
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(.white)
                            .padding(10)
                            .background(.white.opacity(0.15))
                            .clipShape(Circle())
                    }
                    Spacer()
                    Text("목표 진행률")
                        .font(.system(size: 18, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                    Spacer()
                    if let onEdit {
                        Button(action: onEdit) {
                            Image(systemName: "pencil")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundStyle(.white)
                                .padding(10)
                                .background(.white.opacity(0.15))
                                .clipShape(Circle())
                        }
                    } else {
                        Color.clear.frame(width: 40, height: 40)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 56)

                if isLoading {
                    ProgressView().tint(.white).padding(.top, 50)
                } else if let p = progress {
                    HeroProgressRing(progress: p).padding(.top, 20)
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
                    .fill(Color.surfaceGrouped)
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
                        .font(.system(size: 26, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                    Text("달성")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(.white.opacity(0.65))
                }
            }

            VStack(alignment: .leading, spacing: 10) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(progress.goalType.emoji + " " + progress.goalType.displayName)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.white)
                    Text("목표: \(progress.formattedValue(progress.targetValue))")
                        .font(.system(size: 13))
                        .foregroundStyle(.white.opacity(0.75))
                }

                HStack(spacing: 5) {
                    Image(systemName: progress.trackingIcon)
                        .font(.system(size: 11))
                    Text(progress.trackingStatusLabel)
                        .font(.system(size: 12, weight: .semibold))
                }
                .foregroundStyle(ringColor)
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(.white.opacity(0.15))
                .clipShape(Capsule())

                if let days = progress.daysRemaining, days >= 0 {
                    HStack(spacing: 4) {
                        Image(systemName: "calendar").font(.system(size: 10))
                        Text("D-\(days)").font(.system(size: 11, weight: .semibold))
                    }
                    .foregroundStyle(.white.opacity(0.8))
                }
            }
        }
        .padding(.horizontal, 28)
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
                .font(.system(size: 22))
                .foregroundStyle(statusColor)
                .frame(width: 48, height: 48)
                .background(statusColor.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 12))

            VStack(alignment: .leading, spacing: 3) {
                Text(progress.trackingStatusLabel)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(Color.textPrimary)
                Text(progress.isOnTrack
                     ? "현재 페이스로 목표를 달성할 수 있어요"
                     : "목표 날짜까지 페이스를 높여보세요")
                    .font(.system(size: 13))
                    .foregroundStyle(Color.textSecondary)
            }
            Spacer()
        }
        .padding(16)
        .background(Color.surfacePrimary)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .shadow(color: .black.opacity(0.06), radius: 8, x: 0, y: 3)
    }
}

// MARK: - Value Progress Card

private struct ValueProgressCard: View {
    let progress: GoalProgressResponse

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("수치 변화")
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(Color.textPrimary)

            HStack(spacing: 0) {
                ValueColumn(
                    label: "시작",
                    value: progress.formattedValue(progress.startValue),
                    color: Color.textSecondary
                )
                Spacer()
                Image(systemName: "arrow.right")
                    .font(.system(size: 12))
                    .foregroundStyle(Color.textSecondary)
                Spacer()
                ValueColumn(
                    label: "현재",
                    value: progress.formattedValue(progress.currentValue),
                    color: .brandPrimary,
                    isBold: true
                )
                Spacer()
                Image(systemName: "arrow.right")
                    .font(.system(size: 12))
                    .foregroundStyle(Color.textSecondary)
                Spacer()
                ValueColumn(
                    label: "목표",
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
        .padding(18)
        .background(Color.surfacePrimary)
        .clipShape(RoundedRectangle(cornerRadius: 16))
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
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(Color.textSecondary)
            Text(value)
                .font(.system(size: 15, weight: isBold ? .bold : .semibold))
                .foregroundStyle(color)
        }
    }
}

// MARK: - Timeline Card

private struct TimelineCard: View {
    let progress: GoalProgressResponse

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("일정")
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(Color.textPrimary)

            HStack(spacing: 0) {
                TimelineItem(
                    icon: "calendar.badge.clock",
                    label: "마감일",
                    value: progress.formattedTargetDate,
                    color: Color.brandPrimary
                )
                Divider().frame(height: 44)
                TimelineItem(
                    icon: "clock.arrow.trianglehead.counterclockwise.rotate.90",
                    label: "남은 날",
                    value: progress.daysRemaining.map { $0 >= 0 ? "D-\($0)" : "기간 초과" } ?? "-",
                    color: daysColor
                )
                Divider().frame(height: 44)
                TimelineItem(
                    icon: "flag.checkered",
                    label: "예상 완료",
                    value: progress.formattedProjectedDate,
                    color: Color.brandAccent
                )
            }
        }
        .padding(18)
        .background(Color.surfacePrimary)
        .clipShape(RoundedRectangle(cornerRadius: 16))
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
                .font(.system(size: 16))
                .foregroundStyle(color)
            Text(value)
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(color)
                .multilineTextAlignment(.center)
            Text(label)
                .font(.system(size: 10, weight: .medium))
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
                Text("기록 히스토리")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(Color.textPrimary)
                Spacer()
                Text("\(checkpoints.count)개")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Color.textSecondary)
            }

            VStack(spacing: 1) {
                ForEach(Array(sorted.enumerated()), id: \.offset) { _, cp in
                    CheckpointRow(checkpoint: cp)
                    if cp.checkpointDate != sorted.last?.checkpointDate {
                        Divider().padding(.leading, 44)
                    }
                }
            }
        }
        .padding(18)
        .background(Color.surfacePrimary)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .shadow(color: .black.opacity(0.06), radius: 8, x: 0, y: 3)
    }
}

private struct CheckpointRow: View {
    let checkpoint: GoalCheckpointItem

    private var onTrackColor: Color {
        (checkpoint.isOnTrack == true) ? .brandSuccess : .brandWarning
    }

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: (checkpoint.isOnTrack == true) ? "checkmark.circle.fill" : "exclamationmark.circle.fill")
                .font(.system(size: 18))
                .foregroundStyle(onTrackColor)
                .frame(width: 32)

            VStack(alignment: .leading, spacing: 2) {
                Text(checkpoint.formattedDate)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Color.textPrimary)
                if let projected = checkpoint.projectedValue {
                    Text("예상: \(String(format: "%.1f", projected))")
                        .font(.system(size: 11))
                        .foregroundStyle(Color.textSecondary)
                }
            }

            Spacer()

            if let actual = checkpoint.actualValue {
                Text(String(format: "%.1f", actual))
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(Color.textPrimary)
            }
        }
        .padding(.vertical, 10)
    }
}

// MARK: - Empty State

private struct EmptyProgressState: View {
    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "chart.line.uptrend.xyaxis")
                .font(.system(size: 40))
                .foregroundStyle(Color.textSecondary.opacity(0.5))
            Text("진행률 데이터 없음")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Color.textSecondary)
            Text("신체 측정 기록을 추가하면\n목표 진행률을 확인할 수 있어요.")
                .font(.system(size: 13))
                .foregroundStyle(Color.textSecondary.opacity(0.7))
                .multilineTextAlignment(.center)
                .lineSpacing(4)
        }
    }
}
