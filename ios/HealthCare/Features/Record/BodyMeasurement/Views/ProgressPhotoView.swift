import SwiftUI

struct ProgressPhotoView: View {
    @StateObject private var viewModel = ProgressPhotoViewModel()
    @EnvironmentObject private var container: AppContainer

    @State private var showAddSheet = false
    @State private var selectedPhoto: ProgressPhotoItem?
    @State private var activeErrorAlert: ErrorAlertItem?
    @State private var photoToDelete: ProgressPhotoItem?
    @State private var showCompareSheet = false

    private let columns = [GridItem(.flexible(), spacing: 3), GridItem(.flexible(), spacing: 3)]

    var body: some View {
        VStack(spacing: 0) {
            typeTabBar
            if viewModel.isCompareMode {
                compareBar
            }
            photoGrid
        }
        .background(Color.backgroundPage)
        .navigationTitle(Text("body.photo.title"))
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                if !viewModel.photosForSelectedType.isEmpty {
                    Button {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            viewModel.toggleCompareMode()
                        }
                    } label: {
                        Text(viewModel.isCompareMode ? String(localized: "body.photo.compareToggle.cancel") : String(localized: "body.photo.compareToggle.compare"))
                            .font(.bodyMedium).fontWeight(.medium)
                            .foregroundStyle(viewModel.isCompareMode ? Color.textSecondary : Color.brandPrimary)
                    }
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                if !viewModel.isCompareMode {
                    Button {
                        showAddSheet = true
                    } label: {
                        Image(systemName: "plus")
                            .font(.bodyLarge).fontWeight(.semibold)
                            .foregroundStyle(Color.brandPrimary)
                    }
                }
            }
        }
        .sheet(isPresented: $showAddSheet) {
            AddProgressPhotoView(viewModel: viewModel)
                .environmentObject(container)
        }
        .sheet(item: $selectedPhoto) { photo in
            PhotoDetailView(photo: photo, viewModel: viewModel)
        }
        .sheet(isPresented: $showCompareSheet) {
            if viewModel.compareSelection.count == 2 {
                PhotoCompareView(
                    photoA: viewModel.compareSelection[0],
                    photoB: viewModel.compareSelection[1]
                )
            }
        }
        .alert(Text("body.photo.deleteConfirm.title"), isPresented: Binding(
            get: { photoToDelete != nil },
            set: { if !$0 { photoToDelete = nil } }
        )) {
            Button(String(localized: "common.delete.button"), role: .destructive) {
                if let photo = photoToDelete {
                    Task { await viewModel.deletePhoto(photoId: photo.photoId, apiClient: container.apiClient) }
                }
                photoToDelete = nil
            }
            Button(String(localized: "common.cancel"), role: .cancel) { photoToDelete = nil }
        } message: {
            Text(String(localized: "body.photo.deleteConfirm.message"))
        }
        .overlay {
            if viewModel.isLoading {
                ProgressView()
                    .scaleEffect(1.2)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color.black.opacity(0.08))
            }
        }
        .alert(item: $activeErrorAlert) { item in
            Alert(
                title: Text("오류"),
                message: Text(item.message),
                dismissButton: .cancel(Text("확인")) {
                    activeErrorAlert = nil
                    viewModel.errorMessage = nil
                }
            )
        }
        .onChange(of: viewModel.errorMessage) { newValue in
            guard let newValue else { return }
            activeErrorAlert = ErrorAlertItem(message: newValue)
        }
        .task { await viewModel.loadAll(apiClient: container.apiClient) }
    }

    // MARK: - Type Tab Bar

    private var typeTabBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(PhotoType.allCases) { type in
                    let count = viewModel.photosByType[type]?.count ?? 0
                    Button {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            viewModel.selectedType = type
                            viewModel.compareSelection.removeAll()
                        }
                    } label: {
                        HStack(spacing: 6) {
                            Text(type.label)
                                .font(.labelSmall)
                            if count > 0 {
                                Text("\(count)")
                                    .font(.system(size: 11, weight: .heavy, design: .rounded)) // design-lint:ignore — SF Symbol/hero
                                    .padding(.horizontal, 5) // design-lint:ignore — micro/hero spacing
                                    .padding(.vertical, 1) // design-lint:ignore — micro/hero spacing
                                    .background(
                                        viewModel.selectedType == type
                                            ? Color.white.opacity(0.25)
                                            : Color.surfaceCard
                                    )
                                    .clipShape(Capsule())
                            }
                        }
                        .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
                        .padding(.vertical, Spacing.sm) // design-lint:ignore — micro/hero spacing
                        .background(
                            viewModel.selectedType == type
                                ? Color.brandPrimary
                                : Color.surfaceCard
                        )
                        .foregroundStyle(
                            viewModel.selectedType == type ? Color.white : Color.textSecondary
                        )
                        .clipShape(Capsule())
                        .shadow(
                            color: viewModel.selectedType == type
                                ? Color.brandPrimary.opacity(0.3)
                                : .black.opacity(0.05),
                            radius: 4, x: 0, y: 2
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing
            .padding(.vertical, Spacing.lg) // design-lint:ignore — micro/hero spacing
        }
        .background(Color.surfaceCard)
        .overlay(
            Rectangle().fill(Color.hairline).frame(height: 0.5),
            alignment: .bottom
        )
    }

    // MARK: - Compare Bar

    private var compareBar: some View {
        HStack(spacing: 12) {
            Image(systemName: "square.split.2x1")
                .foregroundStyle(Color.brandPrimary)
                .font(.bodyMedium)
            Text(compareBarLabel)
                .font(.bodyMedium).fontWeight(.medium)
                .foregroundStyle(Color.textPrimary)
            Spacer()
            Button {
                showCompareSheet = true
            } label: {
                Text(String(localized: "body.photo.compare.show"))
                    .font(.bodyMedium).fontWeight(.semibold)
                    .foregroundStyle(.white)
                    .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
                    .padding(.vertical, Spacing.sm) // design-lint:ignore — micro/hero spacing
                    .background(viewModel.compareSelection.count == 2 ? Color.brandPrimary : Color.textTertiary)
                    .clipShape(Capsule())
            }
            .disabled(viewModel.compareSelection.count != 2)
        }
        .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing
        .padding(.vertical, Spacing.md) // design-lint:ignore — micro/hero spacing
        .background(Color.surfaceCard)
        .overlay(Rectangle().fill(Color.hairline).frame(height: 0.5), alignment: .bottom)
    }

    private var compareBarLabel: String {
        switch viewModel.compareSelection.count {
        case 0: return String(localized: "body.photo.compare.hint0")
        case 1: return String(localized: "body.photo.compare.hint1")
        default: return String(localized: "body.photo.compare.hint2")
        }
    }

    // MARK: - Photo Grid

    @ViewBuilder
    private var photoGrid: some View {
        let photos = viewModel.photosForSelectedType

        if photos.isEmpty {
            emptyState
        } else {
            ScrollView(showsIndicators: false) {
                LazyVGrid(columns: columns, spacing: 3) {
                    ForEach(photos) { photo in
                        PhotoGridCell(
                            photo: photo,
                            isCompareMode: viewModel.isCompareMode,
                            isSelected: viewModel.isSelectedForCompare(photo)
                        )
                        .onTapGesture {
                            if viewModel.isCompareMode {
                                withAnimation(.easeInOut(duration: 0.15)) {
                                    viewModel.toggleCompareSelection(photo)
                                }
                            } else {
                                selectedPhoto = photo
                            }
                        }
                        .contextMenu {
                            if !viewModel.isCompareMode {
                                Button(role: .destructive) {
                                    photoToDelete = photo
                                } label: {
                                    Label(String(localized: "common.delete.button"), systemImage: "trash")
                                }
                            }
                        }
                    }
                }
                .padding(.bottom, Spacing.xl) // design-lint:ignore — micro/hero spacing
            }
            .refreshable {
                await viewModel.loadAll(apiClient: container.apiClient)
                viewModel.errorMessage = nil
            }
        }
    }

    private var emptyState: some View {
        EmptyState(
            icon: "camera",
            title: String(localized: "body.photo.empty.title"),
            message: String(format: String(localized: "body.photo.empty.message"), viewModel.selectedType.label),
            action: .init(label: String(localized: "body.photo.empty.action")) {
                showAddSheet = true
            }
        )
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private struct ErrorAlertItem: Identifiable, Equatable {
    let id = UUID()
    let message: String
}

// MARK: - Grid Cell

private struct PhotoGridCell: View {
    let photo: ProgressPhotoItem
    let isCompareMode: Bool
    let isSelected: Bool

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .bottomLeading) {
                AsyncImage(url: photo.thumbnailURL) { phase in
                    switch phase {
                    case .success(let img):
                        img.resizable().scaledToFill()
                    case .failure:
                        Color.surfaceSecondary
                            .overlay(
                                Image(systemName: "photo")
                                    .foregroundStyle(Color.textTertiary)
                            )
                    default:
                        Color.surfaceSecondary
                            .overlay(ProgressView().scaleEffect(0.8))
                    }
                }
                .frame(width: geo.size.width, height: geo.size.width)
                .clipped()

                VStack(alignment: .leading, spacing: 2) {
                    if photo.isBaseline {
                        Text(String(localized: "body.photo.baseline"))
                            .font(.system(size: 9, weight: .heavy)) // design-lint:ignore — SF Symbol/hero
                            .foregroundStyle(Color.textHeadline)
                            .padding(.horizontal, 5) // design-lint:ignore — micro/hero spacing
                            .padding(.vertical, 2) // design-lint:ignore — micro/hero spacing
                            .background(Color.brandAccentGlow)
                            .clipShape(Capsule())
                    }
                    Text(photo.displayDate)
                        .font(.captionXSmall).fontWeight(.semibold)
                        .foregroundStyle(.white)
                        .shadow(color: .black.opacity(0.5), radius: 2, x: 0, y: 1)
                }
                .padding(Spacing.sm) // design-lint:ignore — micro/hero spacing
                .background(
                    LinearGradient(
                        colors: [.clear, .black.opacity(0.45)],
                        startPoint: .top, endPoint: .bottom
                    )
                )

                if isCompareMode {
                    ZStack(alignment: .topTrailing) {
                        Color.black.opacity(isSelected ? 0 : 0.25)
                        Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                            .font(.system(size: 22, weight: .semibold)) // design-lint:ignore — SF Symbol/hero
                            .foregroundStyle(isSelected ? Color.brandPrimary : .white)
                            .shadow(color: .black.opacity(0.3), radius: 2, x: 0, y: 1)
                            .padding(Spacing.sm) // design-lint:ignore — micro/hero spacing
                    }
                    .frame(width: geo.size.width, height: geo.size.width)
                }
            }
        }
        .aspectRatio(1, contentMode: .fit)
        .clipShape(Rectangle())
        .overlay(
            isSelected
                ? RoundedRectangle(cornerRadius: 0) // design-lint:ignore — intentional sharp corner
                    .stroke(Color.brandPrimary, lineWidth: 3)
                : nil
        )
    }
}

