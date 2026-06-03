import SwiftUI
import UserNotifications

struct MyPageView: View {
    @StateObject private var viewModel = MyPageViewModel()
    @EnvironmentObject private var authState: AuthState
    @EnvironmentObject private var container: AppContainer

    @State private var showEditSheet = false
    @State private var showDeleteConfirm = false
    @State private var showMedicalSources = false
    @AppStorage("appTheme") private var appThemeRawValue = AppTheme.system.rawValue

    private var selectedTheme: AppTheme {
        AppTheme(rawValue: appThemeRawValue) ?? .system
    }

    var body: some View {
        NavigationStack {
            ScrollView(showsIndicators: false) {
                VStack(spacing: 20) {
                    profileCard
                    menuSections
                }
                .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing
                .padding(.top, Spacing.sm) // design-lint:ignore — micro/hero spacing
                .padding(.bottom, Spacing.xxxl) // design-lint:ignore — micro/hero spacing
            }
            .background(Color.backgroundPage)
            .navigationTitle(Text("mypage.title"))
            .navigationBarTitleDisplayMode(.large)
            .alert(Text("오류"), isPresented: Binding(
                get: { viewModel.errorMessage != nil },
                set: { if !$0 { viewModel.errorMessage = nil } }
            )) {
                Button("확인", role: .cancel) {}
            } message: {
                Text(viewModel.errorMessage ?? "")
            }
            .confirmationDialog(
                Text("mypage.deleteConfirm.title"),
                isPresented: $showDeleteConfirm,
                titleVisibility: .visible
            ) {
                Button(String(localized: "mypage.menu.deleteAccount"), role: .destructive) {
                    Task { await viewModel.deleteAccount(apiClient: container.apiClient, authState: authState) }
                }
                Button(String(localized: "common.cancel.button"), role: .cancel) {}
            }
            .sheet(isPresented: $showEditSheet) {
                EditProfileSheet(viewModel: viewModel, isPresented: $showEditSheet)
                    .environmentObject(container)
            }
            .sheet(isPresented: $showMedicalSources) {
                MedicalSourcesView()
            }
        }
        .refreshable {
            await viewModel.load(apiClient: container.apiClient, authState: authState)
            // pull-to-refresh로 인한 실패는 alert으로 알리지 않음(기존 화면 데이터 유지).
            viewModel.errorMessage = nil
        }
        .task { await viewModel.load(apiClient: container.apiClient, authState: authState) }
        // 신체 측정 기록이 추가/수정/삭제되면 백엔드가 User.weightKg를 동기화하므로
        // 마이페이지도 즉시 다시 가져와 최신 체중을 표시.
        .onReceive(NotificationCenter.default.publisher(for: .bodyMeasurementDidChange)) { _ in
            Task { await viewModel.load(apiClient: container.apiClient, authState: authState) }
        }
    }

    // MARK: - Profile Card

