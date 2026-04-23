import SwiftUI

struct ChangeAnalysisView: View {
    @StateObject private var viewModel = ChangeAnalysisViewModel()
    @EnvironmentObject private var container: AppContainer

    var body: some View {
        NavigationStack {
            ScrollView(showsIndicators: false) {
                VStack(spacing: 20) {
                    DateRangeSection(
                        fromDate: $viewModel.fromDate,
                        toDate: $viewModel.toDate,
                        onAnalyze: { Task { await viewModel.load(apiClient: container.apiClient) } }
                    )

                    if viewModel.isLoading {
                        ProgressView().padding(.top, 60)
                    } else if let analysis = viewModel.analysis {
                        ChangeAnalysisContent(analysis: analysis)
                    } else {
                        ChangeEmptyState()
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 40)
            }
            .background(Color.surfaceGrouped)
            .navigationTitle("변화 분석")
            .navigationBarTitleDisplayMode(.large)
            .alert("오류", isPresented: Binding(
                get: { viewModel.errorMessage != nil },
                set: { if !$0 { viewModel.errorMessage = nil } }
            )) {
                Button("확인", role: .cancel) {}
            } message: {
                Text(viewModel.errorMessage ?? "")
            }
        }
        .task { await viewModel.load(apiClient: container.apiClient) }
    }
}

// MARK: - Date Range Picker

private struct DateRangeSection: View {
    @Binding var fromDate: Date
    @Binding var toDate: Date
    let onAnalyze: () -> Void

    private let presets: [(label: String, months: Int)] = [
        ("1개월", 1), ("3개월", 3), ("6개월", 6)
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("기간 선택")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Color.textPrimary)

            HStack(spacing: 8) {
                ForEach(presets, id: \.months) { preset in
                    Button(action: {
                        toDate = Date()
                        fromDate = Calendar.current.date(
                            byAdding: .month, value: -preset.months, to: Date()
                        ) ?? Date()
                        onAnalyze()
                    }) {
                        Text(preset.label)
                            .font(.system(size: 13, weight: .medium))
                            .foregroundStyle(Color.brandPrimary)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 7)
                            .background(Color.brandSurface)
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }

            VStack(spacing: 0) {
                DatePicker("시작일", selection: $fromDate, in: ...toDate, displayedComponents: .date)
                    .environment(\.locale, Locale(identifier: "ko_KR"))
                    .padding(14)
                Divider().padding(.horizontal, 14)
                DatePicker("종료일", selection: $toDate, in: fromDate..., displayedComponents: .date)
                    .environment(\.locale, Locale(identifier: "ko_KR"))
                    .padding(14)
            }
            .background(Color.surfacePrimary)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .shadow(color: .black.opacity(0.04), radius: 6, x: 0, y: 2)

            Button(action: onAnalyze) {
                Text("분석하기")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Color.brandPrimary)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }
        }
    }
}

// MARK: - Analysis Content

private struct ChangeAnalysisContent: View {
    let analysis: ChangeAnalysisResponse

    var body: some View {
        VStack(spacing: 16) {
            BodyChangeCard(analysis: analysis)
            ExerciseActivityCard(analysis: analysis)
            if analysis.fromSnapshot != nil || analysis.toSnapshot != nil {
                SnapshotComparisonCard(analysis: analysis)
            }
        }
    }
}

// MARK: - Body Change Card

private struct BodyChangeCard: View {
    let analysis: ChangeAnalysisResponse

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 8) {
                Image(systemName: "arrow.up.arrow.down.circle.fill")
                    .foregroundStyle(Color.brandPrimary)
                Text("신체 변화")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color.textPrimary)
            }

            VStack(spacing: 12) {
                ChangeRow(label: "체중",
                          delta: analysis.formattedDelta(analysis.weightChangeKg, unit: "kg", positiveIsGood: false))
                ChangeRow(label: "체지방률",
                          delta: analysis.formattedDelta(analysis.bodyFatPctChange, unit: "%", positiveIsGood: false))
                ChangeRow(label: "근육량",
                          delta: analysis.formattedDelta(analysis.muscleMassChangeKg, unit: "kg", positiveIsGood: true))
                ChangeRow(label: "BMI",
                          delta: analysis.formattedDelta(analysis.bmiChange, unit: "", positiveIsGood: false))
                if analysis.waistChangeCm != nil {
                    ChangeRow(label: "허리 둘레",
                              delta: analysis.formattedDelta(analysis.waistChangeCm, unit: "cm", positiveIsGood: false))
                }
            }
        }
        .padding(18)
        .background(Color.surfacePrimary)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .shadow(color: .black.opacity(0.05), radius: 8, x: 0, y: 2)
    }
}

