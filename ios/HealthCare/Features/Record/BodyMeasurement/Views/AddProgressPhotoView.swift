import SwiftUI
import PhotosUI

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
                .padding(20)
                .padding(.bottom, 40)
            }
            .background(Color.surfaceGrouped)
            .navigationTitle("진행 사진 추가")
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
        .onChange(of: selectedItem) { item in
            Task {
                if let data = try? await item?.loadTransferable(type: Data.self),
                   let img = UIImage(data: data) {
                    selectedImage = img
                }
            }
        }
    }

    // MARK: - Photo Picker

    private var photoPickerSection: some View {
        PhotosPicker(selection: $selectedItem, matching: .images) {
            ZStack {
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .fill(Color.surfacePrimary)
                    .shadow(color: .black.opacity(0.05), radius: 8, x: 0, y: 2)
                    .frame(height: 240)

                if let img = selectedImage {
                    Image(uiImage: img)
                        .resizable()
                        .scaledToFill()
                        .frame(maxWidth: .infinity)
                        .frame(height: 240)
                        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                } else {
                    VStack(spacing: 14) {
                        ZStack {
                            Circle()
                                .fill(Color.brandSurface)
                                .frame(width: 64, height: 64)
                            Image(systemName: "camera.fill")
                                .font(.system(size: 24, weight: .semibold))
                                .foregroundStyle(Color.brandSecondary)
                        }
                        VStack(spacing: 4) {
                            Text("사진 선택")
                                .font(.headingSmall)
                                .foregroundStyle(Color.textPrimary)
                            Text("갤러리에서 가져오기")
                                .font(.bodySmall)
                                .foregroundStyle(Color.textTertiary)
                        }
                    }
                }
            }
        }
    }

    // MARK: - Type Selector

    private var typeSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("포즈")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Color.textSecondary)
                .padding(.horizontal, 4)

            HStack(spacing: 8) {
                ForEach(PhotoType.allCases) { type in
                    Button {
                        selectedType = type
                    } label: {
                        VStack(spacing: 4) {
                            Image(systemName: type.icon)
                                .font(.system(size: 16, weight: .medium))
                            Text(type.label)
                                .font(.system(size: 11, weight: .semibold))
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(
                            selectedType == type
                                ? Color.brandPrimary
                                : Color.surfacePrimary
                        )
                        .foregroundStyle(
                            selectedType == type ? Color.white : Color.textSecondary
                        )
                        .clipShape(RoundedRectangle(cornerRadius: 12))
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
            Text("신체 정보 (선택)")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Color.textSecondary)
                .padding(.horizontal, 4)

            VStack(spacing: 0) {
                metaRow(label: "체중", unit: "kg", text: $weightText)
                Divider().padding(.leading, 16)
                metaRow(label: "허리", unit: "cm", text: $waistText)
                Divider().padding(.leading, 16)
                HStack {
                    Text("메모")
                        .font(.bodyMedium)
                        .foregroundStyle(Color.textSecondary)
                        .frame(width: 56, alignment: .leading)
                    TextField("특이사항 (선택)", text: $notes, axis: .vertical)
                        .lineLimit(1...3)
                        .font(.system(size: 14))
                        .foregroundStyle(Color.textPrimary)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            .background(Color.surfacePrimary)
            .clipShape(RoundedRectangle(cornerRadius: 16))
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
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color.textPrimary)
                    .frame(width: 70)
                Text(unit)
                    .font(.bodySmall)
                    .foregroundStyle(Color.textTertiary)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 13)
    }

    // MARK: - Baseline Toggle

    private var baselineToggle: some View {
        Toggle(isOn: $isBaseline) {
            VStack(alignment: .leading, spacing: 2) {
                Text("기준 사진으로 설정")
                    .font(.bodyMedium)
                    .foregroundStyle(Color.textPrimary)
                Text("변화 비교의 시작점이 됩니다")
                    .font(.bodySmall)
                    .foregroundStyle(Color.textTertiary)
            }
        }
        .tint(Color.brandAccent)
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(Color.surfacePrimary)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .shadow(color: .black.opacity(0.04), radius: 6, x: 0, y: 2)
    }

    // MARK: - Upload Button

    private var uploadButton: some View {
        Button {
            guard let img = selectedImage else { return }
            Task {
                await viewModel.upload(
                    image: img,
                    photoType: selectedType,
                    bodyWeightKg: Double(weightText),
                    waistCm: Double(waistText),
                    notes: notes,
                    isBaseline: isBaseline,
                    apiClient: container.apiClient
                )
                if viewModel.errorMessage == nil {
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
                        Text("업로드 중 \(Int(viewModel.uploadProgress * 100))%")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundStyle(.white.opacity(0.85))
                    }
                } else {
                    Text("저장하기")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(.white)
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 54)
            .background(canUpload ? Color.brandPrimary : Color.surfaceSecondary)
            .clipShape(RoundedRectangle(cornerRadius: 14))
        }
        .disabled(!canUpload)
        .animation(.easeInOut(duration: 0.2), value: canUpload)
    }
}