    private var profileCard: some View {
        VStack(spacing: 20) {
            VStack(spacing: 14) {
                ZStack {
                    Circle()
                        .fill(LinearGradient.forestHero)
                        .frame(width: 84, height: 84)
                        .overlay(
                            Circle().stroke(Color.brandAccent.opacity(0.4), lineWidth: 2)
                        )
                        .shadow(color: Color.brandAccent.opacity(0.25), radius: 12, x: 0, y: 4)
                    Text(viewModel.profile?.displayName.prefix(1).uppercased() ?? "?")
                        .font(.brandWordmark)
                        .foregroundStyle(.white)
                }

                VStack(spacing: 6) {
                    Text(viewModel.profile?.displayName ?? String(localized: "mypage.profile.loading"))
                        .font(.numeralMedium).fontWeight(.bold)
                        .foregroundStyle(Color.textHeadline)
                    Text(viewModel.profile?.email ?? "")
                        .font(.bodySmall).fontWeight(.medium)
                        .foregroundStyle(Color.brandAccent)
                }
            }
            .padding(.top, Spacing.xxl) // design-lint:ignore — micro/hero spacing

            statsRow
                .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
                .padding(.bottom, Spacing.lg) // design-lint:ignore — micro/hero spacing
        }
        .frame(maxWidth: .infinity)
        .background(Color.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: Radius.xl))
        .overlay(
            RoundedRectangle(cornerRadius: Radius.xl)
                .stroke(Color.hairline, lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.08), radius: 12, x: 0, y: 4)
    }

    // MARK: - Stats Row

    private var statsRow: some View {
        HStack(spacing: 0) {
            statCell(
                label: String(localized: "mypage.stats.height"),
                value: viewModel.profile?.heightCm.map {
                    String(format: String(localized: "mypage.stats.height.format"), Int($0))
                } ?? "-"
            )
            statDivider
            statCell(
                label: String(localized: "mypage.stats.weight"),
                value: viewModel.profile?.weightKg.map {
                    String(format: String(localized: "mypage.stats.weight.format"), $0)
                } ?? "-"
            )
            statDivider
            statCell(
                label: String(localized: "mypage.stats.activity"),
                value: viewModel.activityLevelLabel
            )
            statDivider
            statCell(
                label: String(localized: "mypage.stats.sex"),
                value: viewModel.sexLabel
            )
        }
        .padding(.vertical, Spacing.lg) // design-lint:ignore — micro/hero spacing
        .background(Color.backgroundPage)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
    }

    private var statDivider: some View {
        Rectangle()
            .fill(Color.hairline)
            .frame(width: 1, height: 32)
    }

    private func statCell(label: String, value: String) -> some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.headingSmall).fontWeight(.bold)
                .foregroundStyle(Color.textHeadline)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Text(label)
                .font(.captionXSmall)
                .foregroundStyle(Color.textSecondary)
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: - Menu Sections

    private var menuSections: some View {
        VStack(spacing: 20) {
            MenuSection(title: String(localized: "mypage.section.account")) {
                MenuRow(
                    icon: "person.crop.circle",
                    iconColor: Color.brandSecondary,
                    label: String(localized: "mypage.menu.editProfile")
                ) {
                    viewModel.populateEditFields()
                    showEditSheet = true
                }
            }

            MenuSection(title: String(localized: "mypage.section.app")) {
                ThemeMenuRow(selectedTheme: selectedTheme) { theme in
                    appThemeRawValue = theme.rawValue
                }
                Divider().padding(.leading, 60) // design-lint:ignore — micro/hero spacing
                LanguageMenuRow()
                Divider().padding(.leading, 60) // design-lint:ignore — micro/hero spacing
                NotificationSettingsRow()
            }

            MenuSection(title: String(localized: "mypage.section.info")) {
                MenuRow(
                    icon: "info.circle",
                    iconColor: Color.brandMoss,
                    label: String(localized: "mypage.menu.version"),
                    trailingText: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0",
                    action: {}
                )
                Divider().padding(.leading, 60) // design-lint:ignore — micro/hero spacing
                MenuRow(
                    icon: "book.closed",
                    iconColor: Color.brandMoss,
                    label: String(localized: "mypage.menu.medicalSources")
                ) {
                    showMedicalSources = true
                }
                Divider().padding(.leading, 60) // design-lint:ignore — micro/hero spacing
                MenuLinkRow(
                    icon: "doc.text",
                    iconColor: Color.brandSecondary,
                    label: String(localized: "mypage.menu.terms"),
                    url: URL(string: "https://gainsy.site/terms")!
                )
                Divider().padding(.leading, 60) // design-lint:ignore — micro/hero spacing
                MenuLinkRow(
                    icon: "hand.raised",
                    iconColor: Color.brandSecondary,
                    label: String(localized: "mypage.menu.privacy"),
                    url: URL(string: "https://gainsy.site/privacy")!
                )
            }

            MenuSection(title: "") {
                MenuRow(
                    icon: "rectangle.portrait.and.arrow.right",
                    iconColor: Color.brandWarning,
                    label: String(localized: "mypage.menu.logout")
                ) {
                    viewModel.logout(authState: authState)
                }
                MenuRow(
                    icon: "trash",
                    iconColor: Color.brandDanger,
                    label: String(localized: "mypage.menu.deleteAccount"),
                    isDestructive: true
                ) {
                    showDeleteConfirm = true
                }
            }
        }
    }
}

// MARK: - Edit Profile Sheet

private struct EditProfileSheet: View {
    @ObservedObject var viewModel: MyPageViewModel
    @Binding var isPresented: Bool
    @EnvironmentObject private var container: AppContainer
    @Environment(\.dismiss) private var dismiss

    private var sexOptions: [(String, String)] {
        [
            (String(localized: "profile.sex.male"),   "MALE"),
            (String(localized: "profile.sex.female"), "FEMALE"),
        ]
    }
    let activityOptions: [ActivityOptionRow] = ActivityLevelOption.all.map {
        ActivityOptionRow(label: $0.label, value: $0.value)
    }

