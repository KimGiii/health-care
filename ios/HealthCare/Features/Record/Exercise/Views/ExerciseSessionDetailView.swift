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
            Color.backgroundPage.ignoresSafeArea()

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
        .confirmationDialog(Text("exercise.confirm.delete"), isPresented: $showDeleteConfirm, titleVisibility: .visible) {
            Button(String(localized: "common.delete.button"), role: .destructive) {
                Task {
                    await viewModel.deleteSession(id: sessionId, apiClient: container.apiClient)
                    dismiss()
                }
            }
            Button(String(localized: "common.cancel"), role: .cancel) {}
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
                        .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
                }

                // 메모
                if let notes = session.notes, !notes.isEmpty {
                    noteCard(text: notes)
                        .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
                }

                // 운동 그룹
                VStack(spacing: 10) {
                    ForEach(Array(session.setsByExercise.enumerated()), id: \.offset) { _, group in
                        ExerciseGroupCard(name: group.exerciseName, sets: group.sets)
                    }
                }
                .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
                .padding(.bottom, Spacing.xxl) // design-lint:ignore — micro/hero spacing
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
                    .font(.numeralMedium).fontWeight(.bold)
                    .foregroundStyle(.white)
                    .padding(.top, Spacing.sm) // design-lint:ignore — micro/hero spacing

                Spacer(minLength: 0)

                // 통계 카드
                HStack(spacing: 0) {
                    detailStat(
                        icon: "scalemass.fill",
                        value: session.totalVolumeKg.map { String(format: "%.0f", $0) } ?? "—",
                        unit: "kg",
                        label: String(localized: "exercise.detail.totalVolume"),
                        color: Color.brandAccent
                    )
                    Divider().frame(height: 44)
                    detailStat(
                        icon: "flame.fill",
                        value: session.caloriesBurned.map { String(format: "%.0f", $0) } ?? "—",
                        unit: "kcal",
                        label: String(localized: "exercise.detail.burnedKcal"),
                        color: .orange
                    )
                    Divider().frame(height: 44)
                    detailStat(
                        icon: "clock.fill",
                        value: session.durationMinutes.map { "\($0)" } ?? "—",
                        unit: String(localized: "exercise.detail.unit.min"),
                        label: String(localized: "exercise.detail.duration"),
                        color: Color.brandAccent
                    )
                    Divider().frame(height: 44)
                    detailStat(
                        icon: "list.number",
                        value: "\(session.sets.count)",
                        unit: String(localized: "exercise.detail.unit.sets"),
                        label: String(localized: "exercise.detail.totalSets"),
                        color: Color.brandAccent
                    )
                }
                .padding(.vertical, Spacing.lg) // design-lint:ignore — micro/hero spacing
                .background(Color.surfaceCard)
                .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
                .shadow(color: .black.opacity(0.10), radius: 12, x: 0, y: 4)
                .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing
                .padding(.bottom, Spacing.xxl) // design-lint:ignore — micro/hero spacing
            }
        }
    }

    private func detailStat(icon: String, value: String, unit: String, label: String, color: Color) -> some View {
        VStack(spacing: 5) {
            Image(systemName: icon)
                .font(.bodyMedium)
                .foregroundStyle(color)
            HStack(alignment: .lastTextBaseline, spacing: 2) {
                Text(value)
                    .font(.cta).fontWeight(.bold)
                    .foregroundStyle(Color.textPrimary)
                Text(unit)
                    .font(.captionXSmall)
                    .foregroundStyle(Color.textSecondary)
            }
            Text(label)
                .font(.captionXSmall)
                .foregroundStyle(Color.textSecondary)
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: - PR Banner

    private func prBanner(sets: [SetDetail]) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "trophy.fill")
                .font(.system(size: 20)) // design-lint:ignore — SF Symbol or special
                .foregroundStyle(.yellow)
            VStack(alignment: .leading, spacing: 3) {
                Text("개인 최고 기록 달성! 🎉")
                    .font(.bodyMedium).fontWeight(.bold)
                    .foregroundStyle(Color.textPrimary)
                Text(sets.map(\.displayExerciseName).joined(separator: ", "))
                    .font(.caption)
                    .foregroundStyle(Color.textSecondary)
                    .lineLimit(2)
            }
            Spacer()
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .background(Color.brandWarning.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .overlay(
            RoundedRectangle(cornerRadius: Radius.lg)
                .stroke(Color.brandWarning.opacity(0.4), lineWidth: 1)
        )
    }

    // MARK: - Note Card

    private func noteCard(text: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "note.text")
                .foregroundStyle(Color.brandAccent)
                .padding(.top, 1) // design-lint:ignore — micro/hero spacing
            Text(text)
                .font(.bodyMedium)
                .foregroundStyle(Color.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
            Spacer()
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .shadow(color: .black.opacity(0.04), radius: 4, x: 0, y: 2)
    }

    // MARK: - Error State

    private func errorState(message: String) -> some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 44)) // design-lint:ignore — SF Symbol or special
                .foregroundStyle(Color.brandWarning)
            Text(message)
                .font(.bodyMedium)
                .foregroundStyle(Color.textSecondary)
                .multilineTextAlignment(.center)
            Button(String(localized: "exercise.retry")) {
                Task { await viewModel.loadSession(id: sessionId, apiClient: container.apiClient) }
            }
            .foregroundStyle(Color.brandAccent)
            .fontWeight(.semibold)
        }
        .padding(Spacing.xxl) // design-lint:ignore — micro/hero spacing
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
                    .fill(Color.backgroundPage)
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
                    .font(.headingSmall).fontWeight(.bold)
                    .foregroundStyle(Color.textPrimary)
                Spacer()
                Text(String(format: String(localized: "exercise.set.count"), sets.count))
                    .font(.caption).fontWeight(.medium)
                    .foregroundStyle(Color.textSecondary)
                    .padding(.horizontal, Spacing.sm) // design-lint:ignore — micro/hero spacing
                    .padding(.vertical, Spacing.xs) // design-lint:ignore — micro/hero spacing
                    .background(Color.backgroundPage)
                    .clipShape(Capsule())
            }
            .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
            .padding(.vertical, Spacing.md) // design-lint:ignore — micro/hero spacing
            .background(Color.surfaceCard)

            // 세트 행
            ForEach(sets) { set in
                Divider().padding(.horizontal, Spacing.lg)
                SetRow(set: set)
            }
        }
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
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
                .font(.bodySmall).fontWeight(.bold)
                .foregroundStyle(Color.brandAccent)
                .frame(width: 28, height: 28)
                .background(Color.surfaceCard)
                .clipShape(Circle())

            // 내용
            Text(set.setDescription)
                .font(.bodyLarge).fontWeight(.semibold)
                .foregroundStyle(Color.textPrimary)

            Spacer()

            // PR 배지
            if set.personalRecord {
                HStack(spacing: 3) {
                    Image(systemName: "trophy.fill")
                        .font(.captionXSmall)
                        .foregroundStyle(.yellow)
                    Text("PR")
                        .font(.captionXSmall).fontWeight(.bold)
                        .foregroundStyle(Color.brandWarning)
                }
                .padding(.horizontal, Spacing.sm) // design-lint:ignore — micro/hero spacing
                .padding(.vertical, Spacing.xs) // design-lint:ignore — micro/hero spacing
                .background(Color.brandWarning.opacity(0.12))
                .clipShape(Capsule())
            }
        }
        .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
        .padding(.vertical, 11) // design-lint:ignore — micro/hero spacing
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
            errorMessage = String(localized: "exercise.error.loadDetail")
        }
    }

    func deleteSession(id: Int, apiClient: APIClient) async {
        do {
            try await apiClient.requestVoid(.deleteExerciseSession(id: id))
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = String(localized: "exercise.error.delete")
        }
    }
}
