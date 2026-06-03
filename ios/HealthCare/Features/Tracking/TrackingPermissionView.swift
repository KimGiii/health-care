import SwiftUI
import AppTrackingTransparency
import AdSupport

struct TrackingPermissionView: View {
    let onFinished: () -> Void

    @State private var isRequesting = false

    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            Image(systemName: "hand.raised.circle.fill")
                .font(.system(size: 76, weight: .regular)) // design-lint:ignore — SF Symbol/hero
                .foregroundStyle(Color.brandPrimary)

            VStack(spacing: 12) {
                Text("맞춤 광고를 위한 허용 요청")
                    .font(.headingLarge)
                    .foregroundStyle(Color.textPrimary)
                    .multilineTextAlignment(.center)

                Text("다음 화면에서 광고 추적 허용 여부를 묻습니다.\n허용 시 관심사에 더 가까운 광고를 보여드리며,\n거부하셔도 모든 기능을 정상 이용하실 수 있습니다.")
                    .font(.bodyMedium)
                    .foregroundStyle(Color.textSecondary)
                    .multilineTextAlignment(.center)
                    .lineSpacing(4)
            }
            .padding(.horizontal, Spacing.xxl) // design-lint:ignore — micro/hero spacing

            VStack(alignment: .leading, spacing: 14) {
                bulletRow(
                    icon: "checkmark.shield.fill",
                    iconColor: Color.brandPrimary,
                    title: "사용 목적",
                    detail: "광고 식별자(IDFA)를 활용한 맞춤 광고 표시"
                )
                bulletRow(
                    icon: "lock.fill",
                    iconColor: Color(hex: "#2563EB"),
                    title: "수집 정보",
                    detail: "건강·신체 측정 데이터는 광고에 사용되지 않습니다"
                )
                bulletRow(
                    icon: "arrow.uturn.backward.circle.fill",
                    iconColor: Color(hex: "#7C3AED"),
                    title: "변경 방법",
                    detail: "iOS 설정 > 개인정보 보호 > 추적에서 언제든 변경"
                )
            }
            .padding(Spacing.xl) // design-lint:ignore — micro/hero spacing
            .background(Color.surfaceCard)
            .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
            .shadow(color: .black.opacity(0.05), radius: 6, x: 0, y: 2)
            .padding(.horizontal, Spacing.xxl) // design-lint:ignore — micro/hero spacing

            Spacer()

            Button {
                Task { await requestTracking() }
            } label: {
                Group {
                    if isRequesting {
                        ProgressView().tint(.white)
                    } else {
                        Text("계속")
                            .font(.bodyLarge).fontWeight(.semibold)
                            .foregroundStyle(.white)
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(height: 54)
                .background(Color.brandPrimary)
                .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
            }
            .disabled(isRequesting)
            .padding(.horizontal, Spacing.xxl) // design-lint:ignore — micro/hero spacing
            .padding(.bottom, Spacing.xxl) // design-lint:ignore — micro/hero spacing
        }
        .background(Color.backgroundPage.ignoresSafeArea())
    }

    private func bulletRow(icon: String, iconColor: Color, title: String, detail: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon)
                .font(.bodyLarge).fontWeight(.medium)
                .foregroundStyle(iconColor)
                .frame(width: 28)

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.bodyMedium).fontWeight(.semibold)
                    .foregroundStyle(Color.textPrimary)
                Text(detail)
                    .font(.bodySmall)
                    .foregroundStyle(Color.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer(minLength: 0)
        }
    }

    @MainActor
    private func requestTracking() async {
        isRequesting = true
        defer { isRequesting = false }

        if ATTrackingManager.trackingAuthorizationStatus == .notDetermined {
            _ = await ATTrackingManager.requestTrackingAuthorization()
        }
        onFinished()
    }
}
