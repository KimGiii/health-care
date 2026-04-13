import SwiftUI

// MARK: - Detail View

struct ExerciseSessionDetailView: View {
    let sessionId: Int

    @StateObject private var viewModel = ExerciseSessionDetailViewModel()
    @EnvironmentObject private var container: AppContainer
    @Environment(\.dismiss) private var dismiss
    @State private var showDeleteConfirm = false

    var body: some View {
        ZStack {
            Color.surfaceGrouped.ignoresSafeArea()

            Group {
                if viewModel.isLoading {
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if let session = viewModel.session {
                    mainContent(session: session)
                } else if let error = viewModel.errorMessage {
                    errorState(message: error)
                }
            }
        }
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    showDeleteConfirm = true
                } label: {
                    Image(systemName: "trash")
                        .foregroundStyle(Color.brandDanger)
                }
            }
        }
        .confirmationDialog("운동 기록을 삭제할까요?", isPresented: $showDeleteConfirm, titleVisibility: .visible) {
            Button("삭제", role: .destructive) {
                Task {
                    await viewModel.deleteSession(id: sessionId, apiClient: container.apiClient)
                    dismiss()
                }
            }
            Button("취소", role: .cancel) {}
        }
        .task { await viewModel.loadSession(id: sessionId, apiClient: container.apiClient) }
    }

    // MARK: - Main Content

    @ViewBuilder
    private func mainContent(session: SessionDetail) -> some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 16) {
                // 헤더 — 날짜 + 통계
                detailHeader(session: session)

                // PR 배너
                let prSets = session.sets.filter(\.personalRecord)
                if !prSets.isEmpty {
                    prBanner(sets: prSets)
                        .padding(.horizontal, 16)
                }

                // 메모
                if let notes = session.notes, !notes.isEmpty {
                    noteCard(text: notes)
                        .padding(.horizontal, 16)
                }

                // 운동 그룹
                VStack(spacing: 10) {
                    ForEach(Array(session.setsByExercise.enumerated()), id: \.offset) { _, group in
                        ExerciseGroupCard(name: group.exerciseName, sets: group.sets)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 32)
            }
        }
    }

    // MARK: - Detail Header

    private func detailHeader(session: SessionDetail) -> some View {
        ZStack(alignment: .top) {
            // 그린 헤더 배경
            DetailHeaderBackground()
                .frame(height: 200)

            VStack(spacing: 0) {
                Color.clear.frame(height: 12)

                // 날짜
                Text(session.formattedDate)
                    .font(.system(size: 20, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
                    .padding(.top, 8)

                Spacer(minLength: 0)

                // 통계 카드
                HStack(spacing: 0) {
                    detailStat(
                        icon: "scalemass.fill",
                        value: session.totalVolumeKg.map { String(format: "%.0f", $0) } ?? "—",
                        unit: "kg",
                        label: "총 볼륨",
                        color: Color.brandAccent
                    )
                    Divider().frame(height: 44)
                    detailStat(
                        icon: "flame.fill",
                        value: session.caloriesBurned.map { String(format: "%.0f", $0) } ?? "—",
                        unit: "kcal",
                        label: "소모 칼로리",
                        color: .orange
                    )
                    Divider().frame(height: 44)
                    detailStat(
                        icon: "clock.fill",
                        value: session.durationMinutes.map { "\($0)" } ?? "—",
                        unit: "분",
                        label: "운동 시간",
                        color: Color.brandPrimary
                    )
                    Divider().frame(height: 44)
                    detailStat(
                        icon: "list.number",
                        value: "\(session.sets.count)",
                        unit: "세트",
                        label: "총 세트",
                        color: Color.brandPrimary
                    )
                }
                .padding(.vertical, 16)
                .background(.white)
                .clipShape(RoundedRectangle(cornerRadius: 18))
                .shadow(color: .black.opacity(0.10), radius: 12, x: 0, y: 4)
                .padding(.horizontal, 20)
                .padding(.bottom, 28)
            }
        }
    }

    private func detailStat(icon: String, value: String, unit: String, label: String, color: Color) -> some View {
        VStack(spacing: 5) {
            Image(systemName: icon)
                .font(.system(size: 14))
                .foregroundStyle(color)
            HStack(alignment: .lastTextBaseline, spacing: 2) {
                Text(value)
                    .font(.system(size: 17, weight: .bold, design: .rounded))
                    .foregroundStyle(Color.textPrimary)
                Text(unit)
                    .font(.system(size: 10))
                    .foregroundStyle(Color.textSecondary)
            }
            Text(label)
                .font(.system(size: 10))
                .foregroundStyle(Color.textSecondary)
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: - PR Banner

    private func prBanner(sets: [SetDetail]) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "trophy.fill")
                .font(.system(size: 20))
                .foregroundStyle(.yellow)
            VStack(alignment: .leading, spacing: 3) {
                Text("개인 최고 기록 달성! 🎉")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Color.textPrimary)
                Text(sets.map(\.displayExerciseName).joined(separator: ", "))
                    .font(.system(size: 12))
                    .foregroundStyle(Color.textSecondary)
                    .lineLimit(2)
            }
            Spacer()
        }
        .padding(14)
        .background(Color(hex: "#FFFBEB"))
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Color.brandWarning.opacity(0.4), lineWidth: 1)
        )
    }

    // MARK: - Note Card

    private func noteCard(text: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "note.text")
                .foregroundStyle(Color.brandAccent)
                .padding(.top, 1)
            Text(text)
                .font(.system(size: 14))
                .foregroundStyle(Color.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
            Spacer()
        }
        .padding(14)
        .background(Color.surfacePrimary)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .shadow(color: .black.opacity(0.04), radius: 4, x: 0, y: 2)
    }

    // MARK: - Error State

    private func errorState(message: String) -> some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 44))
                .foregroundStyle(Color.brandWarning)
            Text(message)
                .font(.system(size: 15))
                .foregroundStyle(Color.textSecondary)
                .multilineTextAlignment(.center)
            Button("다시 시도") {
                Task { await viewModel.loadSession(id: sessionId, apiClient: container.apiClient) }
            }
            .foregroundStyle(Color.brandPrimary)
            .fontWeight(.semibold)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Detail Header Background