// MARK: - Detail View

private struct PhotoDetailView: View {
    let photo: ProgressPhotoItem
    @ObservedObject var viewModel: ProgressPhotoViewModel
    @EnvironmentObject private var container: AppContainer
    @Environment(\.dismiss) private var dismiss

    @State private var showDeleteConfirm = false

    var body: some View {
        NavigationStack {
            ScrollView(showsIndicators: false) {
                VStack(spacing: 0) {
                    AsyncImage(url: photo.originalURL ?? photo.thumbnailURL) { phase in
                        switch phase {
                        case .success(let img):
                            img.resizable().scaledToFit()
                        default:
                            Color.surfaceSecondary
                                .frame(height: 360)
                                .overlay(ProgressView())
                        }
                    }
                    .frame(maxWidth: .infinity)

                    VStack(alignment: .leading, spacing: 20) {
                        infoRow(String(localized: "body.photo.detail.pose"), photo.photoType.label)
                        infoRow(String(localized: "body.photo.detail.takenOn"), photo.displayDate)
                        if let w = photo.bodyWeightKg {
                            infoRow(String(localized: "body.photo.detail.weight"), String(format: "%.1f kg", w))
                        }
                        if let wc = photo.waistCm {
                            infoRow("허리", String(format: "%.1f cm", wc))
                        }
                        if let n = photo.notes, !n.isEmpty {
                            infoRow("메모", n)
                        }
                        if photo.isBaseline {
                            HStack(spacing: 8) {
                                Image(systemName: "star.fill")
                                    .foregroundStyle(Color.brandSunrise)
                                Text("기준 사진으로 설정됨")
                                    .font(.bodyMedium)
                                    .foregroundStyle(Color.textSecondary)
                            }
                        }
                    }
                    .padding(Spacing.xxl) // design-lint:ignore — micro/hero spacing

                    Button(role: .destructive) {
                        showDeleteConfirm = true
                    } label: {
                        Label("사진 삭제", systemImage: "trash")
                            .font(.bodyMedium).fontWeight(.medium)
                            .foregroundStyle(Color.red)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, Spacing.lg) // design-lint:ignore — micro/hero spacing
                            .background(Color.red.opacity(0.08))
                            .clipShape(RoundedRectangle(cornerRadius: Radius.md))
                    }
                    .padding(.horizontal, Spacing.xxl) // design-lint:ignore — micro/hero spacing
                    .padding(.bottom, Spacing.xxl) // design-lint:ignore — micro/hero spacing
                }
            }
            .background(Color.backgroundPage)
            .navigationTitle(photo.photoType.label)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("닫기") { dismiss() }
                        .foregroundStyle(Color.textSecondary)
                }
            }
            .alert("사진 삭제", isPresented: $showDeleteConfirm) {
                Button(String(localized: "common.delete.button"), role: .destructive) {
                    Task {
                        await viewModel.deletePhoto(photoId: photo.photoId, apiClient: container.apiClient)
                        dismiss()
                    }
                }
                Button("취소", role: .cancel) {}
            } message: {
                Text(String(localized: "body.photo.deleteConfirm.message"))
            }
        }
    }

    private func infoRow(_ label: String, _ value: String) -> some View {
        HStack(alignment: .top) {
            Text(label)
                .font(.bodySmall)
                .foregroundStyle(Color.textTertiary)
                .frame(width: 52, alignment: .leading)
            Text(value)
                .font(.bodyMedium)
                .foregroundStyle(Color.textPrimary)
            Spacer()
        }
    }
}

