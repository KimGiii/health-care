import SwiftUI

struct AddMeasurementView: View {
    @StateObject private var viewModel: AddMeasurementViewModel
    @EnvironmentObject private var container: AppContainer
    @Environment(\.dismiss) private var dismiss

    init(onSuccess: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: AddMeasurementViewModel(onSuccess: onSuccess))
    }

    var body: some View {
        NavigationStack {
            ScrollView(showsIndicators: false) {
                VStack(spacing: 20) {
                    dateSection
                    bodyCompositionSection
                    circumferenceSection
                    notesSection
                    submitButton
                }
                .padding(20)
                .padding(.bottom, 40)
            }
            .background(Color.surfaceGrouped)
            .navigationTitle("신체 측정 기록")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("취소") { dismiss() }
                        .foregroundStyle(Color.textSecondary)
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
        }
    }

    // MARK: - Date

    private var dateSection: some View {
        FormCard(title: "측정일") {
            DatePicker("측정일", selection: $viewModel.measuredAt, in: ...Date(), displayedComponents: .date)
                .datePickerStyle(.graphical)
                .labelsHidden()
                .tint(Color.brandPrimary)
        }
    }

    // MARK: - Body Composition

    private var bodyCompositionSection: some View {
        FormCard(title: "신체 구성") {
            VStack(spacing: 0) {
                MeasurementField(
                    icon: "scalemass.fill",
                    iconColor: Color(hex: "#2563EB"),
                    label: "체중",
                    unit: "kg",
                    text: $viewModel.weightKg
                )
                Divider().padding(.leading, 52)
                MeasurementField(
                    icon: "percent",
                    iconColor: Color(hex: "#7C3AED"),
                    label: "체지방률",
                    unit: "%",
                    text: $viewModel.bodyFatPct
                )
                Divider().padding(.leading, 52)
                MeasurementField(
                    icon: "figure.arms.open",
                    iconColor: Color.brandPrimary,
                    label: "근육량",
                    unit: "kg",
                    text: $viewModel.muscleMassKg
                )
                Divider().padding(.leading, 52)
                MeasurementField(
                    icon: "heart.fill",
                    iconColor: Color(hex: "#EA580C"),
                    label: "BMI",
                    unit: "",
                    text: $viewModel.bmi
                )
            }
        }
    }

    // MARK: - Circumference

    private var circumferenceSection: some View {
        FormCard(title: "둘레 측정") {
            MeasurementField(
                icon: "ruler",
                iconColor: Color(hex: "#DC2626"),
                label: "허리둘레",
                unit: "cm",
                text: $viewModel.waistCm
            )
        }
    }

    // MARK: - Notes

    private var notesSection: some View {
        FormCard(title: "메모") {
            TextField("특이사항이나 컨디션을 기록하세요 (선택)", text: $viewModel.notes, axis: .vertical)
                .lineLimit(3...5)
                .font(.system(size: 14))
                .foregroundStyle(Color.textPrimary)
        }
    }

    // MARK: - Submit

    private var submitButton: some View {
        Button {
            Task { await viewModel.submit(apiClient: container.apiClient) }
        } label: {
            Group {
                if viewModel.isSubmitting {
                    ProgressView().tint(.white)
                } else {
                    Text("저장하기")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(.white)
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 54)
            .background(viewModel.hasAnyValue ? Color.brandPrimary : Color.surfaceSecondary)
            .clipShape(RoundedRectangle(cornerRadius: 14))
        }
        .disabled(!viewModel.hasAnyValue || viewModel.isSubmitting)
        .animation(.easeInOut(duration: 0.2), value: viewModel.hasAnyValue)
    }
}

// MARK: - Reusable Components

private struct FormCard<Content: View>: View {
    let title: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(title)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Color.textSecondary)
                .padding(.horizontal, 4)
            VStack(alignment: .leading, spacing: 0) {
                content()
            }
            .padding(16)
            .background(Color.surfacePrimary)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .shadow(color: .black.opacity(0.05), radius: 6, x: 0, y: 2)
        }
    }
}

private struct MeasurementField: View {
    let icon: String
    let iconColor: Color
    let label: String
    let unit: String
    @Binding var text: String

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(iconColor)
                .frame(width: 32, height: 32)
                .background(iconColor.opacity(0.1))
                .clipShape(RoundedRectangle(cornerRadius: 8))

            Text(label)
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(Color.textPrimary)

            Spacer()

            HStack(spacing: 4) {
                TextField("0.0", text: $text)
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.trailing)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color.textPrimary)
                    .frame(width: 70)

                if !unit.isEmpty {
                    Text(unit)
                        .font(.system(size: 13))
                        .foregroundStyle(Color.textSecondary)
                }
            }
        }
        .padding(.vertical, 10)
    }
}
