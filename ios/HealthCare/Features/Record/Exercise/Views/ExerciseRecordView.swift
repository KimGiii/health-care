import SwiftUI

// MARK: - Main View

struct ExerciseRecordView: View {
    @StateObject private var viewModel = ExerciseRecordViewModel()
    @EnvironmentObject private var container: AppContainer

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            ScrollView(showsIndicators: false) {
                VStack(spacing: 0) {
                    // C — wave hero + 원형 링
                    ExerciseHeroSection(viewModel: viewModel)

                    // A — 주간 통계 스트립
                    WeeklyStatsStrip(viewModel: viewModel)

                    // A — 세션 카드 리스트
                    SessionListSection(viewModel: viewModel)
                }
            }
            .ignoresSafeArea(edges: .top)
            .background(Color.backgroundPage)
            .refreshable {
                await viewModel.loadSessions(apiClient: container.apiClient)
                viewModel.errorMessage = nil
            }

            // + FAB
            if !viewModel.sessions.isEmpty {
                Button {
                    viewModel.showAddSession = true
                } label: {
                    Image(systemName: "plus")
                        .font(.system(size: 22, weight: .semibold)) // design-lint:ignore — SF Symbol or special
                        .foregroundStyle(.white)
                        .frame(width: 58, height: 58)
                        .background(Color.brandPrimary)
                        .clipShape(Circle())
                        .shadow(color: Color.brandPrimary.opacity(0.45), radius: 12, x: 0, y: 6)
                }
                .padding(.trailing, Spacing.xxl) // design-lint:ignore — micro/hero spacing
                .padding(.bottom, Spacing.xxl) // design-lint:ignore — micro/hero spacing
            }
        }
        .navigationBarHidden(true)
        .sheet(isPresented: $viewModel.showAddSession) {
            AddExerciseSessionView { _ in
                Task { await viewModel.sessionAdded(apiClient: container.apiClient) }
            }
        }
        .alert(Text("오류"), isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button(String(localized: "common.ok"), role: .cancel) { viewModel.errorMessage = nil }
        } message: {
            Text(viewModel.errorMessage ?? "")
        }
        .task { await viewModel.loadSessions(apiClient: container.apiClient) }
    }
}

// MARK: - Hero Section (C 스타일)

private struct ExerciseHeroSection: View {
    @ObservedObject var viewModel: ExerciseRecordViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack(alignment: .top) {
            ExerciseWaveBackground()
                .frame(height: 320)

            VStack(spacing: 0) {
                // 상태바 여백
                Color.clear.frame(height: 56)

                // 내비게이션
                HStack {
                    Button { dismiss() } label: {
                        Image(systemName: "chevron.left")
                            .font(.cta)
                            .foregroundStyle(.white)
                            .padding(Spacing.md) // design-lint:ignore — micro/hero spacing
                            .background(.white.opacity(0.15))
                            .clipShape(Circle())
                    }

                    Spacer()

                    Text(String(localized: "exercise.title"))
                        .font(.headingMedium).fontWeight(.bold)
                        .foregroundStyle(.white)

                    Spacer()

                    Button {
                        viewModel.showAddSession = true
                    } label: {
                        Image(systemName: "plus")
                            .font(.cta)
                            .foregroundStyle(.white)
                            .padding(Spacing.md) // design-lint:ignore — micro/hero spacing
                            .background(.white.opacity(0.15))
                            .clipShape(Circle())
                    }
                }
                .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing

                Spacer(minLength: 0)

                // 원형 링 카드
                HStack(spacing: 32) {
                    ExerciseRingView(
                        label: String(localized: "exercise.stats.volume"),
                        value: viewModel.weeklyVolume,
                        unit: "kg",
                        progress: viewModel.volumeProgress,
                        color: Color.brandAccent
                    )
                    ExerciseRingView(
                        label: String(localized: "exercise.stats.calories"),
                        value: viewModel.weeklyCalories,
                        unit: "kcal",
                        progress: viewModel.calorieProgress,
                        color: Color(hex: "#74C69D")
                    )
                }
                .padding(.horizontal, Spacing.xxl) // design-lint:ignore — micro/hero spacing
                .padding(.vertical, Spacing.xl) // design-lint:ignore — micro/hero spacing
                .background(
                    RoundedRectangle(cornerRadius: Radius.xl)
                        .fill(Color.surfaceCard)
                        .shadow(color: .black.opacity(0.10), radius: 16, x: 0, y: 6)
                )
                .padding(.horizontal, Spacing.xxl) // design-lint:ignore — micro/hero spacing
                .padding(.bottom, Spacing.xxxl) // design-lint:ignore — micro/hero spacing
            }
        }
    }
}

// MARK: - Wave Background

