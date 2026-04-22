import SwiftUI

struct ProgressPhotoView: View {
    @StateObject private var viewModel = ProgressPhotoViewModel()
    @EnvironmentObject private var container: AppContainer

    @State private var showAddSheet = false
    @State private var selectedPhoto: ProgressPhotoItem?

    private let columns = [GridItem(.flexible(), spacing: 3), GridItem(.flexible(), spacing: 3)]

    var body: some View {
        VStack(spacing: 0) {
            typeTabBar
            photoGrid
        }
        .background(Color.surfaceGrouped)
        .navigationTitle("진행 사진")
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    showAddSheet = true
                } label: {
                    Image(systemName: "plus")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Color.brandPrimary)
                }
            }
        }
        .sheet(isPresented: $showAddSheet) {
            AddProgressPhotoView(viewModel: viewModel)
                .environmentObject(container)
        }
        .sheet(item: $selectedPhoto) { photo in
            PhotoDetailView(photo: photo)
        }
        .overlay {
            if viewModel.isLoading {
                ProgressView()
                    .scaleEffect(1.2)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color.black.opacity(0.08))
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
                        }
                    } label: {
                        HStack(spacing: 6) {
                            Text(type.label)
                                .font(.system(size: 13, weight: .semibold))
                            if count > 0 {
                                Text("\(count)")
                                    .font(.system(size: 11, weight: .heavy, design: .rounded))
                                    .padding(.horizontal, 5)
                                    .padding(.vertical, 1)
                                    .background(
                                        viewModel.selectedType == type
                                            ? Color.white.opacity(0.25)
                                            : Color.brandSurface
                                    )
                                    .clipShape(Capsule())
                            }
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(
                            viewModel.selectedType == type
                                ? Color.brandPrimary
                                : Color.surfacePrimary
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
            .padding(.horizontal, 20)
            .padding(.vertical, 14)
        }
        .background(Color.surfacePrimary)
        .overlay(
            Rectangle().fill(Color.hairline).frame(height: 0.5),
            alignment: .bottom
        )
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
                        PhotoGridCell(photo: photo)
                            .onTapGesture { selectedPhoto = photo }
                    }
                }
                .padding(.bottom, 20)
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 20) {
            Spacer()
            ZStack {
                Circle()
                    .fill(Color.brandSurface)
                    .frame(width: 96, height: 96)
                Image(systemName: "camera")
                    .font(.system(size: 36, weight: .light))
                    .foregroundStyle(Color.brandSecondary)
            }
            VStack(spacing: 8) {
                Text("아직 사진이 없어요")
                    .font(.headingMedium)
                    .foregroundStyle(Color.textPrimary)
                Text("+ 버튼으로 \(viewModel.selectedType.label) 사진을 추가하세요")
                    .font(.bodyMedium)
                    .foregroundStyle(Color.textSecondary)
                    .multilineTextAlignment(.center)
            }
            Button {
                showAddSheet = true
            } label: {
                Text("사진 추가")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 28)
                    .padding(.vertical, 13)
                    .background(Color.brandPrimary)
                    .clipShape(Capsule())
            }
            Spacer()
        }
        .padding(.horizontal, 40)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Grid Cell

private struct PhotoGridCell: View {
    let photo: ProgressPhotoItem

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

                // Date overlay
                VStack(alignment: .leading, spacing: 2) {
                    if photo.isBaseline {
                        Text("기준")
                            .font(.system(size: 9, weight: .heavy))
                            .foregroundStyle(Color.brandDusk)
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(Color.brandAccentGlow)
                            .clipShape(Capsule())
                    }
                    Text(photo.displayDate)
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(.white)
                        .shadow(color: .black.opacity(0.5), radius: 2, x: 0, y: 1)
                }
                .padding(8)
                .background(
                    LinearGradient(
                        colors: [.clear, .black.opacity(0.45)],
                        startPoint: .top, endPoint: .bottom
                    )
                )
            }
        }
        .aspectRatio(1, contentMode: .fit)
    }
}

// MARK: - Detail View

private struct PhotoDetailView: View {
    let photo: ProgressPhotoItem
    @Environment(\.dismiss) private var dismiss

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
                        infoRow("포즈", photo.photoType.label)
                        infoRow("촬영일", photo.displayDate)
                        if let w = photo.bodyWeightKg {
                            infoRow("체중", String(format: "%.1f kg", w))
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
                    .padding(24)
                }
            }
            .background(Color.surfaceGrouped)
            .navigationTitle(photo.photoType.label)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("닫기") { dismiss() }
                        .foregroundStyle(Color.textSecondary)
                }
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