    var body: some View {
        NavigationStack {
            ScrollView(showsIndicators: false) {
                VStack(spacing: 16) {
                    EditCard(title: String(localized: "mypage.edit.section.basic")) {
                        EditField(
                            label: String(localized: "mypage.edit.field.displayName"),
                            placeholder: String(localized: "mypage.edit.field.displayName.placeholder"),
                            text: $viewModel.editDisplayName
                        )
                        Divider().padding(.leading, Spacing.lg)
                        EditPickerField(
                            label: String(localized: "mypage.edit.field.sex"),
                            value: viewModel.editSex,
                            options: sexOptions
                        ) { v in
                            viewModel.editSex = v
                        }
                    }

                    EditCard(title: String(localized: "mypage.edit.section.body")) {
                        EditDateField(
                            label: String(localized: "mypage.edit.field.dob"),
                            date: $viewModel.editDateOfBirth
                        )
                        Divider().padding(.leading, Spacing.lg)
                        EditNumericField(
                            label: String(localized: "mypage.edit.field.height"),
                            unit: "cm",
                            text: $viewModel.editHeightCm
                        )
                        Divider().padding(.leading, Spacing.lg)
                        EditNumericField(
                            label: String(localized: "mypage.edit.field.weight"),
                            unit: "kg",
                            text: $viewModel.editWeightKg
                        )
                    }

                    EditCard(title: String(localized: "mypage.edit.section.activity")) {
                        VStack(spacing: 0) {
                            ForEach(activityOptions) { option in
                                Button {
                                    viewModel.editActivityLevel = option.value
                                } label: {
                                    HStack {
                                        Text(option.label)
                                            .font(.bodyMedium)
                                            .foregroundStyle(Color.textPrimary)
                                        Spacer()
                                        if viewModel.editActivityLevel == option.value {
                                            Image(systemName: "checkmark")
                                                .foregroundStyle(Color.brandAccent)
                                                .font(.bodyMedium).fontWeight(.semibold)
                                        }
                                    }
                                    .padding(.vertical, Spacing.md) // design-lint:ignore — micro/hero spacing
                                    .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
                                }
                                if option.value != activityOptions.last?.value {
                                    Divider().padding(.leading, Spacing.lg)
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
                                Text("common.save")
                                    .font(.bodyLarge).fontWeight(.semibold)
                                    .foregroundStyle(.white)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 54)
                        .background(Color.brandPrimary)
                        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
                    }
                    .disabled(viewModel.isLoading)
                }
                .padding(Spacing.xl) // design-lint:ignore — micro/hero spacing
                .padding(.bottom, Spacing.xl) // design-lint:ignore — micro/hero spacing
            }
            .background(Color.backgroundPage)
            .navigationTitle(Text("mypage.edit.title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(String(localized: "common.cancel.button")) { dismiss() }
                        .foregroundStyle(Color.textSecondary)
                }
            }
        }
    }
}

private struct ActivityOptionRow: Identifiable {
    let label: String
    let value: String

    var id: String { value }
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
                    .foregroundStyle(Color.textSecondary)
                    .padding(.horizontal, Spacing.xs) // design-lint:ignore — micro/hero spacing
            }
            VStack(spacing: 0) {
                content()
            }
            .background(Color.surfaceCard)
            .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
            .shadow(color: .black.opacity(0.04), radius: 8, x: 0, y: 2)
        }
    }
}

private struct MenuRow: View {
    let icon: String
    let iconColor: Color
    let label: String
    let trailingText: String?
    /// 텍스트를 danger 색으로 강조 (예: 계정 삭제). 라벨 문자열 비교 대신 명시 플래그를 쓴다 — 영문화 후 비교가 깨지지 않도록.
    let isDestructive: Bool
    let action: () -> Void

