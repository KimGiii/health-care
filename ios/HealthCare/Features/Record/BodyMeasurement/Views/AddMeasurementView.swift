import SwiftUI

struct AddMeasurementView: View {
    @StateObject private var viewModel: AddMeasurementViewModel
    @EnvironmentObject private var container: AppContainer
    @Environment(\.dismiss) private var dismiss
    @State private var showSources = false

    init(initialDate: Date = Date(), onSuccess: @escaping () -> Void) {
        _viewModel = StateObject(
            wrappedValue: AddMeasurementViewModel(initialDate: initialDate, onSuccess: onSuccess)
        )
    }

    var body: some View {
        NavigationStack {
            ScrollView(showsIndicators: false) {
                VStack(spacing: 20) {
                    dateSection
                    bodyCompositionSection
                    circumferenceSection
                    notesSection
                    bmiSourceFooter
                    submitButton
                }
                .padding(Spacing.xl) // design-lint:ignore — micro/hero spacing
                .padding(.bottom, Spacing.xxxl) // design-lint:ignore — micro/hero spacing
            }
            .background(Color.backgroundPage)
            .navigationTitle(Text("body.add.title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(String(localized: "common.cancel")) { dismiss() }
                        .foregroundStyle(Color.textSecondary)
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
            .task { await viewModel.loadUserProfile(apiClient: container.apiClient) }
            .sheet(isPresented: $showSources) { MedicalSourcesView() }
        }
    }

    private var bmiSourceFooter: some View {
        Button {
            showSources = true
        } label: {
            Label(String(localized: "body.add.bmi.sources"), systemImage: "info.circle")
                .font(.caption)
                .foregroundStyle(Color.brandPrimary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, Spacing.xs) // design-lint:ignore — micro/hero spacing
    }

    // MARK: - Date

    private var dateSection: some View {
        FormCard(title: String(localized: "body.add.section.date")) {
            DatePicker(String(localized: "body.add.section.date"), selection: $viewModel.measuredAt, in: ...Date(), displayedComponents: .date)
                .datePickerStyle(.graphical)
                .labelsHidden()
                .tint(Color.brandPrimary)
        }
    }

    // MARK: - Body Composition

    private var bodyCompositionSection: some View {
        FormCard(title: String(localized: "body.add.section.composition")) {
            VStack(spacing: 0) {
                MeasurementField(
                    icon: "scalemass.fill",
                    iconColor: Color(hex: "#2563EB"),
                    label: String(localized: "body.metric.weight"),
                    unit: "kg",
                    text: $viewModel.weightKg
                )
                Divider().padding(.leading, 52) // design-lint:ignore — micro/hero spacing
                MeasurementField(
                    icon: "percent",
                    iconColor: Color(hex: "#7C3AED"),
                    label: String(localized: "body.metric.bodyFatPct"),
                    unit: "%",
                    text: $viewModel.bodyFatPct
                )
                Divider().padding(.leading, 52) // design-lint:ignore — micro/hero spacing
                MeasurementField(
                    icon: "figure.arms.open",
                    iconColor: Color.brandPrimary,
                    label: String(localized: "body.metric.muscleMass"),
                    unit: "kg",
                    text: $viewModel.muscleMassKg
                )
                Divider().padding(.leading, 52) // design-lint:ignore — micro/hero spacing
                if viewModel.isBMIAutoCalculated {
                    AutoCalculatedBMIRow(bmi: viewModel.bmi)
                } else {
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
    }

    // MARK: - Circumference

    private var circumferenceSection: some View {
        FormCard(title: String(localized: "body.add.section.circumference")) {
            VStack(spacing: 0) {
                MeasurementField(
                    icon: "ruler",
                    iconColor: Color(hex: "#0EA5E9"),
                    label: String(localized: "body.circ.chest"),
                    unit: "cm",
                    text: $viewModel.chestCm
                )
                Divider().padding(.leading, 52) // design-lint:ignore — micro/hero spacing
                MeasurementField(
                    icon: "ruler",
                    iconColor: Color(hex: "#DC2626"),
                    label: String(localized: "body.circ.waist"),
                    unit: "cm",
                    text: $viewModel.waistCm
                )
                Divider().padding(.leading, 52) // design-lint:ignore — micro/hero spacing
                MeasurementField(
                    icon: "ruler",
                    iconColor: Color(hex: "#D97706"),
                    label: String(localized: "body.circ.hip"),
                    unit: "cm",
                    text: $viewModel.hipCm
                )
                Divider().padding(.leading, 52) // design-lint:ignore — micro/hero spacing
                MeasurementField(
                    icon: "ruler",
                    iconColor: Color(hex: "#059669"),
                    label: String(localized: "body.circ.thigh"),
                    unit: "cm",
                    text: $viewModel.thighCm
                )
                Divider().padding(.leading, 52) // design-lint:ignore — micro/hero spacing
                MeasurementField(
                    icon: "ruler",
                    iconColor: Color(hex: "#7C3AED"),
                    label: String(localized: "body.circ.arm"),
                    unit: "cm",
                    text: $viewModel.armCm
                )
            }
        }
    }

    // MARK: - Notes

    private var notesSection: some View {
        FormCard(title: String(localized: "body.add.section.notes")) {
            TextField(String(localized: "body.add.notes.placeholder"), text: $viewModel.notes, axis: .vertical)
                .lineLimit(3...5)
                .font(.bodyMedium)
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
                    Text(String(localized: "body.add.save"))
                        .font(.bodyLarge).fontWeight(.semibold)
                        .foregroundStyle(.white)
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 54)
            .background(viewModel.hasAnyValue ? Color.brandPrimary : Color.surfaceSecondary)
            .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
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
                .font(.labelSmall)
                .foregroundStyle(Color.textSecondary)
                .padding(.horizontal, Spacing.xs) // design-lint:ignore — micro/hero spacing
            VStack(alignment: .leading, spacing: 0) {
                content()
            }
            .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
            .background(Color.surfacePrimary)
            .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
            .shadow(color: .black.opacity(0.05), radius: 6, x: 0, y: 2)
        }
    }
}

private struct AutoCalculatedBMIRow: View {
    let bmi: String

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "heart.fill")
                .font(.bodyMedium).fontWeight(.medium)
                .foregroundStyle(Color(hex: "#EA580C"))
                .frame(width: 32, height: 32)
                .background(Color(hex: "#EA580C").opacity(0.1))
                .clipShape(RoundedRectangle(cornerRadius: Radius.sm))

            VStack(alignment: .leading, spacing: 2) {
                Text("BMI")
                    .font(.bodyMedium).fontWeight(.medium)
                    .foregroundStyle(Color.textPrimary)
                Text(String(localized: "body.add.bmi.autoCalc"))
                    .font(.captionXSmall)
                    .foregroundStyle(Color.textSecondary)
            }

            Spacer()

            Text(bmi.isEmpty ? "-" : bmi)
                .font(.headingSmall)
                .foregroundStyle(bmi.isEmpty ? Color.textSecondary : Color.textPrimary)
        }
        .padding(.vertical, Spacing.md) // design-lint:ignore — micro/hero spacing
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
                .font(.bodyMedium).fontWeight(.medium)
                .foregroundStyle(iconColor)
                .frame(width: 32, height: 32)
                .background(iconColor.opacity(0.1))
                .clipShape(RoundedRectangle(cornerRadius: Radius.sm))

            Text(label)
                .font(.bodyMedium).fontWeight(.medium)
                .foregroundStyle(Color.textPrimary)

            Spacer()

            HStack(spacing: 4) {
                TextField("0.0", text: $text)
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.trailing)
                    .font(.headingSmall)
                    .foregroundStyle(Color.textPrimary)
                    .frame(width: 70)

                if !unit.isEmpty {
                    Text(unit)
                        .font(.bodySmall)
                        .foregroundStyle(Color.textSecondary)
                }
            }
        }
        .padding(.vertical, Spacing.md) // design-lint:ignore — micro/hero spacing
    }
}
