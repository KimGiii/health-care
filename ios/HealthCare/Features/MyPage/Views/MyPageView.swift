import SwiftUI

struct MyPageView: View {
    @StateObject private var viewModel = MyPageViewModel()
    @EnvironmentObject private var authState: AuthState
    @EnvironmentObject private var container: AppContainer

    @State private var showEditSheet = false
    @State private var showDeleteConfirm = false

    var body: some View {
        NavigationStack {
            ScrollView(showsIndicators: false) {
                VStack(spacing: 0) {
                    profileHeader
                    statsRow
                    menuSections
                }
                .padding(.bottom, 40)
            }
            .background(Color.surfaceGrouped)
            .navigationTitle("마이")
            .navigationBarTitleDisplayMode(.large)
            .alert("오류", isPresented: Binding(
                get: { viewModel.errorMessage != nil },
                set: { if !$0 { viewModel.errorMessage = nil } }
            )) {
                Button("확인", role: .cancel) {}
            } message: {
                Text(viewModel.errorMessage ?? "")
            }
            .confirmationDialog("계정을 삭제하면 모든 데이터가 영구 삭제됩니다.", isPresented: $showDeleteConfirm, titleVisibility: .visible) {
                Button("계정 삭제", role: .destructive) {
                    Task { await viewModel.deleteAccount(apiClient: container.apiClient, authState: authState) }
                }
                Button("취소", role: .cancel) {}
            }
            .sheet(isPresented: $showEditSheet) {
                EditProfileSheet(viewModel: viewModel, isPresented: $showEditSheet)
                    .environmentObject(container)
            }
        }
        .task { await viewModel.load(apiClient: container.apiClient) }
    }

    // MARK: - Profile Header

    private var profileHeader: some View {
        VStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(LinearGradient.forestHero)
                    .frame(width: 80, height: 80)
                Text(viewModel.profile?.displayName.prefix(1).uppercased() ?? "?")
                    .font(.displayMedium)
                    .foregroundStyle(.white)
            }

            VStack(spacing: 4) {
                Text(viewModel.profile?.displayName ?? "불러오는 중...")
                    .font(.headingLarge)
                    .foregroundStyle(Color.textPrimary)
                Text(viewModel.profile?.email ?? "")
                    .font(.bodyMedium)
                    .foregroundStyle(Color.textSecondary)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 32)
        .padding(.horizontal, 20)
        .background(Color.surfacePrimary)
    }

    // MARK: - Stats Row

    private var statsRow: some View {
        HStack(spacing: 0) {
            statCell(
                label: "키",
                value: viewModel.profile?.heightCm.map { "\(Int($0))cm" } ?? "-"
            )
            Divider().frame(height: 40)
            statCell(
                label: "체중",
                value: viewModel.profile?.weightKg.map { String(format: "%.1fkg", $0) } ?? "-"
            )
            Divider().frame(height: 40)
            statCell(
                label: "활동량",
                value: viewModel.activityLevelLabel
            )
            Divider().frame(height: 40)
            statCell(
                label: "성별",
                value: viewModel.sexLabel
            )
        }
        .padding(.vertical, 16)
        .background(Color.surfacePrimary)
        .overlay(
            Rectangle()
                .fill(Color.hairline)
                .frame(height: 0.5),
            alignment: .top
        )
        .overlay(
            Rectangle()
                .fill(Color.hairline)
                .frame(height: 0.5),
            alignment: .bottom
        )
    }

    private func statCell(label: String, value: String) -> some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.headingSmall)
                .foregroundStyle(Color.textPrimary)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Text(label)
                .font(.caption)
                .foregroundStyle(Color.textTertiary)
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: - Menu Sections

    private var menuSections: some View {
        VStack(spacing: 20) {
            MenuSection(title: "계정 관리") {
                MenuRow(icon: "person.crop.circle", iconColor: Color.brandSecondary, label: "프로필 수정") {
                    viewModel.populateEditFields()
                    showEditSheet = true
                }
            }

            MenuSection(title: "앱 정보") {
                MenuRow(
                    icon: "info.circle",
                    iconColor: Color.brandMoss,
                    label: "버전",
                    trailingText: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0",
                    action: {}
                )
            }

            MenuSection(title: "") {
                MenuRow(icon: "rectangle.portrait.and.arrow.right", iconColor: Color.brandWarning, label: "로그아웃") {
                    viewModel.logout(authState: authState)
                }
                MenuRow(icon: "trash", iconColor: Color.brandDanger, label: "계정 삭제") {
                    showDeleteConfirm = true
                }
            }
        }
        .padding(.top, 24)
        .padding(.horizontal, 20)
    }
}

// MARK: - Edit Profile Sheet

private struct EditProfileSheet: View {
    @ObservedObject var viewModel: MyPageViewModel
    @Binding var isPresented: Bool
    @EnvironmentObject private var container: AppContainer
    @Environment(\.dismiss) private var dismiss

    let sexOptions = [("남성", "MALE"), ("여성", "FEMALE")]
    let activityOptions: [(String, String)] = [
        ("거의 안 움직임", "SEDENTARY"),
        ("가벼운 활동", "LIGHT"),
        ("보통 활동", "MODERATE"),
        ("활발한 활동", "ACTIVE"),
        ("매우 활발", "VERY_ACTIVE"),
    ]