private struct ExerciseWaveBackground: View {
    var body: some View {
        GeometryReader { geo in
            ZStack {
                Color.brandPrimary

                Ellipse()
                    .fill(Color.brandSecondary.opacity(0.50))
                    .frame(width: geo.size.width * 0.72, height: geo.size.height * 0.62)
                    .offset(x: geo.size.width * 0.22, y: -geo.size.height * 0.10)
                    .rotationEffect(.degrees(-16))

                ExerciseWaveCurve()
                    .fill(Color.backgroundPage)
                    .frame(height: 72)
                    .frame(maxWidth: .infinity)
                    .offset(y: geo.size.height - 36)
            }
        }
    }
}

private struct ExerciseWaveCurve: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 0, y: rect.height))
        p.addCurve(
            to: CGPoint(x: rect.width, y: rect.height),
            control1: CGPoint(x: rect.width * 0.28, y: 0),
            control2: CGPoint(x: rect.width * 0.72, y: rect.height * 0.55)
        )
        p.addLine(to: CGPoint(x: rect.width, y: rect.height))
        p.addLine(to: CGPoint(x: 0, y: rect.height))
        p.closeSubpath()
        return p
    }
}

// MARK: - Circular Ring

private struct ExerciseRingView: View {
    let label: String
    let value: Double
    let unit: String
    let progress: Double
    let color: Color

    private var displayValue: String {
        value >= 1_000
            ? String(format: "%.0f", value / 1_000) + "k"
            : String(format: "%.0f", value)
    }

