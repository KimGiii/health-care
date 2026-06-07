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
                Text(String(localized: "tracking.title"))
                    .font(.headingLarge)
                    .foregroundStyle(Color.textPrimary)
                    .multilineTextAlignment(.center)

                Text(String(localized: "tracking.desc"))
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
                    title: String(localized: "tracking.row.purpose.title"),
                    detail: String(localized: "tracking.row.purpose.detail")
                )
                bulletRow(
                    icon: "lock.fill",
                    iconColor: Color(hex: "#2563EB"),
                    title: String(localized: "tracking.row.data.title"),
                    detail: String(localized: "tracking.row.data.detail")
                )
                bulletRow(
                    icon: "arrow.uturn.backward.circle.fill",
                    iconColor: Color(hex: "#7C3AED"),
                    title: String(localized: "tracking.row.change.title"),
                    detail: String(localized: "tracking.row.change.detail")
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
                        Text(String(localized: "tracking.continue"))
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