private struct DetailHeaderBackground: View {
    var body: some View {
        GeometryReader { geo in
            ZStack {
                Color.brandPrimary
                Ellipse()
                    .fill(Color.brandSecondary.opacity(0.45))
                    .frame(width: geo.size.width * 0.65, height: geo.size.height * 0.80)
                    .offset(x: geo.size.width * 0.20, y: -geo.size.height * 0.05)
                    .rotationEffect(.degrees(-12))
                DetailWaveCurve()
                    .fill(Color.surfaceGrouped)
                    .frame(height: 56)
                    .offset(y: geo.size.height - 28)
            }
        }
    }
}

private struct DetailWaveCurve: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 0, y: rect.height))
        p.addCurve(
            to: CGPoint(x: rect.width, y: rect.height),
            control1: CGPoint(x: rect.width * 0.3, y: 0),
            control2: CGPoint(x: rect.width * 0.7, y: rect.height * 0.6)
        )
        p.addLine(to: .init(x: rect.width, y: rect.height))
        p.addLine(to: .init(x: 0, y: rect.height))
        p.closeSubpath()
        return p
    }
}

// MARK: - Exercise Group Card

private struct ExerciseGroupCard: View {
    let name: String
    let sets: [SetDetail]

    var body: some View {
        VStack(spacing: 0) {
            // 그룹 헤더
            HStack {
                Text(name)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(Color.textPrimary)
                Spacer()
                Text("\(sets.count)세트")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Color.textSecondary)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.surfaceSecondary)
                    .clipShape(Capsule())
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(Color.brandLight)

            // 세트 행
            ForEach(sets) { set in
                Divider().padding(.horizontal, 16)
                SetRow(set: set)
            }
        }
        .background(Color.surfacePrimary)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .shadow(color: .black.opacity(0.05), radius: 6, x: 0, y: 2)
    }
}

// MARK: - Set Row

private struct SetRow: View {
    let set: SetDetail

    var body: some View {
        HStack(spacing: 12) {
            // 세트 번호
            Text("\(set.setNumber)")
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(Color.brandPrimary)
                .frame(width: 28, height: 28)
                .background(Color.brandSurface)
                .clipShape(Circle())

            // 내용
            Text(set.setDescription)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Color.textPrimary)

            Spacer()

            // PR 배지
            if set.personalRecord {
                HStack(spacing: 3) {
                    Image(systemName: "trophy.fill")
                        .font(.system(size: 10))
                        .foregroundStyle(.yellow)
                    Text("PR")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Color.brandWarning)
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Color.brandWarning.opacity(0.12))
                .clipShape(Capsule())
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 11)
    }
}

// MARK: - ViewModel

@MainActor
final class ExerciseSessionDetailViewModel: ObservableObject {
    @Published var session: SessionDetail?
    @Published var isLoading = false
    @Published var errorMessage: String?

    func loadSession(id: Int, apiClient: APIClient) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            session = try await apiClient.request(.getExerciseSession(id: id))
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = "운동 상세를 불러오지 못했습니다."
        }
    }

    func deleteSession(id: Int, apiClient: APIClient) async {
        do {
            try await apiClient.requestVoid(.deleteExerciseSession(id: id))
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = "삭제 중 오류가 발생했습니다."
        }
    }
}