    init(
        icon: String,
        iconColor: Color,
        label: String,
        trailingText: String? = nil,
        isDestructive: Bool = false,
        action: @escaping () -> Void
    ) {
        self.icon = icon
        self.iconColor = iconColor
        self.label = label
        self.trailingText = trailingText
        self.isDestructive = isDestructive
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: icon)
                    .font(.bodyMedium).fontWeight(.medium)
                    .foregroundStyle(iconColor)
                    .frame(width: 30, height: 30)
                    .background(iconColor.opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: Radius.sm))

                Text(label)
                    .font(.bodyMedium)
                    .foregroundStyle(isDestructive ? Color.brandDanger : Color.textPrimary)

                Spacer()

                if let t = trailingText {
                    Text(t)
                        .font(.labelSmall)
                        .foregroundStyle(Color.textSecondary)
                } else {
                    Image(systemName: "chevron.right")
                        .font(.captionBold)
                        .foregroundStyle(Color.textSecondary.opacity(0.6))
                }
            }
            .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
            .padding(.vertical, Spacing.lg) // design-lint:ignore — micro/hero spacing
        }
    }
}

private struct MenuLinkRow: View {
    let icon: String
    let iconColor: Color
    let label: String
    let url: URL

    var body: some View {
        Link(destination: url) {
            HStack(spacing: 14) {
                Image(systemName: icon)
                    .font(.bodyMedium).fontWeight(.medium)
                    .foregroundStyle(iconColor)
                    .frame(width: 30, height: 30)
                    .background(iconColor.opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: Radius.sm))

                Text(label)
                    .font(.bodyMedium)
                    .foregroundStyle(Color.textPrimary)

                Spacer()

                Image(systemName: "arrow.up.right")
                    .font(.captionXSmall).fontWeight(.semibold)
                    .foregroundStyle(Color.textSecondary.opacity(0.6))
            }
            .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
            .padding(.vertical, Spacing.lg) // design-lint:ignore — micro/hero spacing
        }
    }
}

private struct ThemeMenuRow: View {
    let selectedTheme: AppTheme
    let onSelect: (AppTheme) -> Void

    var body: some View {
        Menu {
            ForEach(AppTheme.allCases) { theme in
                Button {
                    onSelect(theme)
                } label: {
                    Label(theme.title, systemImage: theme == selectedTheme ? "checkmark" : theme.iconName)
                }
            }
        } label: {
            HStack(spacing: 14) {
                Image(systemName: selectedTheme.iconName)
                    .font(.bodyMedium).fontWeight(.medium)
                    .foregroundStyle(Color.brandAccent)
                    .frame(width: 30, height: 30)
                    .background(Color.brandAccent.opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: Radius.sm))

                Text("화면 모드")
                    .font(.bodyMedium)
                    .foregroundStyle(Color.textPrimary)

                Spacer()

                Text(selectedTheme.title)
                    .font(.labelSmall)
                    .foregroundStyle(Color.brandAccent)

                Image(systemName: "chevron.up.chevron.down")
                    .font(.captionXSmall).fontWeight(.semibold)
                    .foregroundStyle(Color.textSecondary.opacity(0.6))
            }
            .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
            .padding(.vertical, Spacing.lg) // design-lint:ignore — micro/hero spacing
        }
    }
}

// MARK: - Language Menu Row

private struct LanguageMenuRow: View {
    @ObservedObject private var localeManager = LocaleManager.shared
    @State private var showRestartNotice = false

    private var currentLabel: String {
        switch localeManager.current {
        case .system: return String(localized: "settings.language.system")
        case .ko:     return String(localized: "settings.language.korean")
        case .en:     return String(localized: "settings.language.english")
        }
    }

    var body: some View {
        Menu {
            ForEach(AppLanguage.allCases) { language in
                Button {
                    guard language != localeManager.current else { return }
                    localeManager.setLanguage(language)
                    showRestartNotice = true
                } label: {
                    Label {
                        Text(language.displayNameKey)
                    } icon: {
                        if language == localeManager.current {
                            Image(systemName: "checkmark")
                        }
                    }
                }
            }
        } label: {
            HStack(spacing: 14) {
                Image(systemName: "globe")
                    .font(.bodyMedium).fontWeight(.medium)
                    .foregroundStyle(Color.brandAccent)
                    .frame(width: 30, height: 30)
                    .background(Color.brandAccent.opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: Radius.sm))

                Text("settings.language.title")
                    .font(.bodyMedium)
                    .foregroundStyle(Color.textPrimary)

                Spacer()

                Text(currentLabel)
                    .font(.labelSmall)
                    .foregroundStyle(Color.brandAccent)

                Image(systemName: "chevron.up.chevron.down")
                    .font(.captionXSmall).fontWeight(.semibold)
                    .foregroundStyle(Color.textSecondary.opacity(0.6))
            }
            .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
            .padding(.vertical, Spacing.lg) // design-lint:ignore — micro/hero spacing
        }
        .alert(
            Text("settings.language.title"),
            isPresented: $showRestartNotice
        ) {
            Button("common.done", role: .cancel) {}
        } message: {
            Text("settings.language.restart_notice")
        }
    }
}

