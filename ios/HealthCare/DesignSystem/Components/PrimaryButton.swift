import SwiftUI

// MARK: - PrimaryButtonLabel
//
// NavigationLink 등 자체 Button을 갖지 않는 컨테이너에서 동일 스타일을 재사용하기 위한 라벨.

struct PrimaryButtonLabel: View {
    let title: String
    let isEnabled: Bool
    let isLoading: Bool

    init(title: String, isEnabled: Bool = true, isLoading: Bool = false) {
        self.title = title
        self.isEnabled = isEnabled
        self.isLoading = isLoading
    }

    var body: some View {
        Group {
            if isLoading {
                ProgressView().tint(.white)
            } else {
                Text(title).font(.cta)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Spacing.lg)
        .background(isEnabled ? Color.brandPrimary : Color.brandPrimary.opacity(0.3))
        .foregroundStyle(.white)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg, style: .continuous))
        .shadow(
            color: Color.brandPrimary.opacity(isEnabled ? 0.3 : 0),
            radius: 10, x: 0, y: 5
        )
    }
}

// MARK: - PrimaryButton
//
// 앱 표준 주(primary) CTA.
//
// 사용:
// ```
// PrimaryButton("로그인하기", isEnabled: form.isValid, isLoading: vm.isLoading) {
//     Task { await vm.login() }
// }
// ```

struct PrimaryButton: View {
    let title: String
    let isEnabled: Bool
    let isLoading: Bool
    let action: () -> Void

    init(
        _ title: String,
        isEnabled: Bool = true,
        isLoading: Bool = false,
        action: @escaping () -> Void
    ) {
        self.title     = title
        self.isEnabled = isEnabled
        self.isLoading = isLoading
        self.action    = action
    }

    var body: some View {
        Button(action: action) {
            PrimaryButtonLabel(title: title, isEnabled: isEnabled, isLoading: isLoading)
        }
        .disabled(!isEnabled || isLoading)
        .animation(.easeInOut(duration: 0.2), value: isEnabled)
    }
}
