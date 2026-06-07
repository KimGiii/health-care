import SwiftUI
@preconcurrency import PhotosUI

@MainActor
struct AddProgressPhotoView: View {
    @ObservedObject var viewModel: ProgressPhotoViewModel
    @EnvironmentObject private var container: AppContainer
    @Environment(\.dismiss) private var dismiss

    @State private var selectedItem: PhotosPickerItem?
    @State private var selectedImage: UIImage?
    @State private var selectedType: PhotoType = .FRONT
    @State private var weightText = ""
    @State private var waistText = ""
    @State private var notes = ""
    @State private var isBaseline = false

    var canUpload: Bool { selectedImage != nil && !viewModel.isUploading }
    var isRetryState: Bool { viewModel.uploadFailed && !viewModel.isUploading }

    var body: some View {
        NavigationStack {
            ScrollView(showsIndicators: false) {
                VStack(spacing: 20) {
                    photoPickerSection
                    typeSection
                    metaSection
                    baselineToggle
                    uploadButton
                }
                .padding(Spacing.xl) // design-lint:ignore — micro/hero spacing
                .padding(.bottom, Spacing.xxxl) // design-lint:ignore — micro/hero spacing
            }
            .background(Color.backgroundPage)
            .navigationTitle(Text("body.photo.add.title"))
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
        }
        .onChange(of: selectedItem) { item in
            Task { @MainActor in
                if let data = try? await item?.loadTransferable(type: Data.self),
                   let img = UIImage(data: data) {
                    selectedImage = img
                    viewModel.uploadFailed = false
                }
            }
        }
    }

    // MARK: - Photo Picker

    private var photoPickerSection: some View {
        PhotoPickerSection(selectedItem: $selectedItem, selectedImage: selectedImage)
    }

    // MARK: - Type Selector

    private var typeSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(String(localized: "body.photo.add.poseTitle"))
                .font(.labelSmall)
                .foregroundStyle(Color.textSecondary)
                .padding(.horizontal, Spacing.xs) // design-lint:ignore — micro/hero spacing

            HStack(spacing: 8) {
                ForEach(PhotoType.allCases) { type in
                    Button {
                        selectedType = type
                    } label: {
                        VStack(spacing: 4) {
                            Image(systemName: type.icon)
                                .font(.bodyLarge).fontWeight(.medium)
                            Text(type.label)
                                .font(.captionXSmall).fontWeight(.semibold)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, Spacing.md) // design-lint:ignore — micro/hero spacing
                        .background(
                            selectedType == type
                                ? Color.brandPrimary
                                : Color.surfaceCard
                        )
                        .foregroundStyle(
                            selectedType == type ? Color.white : Color.textSecondary
                        )
                        .clipShape(RoundedRectangle(cornerRadius: Radius.md))
                        .shadow(color: .black.opacity(0.04), radius: 4, x: 0, y: 2)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    // MARK: - Meta

    private var metaSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(String(localized: "body.photo.add.bodyInfoTitle"))
                .font(.labelSmall)
                .foregroundStyle(Color.textSecondary)
                .padding(.horizontal, Spacing.xs) // design-lint:ignore — micro/hero spacing

            VStack(spacing: 0) {
                metaRow(label: String(localized: "body.metric.weight"), unit: "kg", text: $weightText)
                Divider().padding(.leading, Spacing.lg)
                metaRow(label: String(localized: "body.metric.waist"), unit: "cm", text: $waistText)
                Divider().padding(.leading, Spacing.lg)
                HStack {
                    Text(String(localized: "body.photo.add.notesTitle"))
                        .font(.bodyMedium)
                        .foregroundStyle(Color.textSecondary)
                        .frame(width: 56, alignment: .leading)
                    TextField(String(localized: "body.photo.add.notesPlaceholder"), text: $notes, axis: .vertical)
                        .lineLimit(1...3)
                        .font(.bodyMedium)
                        .foregroundStyle(Color.textPrimary)
                }
                .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
                .padding(.vertical, Spacing.md) // design-lint:ignore — micro/hero spacing
            }
            .background(Color.surfaceCard)
            .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
            .shadow(color: .black.opacity(0.04), radius: 6, x: 0, y: 2)
        }
    }

    private func metaRow(label: String, unit: String, text: Binding<String>) -> some View {
        HStack {
            Text(label)
                .font(.bodyMedium)
                .foregroundStyle(Color.textSecondary)
                .frame(width: 56, alignment: .leading)
            Spacer()
            HStack(spacing: 4) {
                TextField("0.0", text: text)
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.trailing)
                    .font(.headingSmall)
                    .foregroundStyle(Color.textPrimary)
                    .frame(width: 70)
                Text(unit)
                    .font(.bodySmall)
                    .foregroundStyle(Color.textTertiary)
            }
        }
        .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
        .padding(.vertical, 13) // design-lint:ignore — micro/hero spacing
    }

    // MARK: - Baseline Toggle