// MARK: - Compare View

struct PhotoCompareView: View {
    let photoA: ProgressPhotoItem
    let photoB: ProgressPhotoItem
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            GeometryReader { geo in
                let panelWidth = max(0, geo.size.width / 2 - 1)
                HStack(spacing: 2) {
                    comparePanel(photo: photoA, width: panelWidth)
                    comparePanel(photo: photoB, width: panelWidth)
                }
            }
            .background(Color.black)
            .navigationTitle("비교")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("닫기") { dismiss() }
                        .foregroundStyle(.white)
                }
            }
            .toolbarBackground(.black, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
    }

    private func comparePanel(photo: ProgressPhotoItem, width: CGFloat) -> some View {
        VStack(spacing: 0) {
            AsyncImage(url: photo.originalURL ?? photo.thumbnailURL) { phase in
                switch phase {
                case .success(let img):
                    img.resizable().scaledToFill()
                default:
                    Color.gray.opacity(0.3)
                        .overlay(ProgressView().tint(.white))
                }
            }
            .frame(width: width)
            .clipped()
            .frame(maxHeight: .infinity)

            VStack(spacing: 4) {
                Text(photo.displayDate)
                    .font(.labelSmall)
                    .foregroundStyle(.white)
                if let w = photo.bodyWeightKg {
                    Text(String(format: "%.1f kg", w))
                        .font(.caption)
                        .foregroundStyle(Color.white.opacity(0.7))
                }
            }
            .padding(.vertical, Spacing.md) // design-lint:ignore — micro/hero spacing
            .frame(maxWidth: .infinity)
            .background(Color.black.opacity(0.85))
        }
    }
}