    var body: some View {
        NavigationStack {
            ScrollView(showsIndicators: false) {
                VStack(spacing: 16) {
                    EditCard(title: "기본 정보") {
                        EditField(label: "닉네임", placeholder: "닉네임을 입력하세요", text: $viewModel.editDisplayName)
                        Divider().padding(.leading, 16)
                        EditPickerField(label: "성별", value: viewModel.editSex, options: sexOptions) { v in
                            viewModel.editSex = v
                        }
                    }

                    EditCard(title: "신체 정보") {
                        EditNumericField(label: "키", unit: "cm", text: $viewModel.editHeightCm)
                        Divider().padding(.leading, 16)
                        EditNumericField(label: "체중", unit: "kg", text: $viewModel.editWeightKg)
                    }

                    EditCard(title: "활동량") {
                        VStack(spacing: 0) {
                            ForEach(activityOptions, id: \.1) { label, value in
                                Button {
                                    viewModel.editActivityLevel = value
                                } label: {
                                    HStack {
                                        Text(label)
                                            .font(.bodyMedium)
                                            .foregroundStyle(Color.textPrimary)
                                        Spacer()
                                        if viewModel.editActivityLevel == value {
                                            Image(systemName: "checkmark")
                                                .foregroundStyle(Color.brandAccent)
                                                .font(.system(size: 14, weight: .semibold))
                                        }
                                    }
                                    .padding(.vertical, 12)
                                    .padding(.horizontal, 16)
                                }
                                if value != activityOptions.last?.1 {
                                    Divider().padding(.leading, 16)
                                }
                            }
                        }
                    }

                    Button {
                        Task {
                            await viewModel.saveProfile(apiClient: container.apiClient)
                            if viewModel.errorMessage == nil {
                                dismiss()
                            }
                        }
                    } label: {
                        Group {
                            if viewModel.isLoading {
                                ProgressView().tint(.white)
                            } else {
                                Text("저장하기")
                                    .font(.system(size: 16, weight: .semibold))
                                    .foregroundStyle(.white)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 54)
                        .background(Color.brandPrimary)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                    }
                    .disabled(viewModel.isLoading)
                }
                .padding(20)
                .padding(.bottom, 20)
            }
            .background(Color.surfaceGrouped)
            .navigationTitle("프로필 수정")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("취소") { dismiss() }
                        .foregroundStyle(Color.textSecondary)
                }
            }
        }
    }
}

// MARK: - Reusable Components

private struct MenuSection<Content: View>: View {
    let title: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if !title.isEmpty {
                Text(title)
                    .font(.eyebrow)
                    .tracking(1.5)
                    .foregroundStyle(Color.textTertiary)
                    .padding(.horizontal, 4)
            }
            VStack(spacing: 0) {
                content()
            }
            .background(Color.surfacePrimary)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .shadow(color: .black.opacity(0.04), radius: 8, x: 0, y: 2)
        }
    }
}

private struct MenuRow: View {
    let icon: String
    let iconColor: Color
    let label: String
    let trailingText: String?
    let action: () -> Void

    init(icon: String, iconColor: Color, label: String, trailingText: String? = nil, action: @escaping () -> Void) {
        self.icon = icon
        self.iconColor = iconColor
        self.label = label
        self.trailingText = trailingText
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: icon)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(iconColor)
                    .frame(width: 30, height: 30)
                    .background(iconColor.opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: 8))

                Text(label)
                    .font(.bodyMedium)
                    .foregroundStyle(
                        label == "계정 삭제" ? Color.brandDanger : Color.textPrimary
                    )

                Spacer()

                if let t = trailingText {
                    Text(t)
                        .font(.bodySmall)
                        .foregroundStyle(Color.textTertiary)
                } else {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Color.textTertiary)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
        }
    }
}

private struct EditCard<Content: View>: View {
    let title: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.eyebrow)
                .tracking(1.5)
                .foregroundStyle(Color.textTertiary)
                .padding(.horizontal, 4)
            VStack(spacing: 0) {
                content()
            }
            .background(Color.surfacePrimary)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .shadow(color: .black.opacity(0.04), radius: 8, x: 0, y: 2)
        }
    }
}

private struct EditField: View {
    let label: String
    let placeholder: String
    @Binding var text: String

    var body: some View {
        HStack {
            Text(label)
                .font(.bodyMedium)
                .foregroundStyle(Color.textSecondary)
                .frame(width: 64, alignment: .leading)
            TextField(placeholder, text: $text)
                .font(.bodyMedium)
                .foregroundStyle(Color.textPrimary)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 13)
    }
}

private struct EditNumericField: View {
    let label: String
    let unit: String
    @Binding var text: String

    var body: some View {
        HStack {
            Text(label)
                .font(.bodyMedium)
                .foregroundStyle(Color.textSecondary)
                .frame(width: 64, alignment: .leading)
            Spacer()
            HStack(spacing: 4) {
                TextField("0", text: $text)
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.trailing)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color.textPrimary)
                    .frame(width: 72)
                Text(unit)
                    .font(.bodySmall)
                    .foregroundStyle(Color.textTertiary)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 13)
    }
}

private struct EditPickerField: View {
    let label: String
    let value: String
    let options: [(String, String)]
    let onSelect: (String) -> Void

    private var displayLabel: String {
        options.first(where: { $0.1 == value })?.0 ?? "선택"
    }

    var body: some View {
        Menu {
            ForEach(options, id: \.1) { name, key in
                Button(name) { onSelect(key) }
            }
        } label: {
            HStack {
                Text(label)
                    .font(.bodyMedium)
                    .foregroundStyle(Color.textSecondary)
                    .frame(width: 64, alignment: .leading)
                Spacer()
                Text(displayLabel)
                    .font(.bodyMedium)
                    .foregroundStyle(Color.textPrimary)
                Image(systemName: "chevron.up.chevron.down")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Color.textTertiary)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 13)
        }
    }
}