    private var baselineToggle: some View {
        Toggle(isOn: $isBaseline) {
            VStack(alignment: .leading, spacing: 2) {
                Text(String(localized: "body.photo.add.setBaseline"))
                    .font(.bodyMedium)
                    .foregroundStyle(Color.textPrimary)
                Text(String(localized: "body.photo.add.setBaselineHint"))
                    .font(.bodySmall)
                    .foregroundStyle(Color.textTertiary)
            }
        }
        .tint(Color.brandAccent)
        .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
        .padding(.vertical, Spacing.lg) // design-lint:ignore — micro/hero spacing
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .shadow(color: .black.opacity(0.04), radius: 6, x: 0, y: 2)
    }

    // MARK: - Upload Button

    private var uploadButton: some View {
        VStack(spacing: 10) {
            if isRetryState {
                uploadFailureBanner
            }

            Button {
                guard let img = selectedImage else { return }
                Task {
                    if isRetryState {
                        await viewModel.retryUpload(apiClient: container.apiClient)
                    } else {
                        await viewModel.upload(
                            image: img,
                            photoType: selectedType,
                            bodyWeightKg: Double(weightText),
                            waistCm: Double(waistText),
                            notes: notes,
                            isBaseline: isBaseline,
                            apiClient: container.apiClient
                        )
                    }
                    if !viewModel.uploadFailed {
                        dismiss()
                    }
                }
            } label: {
                ZStack {
                    if viewModel.isUploading {
                        VStack(spacing: 8) {
                            ProgressView(value: viewModel.uploadProgress)
                                .tint(.white)
                                .frame(width: 160)
                            Text(String(format: String(localized: "body.photo.add.uploadProgress"), Int(viewModel.uploadProgress * 100)))
                                .font(.bodySmall).fontWeight(.medium)
                                .foregroundStyle(.white.opacity(0.85))
                        }
                    } else if isRetryState {
                        HStack(spacing: 8) {
                            Image(systemName: "arrow.clockwise")
                                .font(.headingSmall)
                            Text(String(localized: "body.photo.add.retry"))
                                .font(.bodyLarge).fontWeight(.semibold)
                        }
                        .foregroundStyle(.white)
                    } else {
                        Text(String(localized: "body.photo.add.save"))
                            .font(.bodyLarge).fontWeight(.semibold)
                            .foregroundStyle(.white)
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(height: 54)
                .background(uploadButtonBackground)
                .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
            }
            .disabled(!canUpload)
            .animation(.easeInOut(duration: 0.2), value: canUpload)
            .animation(.easeInOut(duration: 0.2), value: isRetryState)
        }
    }

    private var uploadButtonBackground: Color {
        guard canUpload else { return Color.surfaceSecondary }
        return isRetryState ? Color.brandPrimary : Color.brandPrimary
    }

    private var uploadFailureBanner: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.bodyMedium).fontWeight(.semibold)
                .foregroundStyle(Color.brandDanger)
                .padding(.top, 1) // design-lint:ignore — micro/hero spacing
            Text(viewModel.uploadFailureMessage)
                .font(.bodySmall)
                .foregroundStyle(Color.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
        .padding(.vertical, Spacing.md) // design-lint:ignore — micro/hero spacing
        .background(Color.brandDanger.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: Radius.md))
        .transition(.opacity.combined(with: .move(edge: .bottom)))
    }
}

// MARK: - Photo Picker Section
//
// PhotosPicker의 label 클로저가 Swift 6 strict concurrency에서 nonisolated로
// 추론되는 문제를 피하기 위해 별도 @MainActor View로 분리.

@MainActor
private struct PhotoPickerSection: View {
    @Binding var selectedItem: PhotosPickerItem?
    let selectedImage: UIImage?

    var body: some View {
        PhotosPicker(selection: $selectedItem, matching: .images) {
            ZStack {
                RoundedRectangle(cornerRadius: Radius.xl, style: .continuous)
                    .fill(Color.surfaceCard)
                    .shadow(color: .black.opacity(0.05), radius: 8, x: 0, y: 2)
                    .frame(height: 240)

                if let img = selectedImage {
                    Image(uiImage: img)
                        .resizable()
                        .scaledToFill()
                        .frame(maxWidth: .infinity)
                        .frame(height: 240)
                        .clipShape(RoundedRectangle(cornerRadius: Radius.xl, style: .continuous))
                } else {
                    VStack(spacing: 14) {
                        ZStack {
                            Circle()
                                .fill(Color.surfaceCard)
                                .frame(width: 64, height: 64)
                            Image(systemName: "camera.fill")
                                .font(.system(size: 24, weight: .semibold)) // design-lint:ignore — SF Symbol/hero
                                .foregroundStyle(Color.brandSecondary)
                        }
                        VStack(spacing: 4) {
                            Text(String(localized: "body.photo.add.selectPhoto"))
                                .font(.headingSmall)
                                .foregroundStyle(Color.textPrimary)
                            Text(String(localized: "body.photo.add.fromGallery"))
                                .font(.bodySmall)
                                .foregroundStyle(Color.textTertiary)
                        }
                    }
                }
            }
        }
    }
}
