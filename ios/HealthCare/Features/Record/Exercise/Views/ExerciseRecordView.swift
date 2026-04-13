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
            .background(Color.surfaceGrouped)
            .refreshable { await viewModel.loadSessions(apiClient: container.apiClient) }

            // + FAB
            if !viewModel.sessions.isEmpty {
                Button {
                    viewModel.showAddSession = true
                } label: {
                    Image(systemName: "plus")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 58, height: 58)
                        .background(Color.brandPrimary)
                        .clipShape(Circle())
                        .shadow(color: Color.brandPrimary.opacity(0.45), radius: 12, x: 0, y: 6)
                }
                .padding(.trailing, 24)
                .padding(.bottom, 32)
            }
        }
        .navigationBarHidden(true)
        .sheet(isPresented: $viewModel.showAddSession) {
            AddExerciseSessionView { _ in
                Task { await viewModel.sessionAdded(apiClient: container.apiClient) }
            }
        }
        .alert("오류", isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button("확인", role: .cancel) { viewModel.errorMessage = nil }
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
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(.white)
                            .padding(10)
                            .background(.white.opacity(0.15))
                            .clipShape(Circle())
                    }

                    Spacer()

                    Text("운동 기록")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundStyle(.white)

                    Spacer()

                    Button {
                        viewModel.showAddSession = true
                    } label: {
                        Image(systemName: "plus")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(.white)
                            .padding(10)
                            .background(.white.opacity(0.15))
                            .clipShape(Circle())
                    }
                }
                .padding(.horizontal, 20)

                Spacer(minLength: 0)

                // 원형 링 카드
                HStack(spacing: 32) {
                    ExerciseRingView(
                        label: "볼륨",
                        value: viewModel.weeklyVolume,
                        unit: "kg",
                        progress: viewModel.volumeProgress,
                        color: Color.brandAccent
                    )
                    ExerciseRingView(
                        label: "칼로리",
                        value: viewModel.weeklyCalories,
                        unit: "kcal",
                        progress: viewModel.calorieProgress,
                        color: Color(hex: "#74C69D")
                    )
                }
                .padding(.horizontal, 32)
                .padding(.vertical, 20)
                .background(
                    RoundedRectangle(cornerRadius: 24)
                        .fill(.white)
                        .shadow(color: .black.opacity(0.10), radius: 16, x: 0, y: 6)
                )
                .padding(.horizontal, 28)
                .padding(.bottom, 40)
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
                    .fill(Color.surfaceGrouped)
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
                        .font(.system(size: 20, weight: .bold, design: .rounded))
                        .foregroundStyle(Color.textPrimary)
                    Text(unit)
                        .font(.system(size: 11))
                        .foregroundStyle(Color.textSecondary)
                }
            }
            .frame(width: 96, height: 96)

            Text(label)
                .font(.system(size: 13, weight: .semibold))
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
                label: "총 볼륨",
                value: viewModel.weeklyVolume > 0
                    ? String(format: "%.0fkg", viewModel.weeklyVolume)
                    : "—"
            )

            Divider().frame(height: 36)

            statCell(
                label: "소모 칼로리",
                value: viewModel.weeklyCalories > 0
                    ? String(format: "%.0fkcal", viewModel.weeklyCalories)
                    : "—"
            )

            Divider().frame(height: 36)

            statCell(
                label: "운동일",
                value: viewModel.weeklyWorkoutDays > 0
                    ? "\(viewModel.weeklyWorkoutDays)일"
                    : "—"
            )
        }
        .padding(.vertical, 16)
        .background(Color.surfacePrimary)
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
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(Color.textSecondary)
            Text(value)
                .font(.system(size: 18, weight: .bold, design: .rounded))
                .foregroundStyle(Color.brandPrimary)
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
                .padding(.top, 60)
        } else if viewModel.sessions.isEmpty {
            EmptyExerciseState {
                viewModel.showAddSession = true
            }
        } else {
            VStack(spacing: 0) {
                // 섹션 헤더
                HStack {
                    Text("운동 기록")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(Color.textPrimary)
                    Spacer()
                    Text("총 \(viewModel.sessions.count)회")
                        .font(.system(size: 13))
                        .foregroundStyle(Color.textSecondary)
                }
                .padding(.horizontal, 20)
                .padding(.top, 20)
                .padding(.bottom, 12)

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
                                Label("삭제", systemImage: "trash")
                            }
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 100) // FAB 여백
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
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color.textPrimary)

                HStack(spacing: 14) {
                    if let vol = session.totalVolumeKg {
                        statChip(
                            icon: "figure.strengthtraining.traditional",
                            value: String(format: "%.0fkg", vol),
                            color: Color.brandPrimary
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
                            value: "\(dur)분",
                            color: Color.textSecondary
                        )
                    }
                }
            }

            Spacer()

            Image(systemName: "chevron.right")
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(Color.textSecondary.opacity(0.5))
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(Color.surfacePrimary)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .shadow(color: .black.opacity(0.05), radius: 6, x: 0, y: 2)
    }

    private func statChip(icon: String, value: String, color: Color) -> some View {
        HStack(spacing: 4) {
            Image(systemName: icon)
                .font(.system(size: 11))
                .foregroundStyle(color)
            Text(value)
                .font(.system(size: 12, weight: .medium))
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
            Text(parts.count >= 2 ? "\(parts[1])월" : "")
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(Color.brandPrimary)
            Text(parts.count >= 3 ? parts[2] : "")
                .font(.system(size: 24, weight: .bold, design: .rounded))
                .foregroundStyle(Color.brandPrimary)
        }
        .frame(width: 52, height: 60)
        .background(Color.brandSurface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

// MARK: - Empty State

private struct EmptyExerciseState: View {
    let onTap: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "figure.strengthtraining.traditional")
                .font(.system(size: 60))
                .foregroundStyle(Color.brandPrimary.opacity(0.25))

            VStack(spacing: 6) {
                Text("첫 운동을 기록해보세요")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(Color.textPrimary)
                Text("운동을 기록하면 주간 볼륨과\n칼로리 목표가 채워집니다")
                    .font(.system(size: 14))
                    .foregroundStyle(Color.textSecondary)
                    .multilineTextAlignment(.center)
                    .lineSpacing(3)
            }

            Button(action: onTap) {
                Text("운동 기록 시작")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 32)
                    .padding(.vertical, 14)
                    .background(Color.brandPrimary)
                    .clipShape(Capsule())
                    .shadow(color: Color.brandPrimary.opacity(0.35), radius: 10, x: 0, y: 4)
            }
        }
        .padding(.top, 48)
        .padding(.horizontal, 32)
    }
}