// MARK: - Notification Settings Row
//
// 알림 권한 상태를 표시하고 탭 시 iOS 시스템 설정으로 이동.
// 시스템 설정에서 변경 후 앱 복귀(scenePhase = .active) 시 상태 재조회.

private struct NotificationSettingsRow: View {
    @Environment(\.scenePhase) private var scenePhase
    @State private var status: UNAuthorizationStatus = .notDetermined

    private var statusText: String {
        switch status {
        case .authorized:   return String(localized: "mypage.notification.status.authorized")
        case .denied:       return String(localized: "mypage.notification.status.denied")
        case .notDetermined: return String(localized: "mypage.notification.status.notDetermined")
        case .provisional:  return String(localized: "mypage.notification.status.provisional")
        case .ephemeral:    return String(localized: "mypage.notification.status.ephemeral")
        @unknown default:   return String(localized: "mypage.notification.status.unknown")
        }
    }

    private var statusColor: Color {
        switch status {
        case .authorized, .provisional, .ephemeral: return Color.brandAccent
        case .denied:                                return Color.brandDanger
        case .notDetermined:                         return Color.textSecondary
        @unknown default:                            return Color.textSecondary
        }
    }

    var body: some View {
        Button(action: openSystemSettings) {
            HStack(spacing: 14) {
                Image(systemName: "bell.badge")
                    .font(.bodyMedium).fontWeight(.medium)
                    .foregroundStyle(Color.brandAccent)
                    .frame(width: 30, height: 30)
                    .background(Color.brandAccent.opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: Radius.sm))

                Text("알림")
                    .font(.bodyMedium)
                    .foregroundStyle(Color.textPrimary)

                Spacer()

                Text(statusText)
                    .font(.labelSmall)
                    .foregroundStyle(statusColor)

                Image(systemName: "chevron.right")
                    .font(.captionBold)
                    .foregroundStyle(Color.textSecondary.opacity(0.6))
            }
            .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
            .padding(.vertical, Spacing.lg) // design-lint:ignore — micro/hero spacing
        }
        .task { await refreshStatus() }
        .onChange(of: scenePhase) { phase in
            if phase == .active {
                Task { await refreshStatus() }
            }
        }
    }

    private func refreshStatus() async {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        await MainActor.run {
            self.status = settings.authorizationStatus
        }
    }

    private func openSystemSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
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
                .foregroundStyle(Color.textSecondary)
                .padding(.horizontal, Spacing.xs) // design-lint:ignore — micro/hero spacing
            VStack(spacing: 0) {
                content()
            }
            .background(Color.surfaceCard)
            .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
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
        .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
        .padding(.vertical, 13) // design-lint:ignore — micro/hero spacing
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
                    .font(.headingSmall)
                    .foregroundStyle(Color.textPrimary)
                    .frame(width: 72)
                Text(unit)
                    .font(.bodySmall)
                    .foregroundStyle(Color.textTertiary)
            }
        }
        .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
        .padding(.vertical, 13) // design-lint:ignore — micro/hero spacing
    }
}

private struct EditDateField: View {
    let label: String
    @Binding var date: Date

    var body: some View {
        HStack {
            Text(label)
                .font(.bodyMedium)
                .foregroundStyle(Color.textSecondary)
                .frame(width: 64, alignment: .leading)
            Spacer()
            DatePicker(
                "",
                selection: $date,
                in: ...Date(),
                displayedComponents: .date
            )
            .labelsHidden()
            .environment(\.locale, Locale(identifier: "ko_KR"))
        }
        .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
        .padding(.vertical, 13) // design-lint:ignore — micro/hero spacing
    }
}

private struct EditPickerField: View {
    let label: String
    let value: String
    let options: [(String, String)]
    let onSelect: (String) -> Void

    private var displayLabel: String {
        options.first(where: { $0.1 == value })?.0 ?? String(localized: "mypage.edit.select")
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
                    .font(.captionXSmall).fontWeight(.semibold)
                    .foregroundStyle(Color.textTertiary)
            }
            .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
            .padding(.vertical, 13) // design-lint:ignore — micro/hero spacing
        }
    }
}