    var body: some View {
        VStack(spacing: 10) {
            ZStack {
                Circle()
                    .stroke(color.opacity(0.15), lineWidth: 10)
                Circle()
                    .trim(from: 0, to: progress)
                    .stroke(color, style: StrokeStyle(lineWidth: 10, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                    .animation(.easeInOut(duration: 0.9), value: progress)

                VStack(spacing: 2) {
                    Text(displayValue)
                        .font(.numeralMedium).fontWeight(.bold)
                        .foregroundStyle(Color.textPrimary)
                    Text(unit)
                        .font(.captionXSmall)
                        .foregroundStyle(Color.textSecondary)
                }
            }
            .frame(width: 96, height: 96)

            Text(label)
                .font(.labelSmall)
                .foregroundStyle(Color.textSecondary)
        }
    }
}

// MARK: - Weekly Stats Strip (A 스타일)

private struct WeeklyStatsStrip: View {
    @ObservedObject var viewModel: ExerciseRecordViewModel

    var body: some View {
        HStack(spacing: 0) {
            statCell(
                label: String(localized: "exercise.detail.totalVolume"),
                value: viewModel.weeklyVolume > 0
                    ? String(format: "%.0fkg", viewModel.weeklyVolume)
                    : "—"
            )

            Divider().frame(height: 36)

            statCell(
                label: String(localized: "exercise.detail.burnedKcal"),
                value: viewModel.weeklyCalories > 0
                    ? String(format: "%.0fkcal", viewModel.weeklyCalories)
                    : "—"
            )

            Divider().frame(height: 36)

            statCell(
                label: String(localized: "exercise.stats.days"),
                value: viewModel.weeklyWorkoutDays > 0
                    ? String(format: String(localized: "exercise.stats.days.value"), viewModel.weeklyWorkoutDays)
                    : "—"
            )
        }
        .padding(.vertical, Spacing.lg) // design-lint:ignore — micro/hero spacing
        .background(Color.surfaceCard)
        .overlay(
            Rectangle()
                .fill(Color(uiColor: .separator))
                .frame(height: 0.5),
            alignment: .bottom
        )
    }

    private func statCell(label: String, value: String) -> some View {
        VStack(spacing: 4) {
            Text(label)
                .font(.captionXSmall)
                .foregroundStyle(Color.textSecondary)
            Text(value)
                .font(.numeralMedium).fontWeight(.bold)
                .foregroundStyle(Color.brandAccent)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Session List Section (A 스타일)

private struct SessionListSection: View {
    @ObservedObject var viewModel: ExerciseRecordViewModel
    @EnvironmentObject private var container: AppContainer

    var body: some View {
        if viewModel.isLoading && viewModel.sessions.isEmpty {
            ProgressView()
                .frame(maxWidth: .infinity)
                .padding(.top, 60) // design-lint:ignore — micro/hero spacing
        } else if viewModel.sessions.isEmpty {
            EmptyExerciseState {
                viewModel.showAddSession = true
            }
        } else {
            VStack(spacing: 0) {
                // 섹션 헤더
                HStack {
                    Text(String(localized: "exercise.title"))
                        .font(.headingSmall).fontWeight(.bold)
                        .foregroundStyle(Color.textHeadline)
                    Spacer()
                    Text(String(format: String(localized: "exercise.list.totalCount"), viewModel.sessions.count))
                        .font(.bodySmall)
                        .foregroundStyle(Color.textSecondary)
                }
                .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing
                .padding(.top, Spacing.xl) // design-lint:ignore — micro/hero spacing
                .padding(.bottom, Spacing.md) // design-lint:ignore — micro/hero spacing

                // 카드 리스트
                LazyVStack(spacing: 10) {
                    ForEach(viewModel.sessions) { session in
                        NavigationLink {
                            ExerciseSessionDetailView(sessionId: session.sessionId)
                        } label: {
                            SessionCard(session: session)
                        }
                        .buttonStyle(.plain)
                        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                            Button(role: .destructive) {
                                Task {
                                    await viewModel.deleteSession(
                                        id: session.sessionId,
                                        apiClient: container.apiClient
                                    )
                                }
                            } label: {
                                Label(String(localized: "common.delete.button"), systemImage: "trash")
                            }
                        }
                    }
                }
                .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
                .padding(.bottom, 100) // FAB 여백 // design-lint:ignore — micro/hero spacing
            }
        }
    }
}

// MARK: - Session Card (A 스타일)

struct SessionCard: View {
    let session: SessionSummary

    var body: some View {
        HStack(spacing: 14) {
            // 날짜 배지
            DateBadge(dateString: session.sessionDate)

            // 본문
            VStack(alignment: .leading, spacing: 7) {
                Text(session.formattedDate)
                    .font(.headingSmall)
                    .foregroundStyle(Color.textHeadline)

                HStack(spacing: 14) {
                    if let vol = session.totalVolumeKg {
                        statChip(
                            icon: "figure.strengthtraining.traditional",
                            value: String(format: "%.0fkg", vol),
                            color: Color.brandAccent
                        )
                    }
                    if let cal = session.caloriesBurned {
                        statChip(
                            icon: "flame.fill",
                            value: String(format: "%.0fkcal", cal),
                            color: .orange
                        )
                    }
                    if let dur = session.durationMinutes {
                        statChip(
                            icon: "clock",
                            value: String(format: String(localized: "exercise.set.duration.min"), dur),
                            color: Color.textSecondary
                        )
                    }
                }
            }

            Spacer()

            Image(systemName: "chevron.right")
                .font(.bodySmall).fontWeight(.medium)
                .foregroundStyle(Color.textSecondary.opacity(0.5))
        }
        .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
        .padding(.vertical, Spacing.lg) // design-lint:ignore — micro/hero spacing
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .shadow(color: .black.opacity(0.05), radius: 6, x: 0, y: 2)
    }

    private func statChip(icon: String, value: String, color: Color) -> some View {
        HStack(spacing: 4) {
            Image(systemName: icon)
                .font(.captionXSmall)
                .foregroundStyle(color)
            Text(value)
                .font(.caption).fontWeight(.medium)
                .foregroundStyle(Color.textSecondary)
        }
    }
}

// MARK: - Date Badge

struct DateBadge: View {
    let dateString: String

    private var parts: [String] {
        dateString.split(separator: "-").map(String.init)
    }

    var body: some View {
        VStack(spacing: 1) {
            Text(parts.count >= 2 ? String(format: String(localized: "exercise.month.format"), Int(parts[1]) ?? 0) : "")
                .font(.captionXSmall)
                .foregroundStyle(Color.brandAccent)
            Text(parts.count >= 3 ? parts[2] : "")
                .font(.headingLarge)
                .foregroundStyle(Color.brandAccent)
        }
        .frame(width: 52, height: 60)
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.md))
    }
}

// MARK: - Empty State

private struct EmptyExerciseState: View {
    let onTap: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "figure.strengthtraining.traditional")
                .font(.system(size: 60)) // design-lint:ignore — SF Symbol or special
                .foregroundStyle(Color.brandAccent.opacity(0.45))

            VStack(spacing: 6) {
                Text(String(localized: "exercise.empty.title"))
                    .font(.headingMedium).fontWeight(.bold)
                    .foregroundStyle(Color.textHeadline)
                Text(String(localized: "exercise.empty.message"))
                    .font(.bodyMedium)
                    .foregroundStyle(Color.textSecondary)
                    .multilineTextAlignment(.center)
                    .lineSpacing(3)
            }

            Button(action: onTap) {
                Text(String(localized: "exercise.empty.action"))
                    .font(.bodyLarge).fontWeight(.semibold)
                    .foregroundStyle(.white)
                    .padding(.horizontal, Spacing.xxl) // design-lint:ignore — micro/hero spacing
                    .padding(.vertical, Spacing.lg) // design-lint:ignore — micro/hero spacing
                    .background(Color.brandPrimary)
                    .clipShape(Capsule())
                    .shadow(color: Color.brandPrimary.opacity(0.35), radius: 10, x: 0, y: 4)
            }
        }
        .padding(.top, 48) // design-lint:ignore — micro/hero spacing
        .padding(.horizontal, Spacing.xxl) // design-lint:ignore — micro/hero spacing
    }
}