private struct ChangeRow: View {
    let label: String
    let delta: (text: String, isPositive: Bool?)

    var color: Color {
        switch delta.isPositive {
        case true:  return .green
        case false: return Color.brandDanger
        case nil:   return Color.textSecondary
        }
    }

    var body: some View {
        HStack {
            Text(label)
                .font(.system(size: 14))
                .foregroundStyle(Color.textSecondary)
            Spacer()
            Text(delta.text)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(color)
        }
    }
}

// MARK: - Exercise Activity Card

private struct ExerciseActivityCard: View {
    let analysis: ChangeAnalysisResponse

    var body: some View {
        HStack(spacing: 0) {
            VStack(spacing: 4) {
                Text("\(analysis.exerciseSessionCount)회")
                    .font(.system(size: 22, weight: .bold, design: .rounded))
                    .foregroundStyle(.cyan)
                Text("운동 세션")
                    .font(.system(size: 12))
                    .foregroundStyle(Color.textSecondary)
            }
            .frame(maxWidth: .infinity)

            Divider().frame(height: 44)

            VStack(spacing: 4) {
                Text("\(analysis.totalExerciseMinutes)분")
                    .font(.system(size: 22, weight: .bold, design: .rounded))
                    .foregroundStyle(.cyan)
                Text("총 운동 시간")
                    .font(.system(size: 12))
                    .foregroundStyle(Color.textSecondary)
            }
            .frame(maxWidth: .infinity)
        }
        .padding(18)
        .background(Color.surfacePrimary)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .shadow(color: .black.opacity(0.05), radius: 8, x: 0, y: 2)
    }
}

// MARK: - Snapshot Comparison Card

private struct SnapshotComparisonCard: View {
    let analysis: ChangeAnalysisResponse

    private let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.locale = Locale(identifier: "ko_KR")
        return f
    }()

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 8) {
                Image(systemName: "calendar.badge.clock")
                    .foregroundStyle(.purple)
                Text("측정값 비교")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color.textPrimary)
            }

            HStack(alignment: .top, spacing: 16) {
                if let from = analysis.fromSnapshot {
                    SnapshotColumn(title: "시작", snapshot: from)
                }
                if analysis.fromSnapshot != nil && analysis.toSnapshot != nil {
                    Divider()
                }
                if let to = analysis.toSnapshot {
                    SnapshotColumn(title: "종료", snapshot: to)
                }
            }
        }
        .padding(18)
        .background(Color.surfacePrimary)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .shadow(color: .black.opacity(0.05), radius: 8, x: 0, y: 2)
    }
}

private struct SnapshotColumn: View {
    let title: String
    let snapshot: ChangeAnalysisResponse.BodySnapshot

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Color.textSecondary)
            if let w = snapshot.weightKg {
                Text(String(format: "%.1f kg", w))
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(Color.textPrimary)
            }
            if let bf = snapshot.bodyFatPct {
                Text(String(format: "체지방 %.1f%%", bf))
                    .font(.system(size: 13))
                    .foregroundStyle(Color.textSecondary)
            }
            if let mm = snapshot.muscleMassKg {
                Text(String(format: "근육 %.1f kg", mm))
                    .font(.system(size: 13))
                    .foregroundStyle(Color.textSecondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct ChangeEmptyState: View {
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "waveform.path.ecg")
                .font(.system(size: 48))
                .foregroundStyle(Color.textSecondary.opacity(0.5))
            Text("기간을 선택하고 분석을 시작하세요")
                .font(.system(size: 16, weight: .medium))
                .foregroundStyle(Color.textSecondary)
                .multilineTextAlignment(.center)
        }
        .padding(.top, 60)
    }
}
