import SwiftUI

struct AddGoalView: View {
    @StateObject private var viewModel = AddGoalViewModel()
    @EnvironmentObject private var container: AppContainer
    @Environment(\.dismiss) private var dismiss

    let onSuccess: (GoalResponse) -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 28) {
                    GoalTypeSection(selected: $viewModel.selectedType)

                    if viewModel.selectedType.requiresTargetValue {
                        TargetValueSection(
                            type: viewModel.selectedType,
                            valueText: $viewModel.targetValueText,
                            startValueText: $viewModel.startValueText
                        )
                    }

                    TargetDateSection(
                        targetDate: $viewModel.targetDate,
                        weeklyRateText: $viewModel.weeklyRateText,
                        selectedType: viewModel.selectedType
                    )

                    if let error = viewModel.errorMessage {
                        ErrorBanner(message: error)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 24)
            }
            .background(Color.surfaceGrouped)
            .navigationTitle("목표 설정")
            .navigationBarTitleDisplayMode(.large)
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
                                await viewModel.submit(apiClient: container.apiClient) { response in
                                    onSuccess(response)
                                    dismiss()
                                }
                            }
                        }
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(viewModel.isValid ? Color.brandPrimary : Color.textSecondary)
                        .disabled(!viewModel.isValid)
                    }
                }
            }
        }
    }
}

// MARK: - Goal Type Section

private struct GoalTypeSection: View {
    @Binding var selected: GoalType

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            SectionLabel(title: "목표 유형")

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(GoalType.allCases, id: \.self) { type in
                        GoalTypeCard(type: type, isSelected: selected == type) {
                            selected = type
                        }
                    }
                }
                .padding(.horizontal, 1)
                .padding(.vertical, 4)
            }
        }
    }
}

private struct GoalTypeCard: View {
    let type: GoalType
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 10) {
                Image(systemName: type.icon)
                    .font(.system(size: 22))
                    .foregroundStyle(isSelected ? .white : Color.brandPrimary)
                    .frame(width: 52, height: 52)
                    .background(
                        isSelected ? Color.brandPrimary : Color.brandSurface
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 14))

                Text(type.displayName)
                    .font(.system(size: 12, weight: isSelected ? .bold : .regular))
                    .foregroundStyle(isSelected ? Color.brandPrimary : Color.textSecondary)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .frame(width: 72)
            }
            .padding(.vertical, 4)
        }
        .buttonStyle(.plain)
        .animation(.easeInOut(duration: 0.15), value: isSelected)
    }
}

// MARK: - Target Value Section

private struct TargetValueSection: View {
    let type: GoalType
    @Binding var valueText: String
    @Binding var startValueText: String

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            SectionLabel(title: "목표값")

            FormCard {
                VStack(spacing: 16) {
                    NumericField(
                        label: "목표 \(type.displayName)",
                        placeholder: "예: 70.0",
                        unit: type.displayUnit,
                        text: $valueText
                    )

                    Divider()

                    NumericField(
                        label: "현재 값 (선택)",
                        placeholder: type.displayUnit.isEmpty ? "현재 값 입력" : "현재 \(type.displayUnit) 입력",
                        unit: type.displayUnit,
                        text: $startValueText
                    )
                }
            }
        }
    }
}

// MARK: - Target Date Section

private struct TargetDateSection: View {
    @Binding var targetDate: Date
    @Binding var weeklyRateText: String
    let selectedType: GoalType

    private let presets: [(label: String, days: Int)] = [
        ("4주", 28), ("8주", 56), ("12주", 84), ("24주", 168)
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            SectionLabel(title: "목표 날짜")

            HStack(spacing: 8) {
                ForEach(presets, id: \.days) { preset in
                    PresetChip(label: preset.label) {
                        targetDate = Calendar.current.date(
                            byAdding: .day, value: preset.days, to: Date()
                        ) ?? Date()
                    }
                }
            }

            FormCard {
                DatePicker(
                    "목표 날짜",
                    selection: $targetDate,
                    in: Date()...,
                    displayedComponents: .date
                )
                .datePickerStyle(.compact)
                .environment(\.locale, Locale(identifier: "ko_KR"))
            }

            if selectedType.supportsWeeklyRateTarget {
                FormCard {
                    NumericField(
                        label: "주간 목표 변화량 (선택)",
                        placeholder: selectedType == .BODY_RECOMPOSITION ? "예: 0.25" : "예: 0.5",
                        unit: selectedType.weeklyRateDisplayUnit,
                        text: $weeklyRateText
                    )
                }
            }
        }
    }
}

// MARK: - Shared Components

private struct SectionLabel: View {
    let title: String

    var body: some View {
        Text(title)
            .font(.system(size: 15, weight: .semibold))
            .foregroundStyle(Color.textPrimary)
    }
}

private struct FormCard<Content: View>: View {
    let content: () -> Content

    init(@ViewBuilder content: @escaping () -> Content) {
        self.content = content
    }

    var body: some View {
        content()
            .padding(16)
            .background(Color.surfacePrimary)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .shadow(color: .black.opacity(0.04), radius: 6, x: 0, y: 2)
    }
}

private struct NumericField: View {
    let label: String
    let placeholder: String
    let unit: String
    @Binding var text: String

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 3) {
                Text(label)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Color.textSecondary)
                TextField(placeholder, text: $text)
                    .keyboardType(.decimalPad)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Color.textPrimary)
            }
            Spacer()
            if !unit.isEmpty {
                Text(unit)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Color.textSecondary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(Color.surfaceSecondary)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
            }
        }
    }
}

private struct PresetChip: View {
    let label: String
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            Text(label)
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

private struct ErrorBanner: View {
    let message: String

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(Color.brandDanger)
            Text(message)
                .font(.system(size: 13))
                .foregroundStyle(Color.brandDanger)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.brandDanger.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}
