import SwiftUI

struct EditGoalView: View {
    @StateObject private var viewModel: EditGoalViewModel
    @EnvironmentObject private var container: AppContainer
    @Environment(\.dismiss) private var dismiss

    let onSuccess: () -> Void

    init(progress: GoalProgressResponse, onSuccess: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: EditGoalViewModel(progress: progress))
        self.onSuccess = onSuccess
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 28) {
                    GoalTypeInfoSection(goalType: viewModel.goalType)

                    if viewModel.goalType.requiresTargetValue {
                        EditTargetValueSection(
                            type: viewModel.goalType,
                            valueText: $viewModel.targetValueText
                        )
                    }

                    EditTargetDateSection(
                        targetDate: $viewModel.targetDate,
                        weeklyRateText: $viewModel.weeklyRateText,
                        goalType: viewModel.goalType
                    )

                    if let error = viewModel.errorMessage {
                        EditErrorBanner(message: error)
                    }
                }
                .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing
                .padding(.vertical, Spacing.xxl) // design-lint:ignore — micro/hero spacing
            }
            .background(Color.surfaceGrouped)
            .navigationTitle("목표 수정")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("취소") { dismiss() }
                        .foregroundStyle(Color.textSecondary)
                }
                ToolbarItem(placement: .confirmationAction) {
                    if viewModel.isLoading {
                        ProgressView()
                    } else {
                        Button("저장") {
                            Task {
                                await viewModel.submit(apiClient: container.apiClient) {
                                    onSuccess()
                                    dismiss()
                                }
                            }
                        }
                        .font(.headingSmall)
                        .foregroundStyle(viewModel.isValid ? Color.brandPrimary : Color.textSecondary)
                        .disabled(!viewModel.isValid)
                    }
                }
            }
        }
    }
}

// MARK: - Goal Type Info (read-only — cannot change type on edit)

private struct GoalTypeInfoSection: View {
    let goalType: GoalType

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: goalType.icon)
                .font(.headingLarge)
                .foregroundStyle(.white)
                .frame(width: 52, height: 52)
                .background(goalType.accentColor)
                .clipShape(RoundedRectangle(cornerRadius: Radius.lg))

            VStack(alignment: .leading, spacing: 4) {
                Text(goalType.displayName)
                    .font(.bodyLarge).fontWeight(.semibold)
                    .foregroundStyle(Color.textPrimary)
                Text("목표 유형은 변경할 수 없습니다")
                    .font(.bodySmall)
                    .foregroundStyle(Color.textSecondary)
            }
            Spacer()
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .background(Color.surfacePrimary)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .shadow(color: .black.opacity(0.04), radius: 6, x: 0, y: 2)
    }
}

// MARK: - Edit Target Value

private struct EditTargetValueSection: View {
    let type: GoalType
    @Binding var valueText: String

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("목표값")
                .font(.headingSmall)
                .foregroundStyle(Color.textPrimary)

            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text("목표 \(type.displayName)")
                        .font(.caption).fontWeight(.medium)
                        .foregroundStyle(Color.textSecondary)
                    TextField("예: 70.0", text: $valueText)
                        .keyboardType(.decimalPad)
                        .font(.bodyLarge).fontWeight(.semibold)
                        .foregroundStyle(Color.textPrimary)
                }
                Spacer()
                if !type.displayUnit.isEmpty {
                    Text(type.displayUnit)
                        .font(.bodyMedium).fontWeight(.medium)
                        .foregroundStyle(Color.textSecondary)
                        .padding(.horizontal, Spacing.md) // design-lint:ignore — micro/hero spacing
                        .padding(.vertical, Spacing.sm) // design-lint:ignore — micro/hero spacing
                        .background(Color.surfaceSecondary)
                        .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
                }
            }
            .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
            .background(Color.surfacePrimary)
            .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
            .shadow(color: .black.opacity(0.04), radius: 6, x: 0, y: 2)
        }
    }
}

// MARK: - Edit Target Date

private struct EditTargetDateSection: View {
    @Binding var targetDate: Date
    @Binding var weeklyRateText: String
    let goalType: GoalType

    private let presets: [(label: String, days: Int)] = [
        ("4주", 28), ("8주", 56), ("12주", 84), ("24주", 168)
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("목표 날짜")
                .font(.headingSmall)
                .foregroundStyle(Color.textPrimary)

            HStack(spacing: 8) {
                ForEach(presets, id: \.days) { preset in
                    Button(action: {
                        targetDate = Calendar.current.date(
                            byAdding: .day, value: preset.days, to: Date()
                        ) ?? Date()
                    }) {
                        Text(preset.label)
                            .font(.bodySmall).fontWeight(.medium)
                            .foregroundStyle(Color.brandPrimary)
                            .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
                            .padding(.vertical, 7) // design-lint:ignore — micro/hero spacing
                            .background(Color.surfaceCard)
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }

            DatePicker(
                "목표 날짜",
                selection: $targetDate,
                in: Date()...,
                displayedComponents: .date
            )
            .datePickerStyle(.compact)
            .environment(\.locale, LocaleManager.resolvedLocale)
            .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
            .background(Color.surfacePrimary)
            .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
            .shadow(color: .black.opacity(0.04), radius: 6, x: 0, y: 2)

            if goalType.supportsWeeklyRateTarget {
                HStack {
                    VStack(alignment: .leading, spacing: 3) {
                        Text("주간 목표 변화량 (선택)")
                            .font(.caption).fontWeight(.medium)
                            .foregroundStyle(Color.textSecondary)
                        TextField(
                            goalType == .BODY_RECOMPOSITION ? "예: 0.25" : "예: 0.5",
                            text: $weeklyRateText
                        )
                        .keyboardType(.decimalPad)
                        .font(.bodyLarge).fontWeight(.semibold)
                        .foregroundStyle(Color.textPrimary)
                    }
                    Spacer()
                    Text(goalType.weeklyRateDisplayUnit)
                        .font(.bodyMedium).fontWeight(.medium)
                        .foregroundStyle(Color.textSecondary)
                        .padding(.horizontal, Spacing.md) // design-lint:ignore — micro/hero spacing
                        .padding(.vertical, Spacing.sm) // design-lint:ignore — micro/hero spacing
                        .background(Color.surfaceSecondary)
                        .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
                }
                .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
                .background(Color.surfacePrimary)
                .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
                .shadow(color: .black.opacity(0.04), radius: 6, x: 0, y: 2)
            }
        }
    }
}

// MARK: - Error Banner

private struct EditErrorBanner: View {
    let message: String

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(Color.brandDanger)
            Text(message)
                .font(.bodySmall)
                .foregroundStyle(Color.brandDanger)
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.brandDanger.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: Radius.md))
    }
}
