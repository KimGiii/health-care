import SwiftUI

// MARK: - Record Navigation Destinations
//
// MainTabView가 푸시 라우팅에서 recordPath에 직접 append할 수 있도록
// value 기반 enum으로 노출. 식사 리마인더 푸시 → 식단 기록 화면으로 직진.

enum RecordDestination: Hashable {
    case diet
    case exercise
    case body
    case progressPhoto
}

// VITALITY — Record Hub
// Editorial entry point. Giant serif display, bento grid, deliberate
// asymmetry. Each route card uses a distinct visual language while sharing
// tokens from the design system.

struct RecordHubView: View {
    @Environment(\.dismiss) private var dismiss
    let showsDismissButton: Bool

    init(showsDismissButton: Bool = true) {
        self.showsDismissButton = showsDismissButton
    }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 0) {
                HubHeroSection(
                    showsDismissButton: showsDismissButton,
                    onDismiss: { dismiss() }
                )

                HubBody()
                    .padding(.top, -30) // lift body into hero // design-lint:ignore — micro/hero spacing
            }
        }
        .background(Color.backgroundPage.ignoresSafeArea())
        .ignoresSafeArea(edges: .top)
        .navigationBarHidden(true)
        .navigationDestination(for: RecordDestination.self) { dest in
            switch dest {
            case .diet:          DietRecordView()
            case .exercise:      ExerciseRecordView()
            case .body:          BodyMeasurementView()
            case .progressPhoto: ProgressPhotoView()
            }
        }
    }
}

// MARK: - Hero Section

private struct HubHeroSection: View {
    let showsDismissButton: Bool
    let onDismiss: () -> Void

    private var todayLabel: String {
        let f = DateFormatter()
        f.dateFormat = "EEEE · d MMM"
        f.locale = Locale(identifier: "en_US")
        return f.string(from: Date()).uppercased()
    }

    private var koDate: String {
        let f = DateFormatter()
        f.locale = LocaleManager.resolvedLocale
        f.dateFormat = String(localized: "home.date.format")
        return f.string(from: Date())
    }

    var body: some View {
        ZStack(alignment: .top) {
            HubBackdrop()
                .frame(height: 340)

            VStack(alignment: .leading, spacing: 0) {
                // Nav
                HStack {
                    if showsDismissButton {
                        Button(action: onDismiss) {
                            Image(systemName: "chevron.left")
                                .font(.bodyMedium).fontWeight(.heavy)
                                .foregroundStyle(.white)
                                .frame(width: 40, height: 40)
                                .background(
                                    Circle().fill(Color.glassLight)
                                        .overlay(Circle().stroke(Color.glassEdge, lineWidth: 0.8))
                                )
                        }
                    } else {
                        Color.clear.frame(width: 40, height: 40)
                    }
                    Spacer()
                    Color.clear.frame(width: 40, height: 40)
                }
                .padding(.top, 54) // design-lint:ignore — micro/hero spacing
                .padding(.horizontal, 22) // design-lint:ignore — micro/hero spacing

                Spacer(minLength: 0)

                // Editorial block
                VStack(alignment: .leading, spacing: 10) {
                    Text(todayLabel)
                        .eyebrowStyle(Color.brandAccentGlow.opacity(0.85))

                    (Text("기록은\n")
                        .foregroundColor(.white)
                     + Text("내일의 나").italic()
                        .foregroundColor(Color.brandAccentGlow)
                     + Text("\n에게 쓰는 편지")
                        .foregroundColor(.white))
                        .font(.system(size: 34, weight: .bold, design: .serif)) // design-lint:ignore — SF Symbol or special
                        .heroTracking()
                        .lineSpacing(-2)

                    Text(koDate)
                        .font(.caption).fontWeight(.medium)
                        .foregroundStyle(.white.opacity(0.55))
                        .padding(.top, 2) // design-lint:ignore — micro/hero spacing
                }
                .padding(.horizontal, 22) // design-lint:ignore — micro/hero spacing
                .padding(.bottom, 58) // design-lint:ignore — micro/hero spacing
            }
        }
    }
}

private struct HubBackdrop: View {
    var body: some View {
        GeometryReader { geo in
            ZStack {
                LinearGradient.forestHero

                RadialGradient(
                    colors: [Color.brandAccent.opacity(0.45), .clear],
                    center: UnitPoint(x: 0.1, y: 0.2),
                    startRadius: 0, endRadius: geo.size.width * 0.85
                )
                .blendMode(.screen)

                RadialGradient(
                    colors: [Color.brandSunrise.opacity(0.28), .clear],
                    center: UnitPoint(x: 0.95, y: 0.9),
                    startRadius: 0, endRadius: geo.size.width * 0.7
                )
                .blendMode(.plusLighter)

                // bottom transition
                VStack(spacing: 0) {
                    Spacer()
                    Rectangle()
                        .fill(Color.backgroundPage)
                        .frame(height: 30)
                        .mask(
                            LinearGradient(
                                colors: [.clear, .black, .black],
                                startPoint: .top, endPoint: .bottom
                            )
                        )
                }
            }
        }
    }
}

// MARK: - Body

private struct HubBody: View {
    @EnvironmentObject private var container: AppContainer
    @State private var latestWeight: Double? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 22) {
            // Prompt row — like an editorial kicker
            HStack(alignment: .firstTextBaseline, spacing: 12) {
                Rectangle()
                    .fill(LinearGradient.sunrise)
                    .frame(width: 22, height: 2)
                    .offset(y: -4)
                VStack(alignment: .leading, spacing: 2) {
                    Text("WHAT TO LOG").eyebrowStyle(Color.textTertiary)
                    Text("무엇을 기록할까요?")
                        .font(.displayMedium).fontWeight(.bold)
                        .foregroundStyle(Color.textHeadline)
                }
                Spacer()
            }

            // Bento grid
            VStack(spacing: 14) {
                // Row 1: large exercise card
                NavigationLink(destination: ExerciseRecordView()) {
                    ExerciseRouteCard()
                }
                .buttonStyle(.plain)

                // Row 2: diet + body
                HStack(spacing: 14) {
                    NavigationLink(destination: DietRecordView()) {
                        DietRouteCard()
                    }
                    .buttonStyle(.plain)

                    NavigationLink(destination: BodyMeasurementView()) {
                        BodyRouteCard(latestWeight: latestWeight)
                    }
                    .buttonStyle(.plain)
                }

                // Row 3: progress photos
                NavigationLink(destination: ProgressPhotoView()) {
                    PhotoRouteCard()
                }
                .buttonStyle(.plain)
            }

            // Small tip / footer
            HubFootnote()
                .padding(.top, Spacing.sm) // design-lint:ignore — micro/hero spacing
        }
        .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing
        .padding(.top, 34) // design-lint:ignore — micro/hero spacing
        .padding(.bottom, 64) // design-lint:ignore — micro/hero spacing
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            Color.backgroundPage
                .clipShape(RoundedCorner(radius: 32, corners: [.topLeft, .topRight]))
        )
        .task {
            let response: MeasurementResponse? = try? await container.apiClient.request(.getLatestBodyMeasurement)
            latestWeight = response?.weightKg
        }
    }
}

private struct RoundedCorner: Shape {
    var radius: CGFloat
    var corners: UIRectCorner

    func path(in rect: CGRect) -> Path {
        Path(UIBezierPath(roundedRect: rect,
                          byRoundingCorners: corners,
                          cornerRadii: CGSize(width: radius, height: radius)).cgPath)
    }
}

// MARK: - Exercise (large, dark, editorial)

private struct ExerciseRouteCard: View {
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: Radius.xl, style: .continuous)
                .fill(LinearGradient.forestHero)

            RoundedRectangle(cornerRadius: Radius.xl, style: .continuous)
                .fill(
                    RadialGradient(
                        colors: [Color.brandAccent.opacity(0.38), .clear],
                        center: UnitPoint(x: 0.85, y: 0.2),
                        startRadius: 10, endRadius: 260
                    )
                )

            HStack(alignment: .top, spacing: 16) {
                VStack(alignment: .leading, spacing: 14) {
                    HStack(spacing: 8) {
                        Text("01").font(.eyebrow)
                            .tracking(1.6)
                            .foregroundStyle(Color.brandAccentGlow)
                        Rectangle()
                            .fill(Color.brandAccentGlow.opacity(0.6))
                            .frame(width: 18, height: 1)
                        Text("STRENGTH")
                            .eyebrowStyle(Color.brandAccentGlow.opacity(0.9))
                    }

                    Text("오늘의 운동")
                        .font(.displayMedium)
                        .foregroundStyle(.white)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)

                    Text("세트 · 횟수 · 무게를\n있는 그대로 기록")
                        .font(.bodySmall).fontWeight(.medium)
                        .foregroundStyle(.white.opacity(0.6))
                        .lineSpacing(1)
                }

                Spacer()

                // Large iconographic mark (not a generic SF glyph wrapper)
                ZStack {
                    Circle()
                        .stroke(Color.white.opacity(0.18), lineWidth: 1)
                        .frame(width: 88, height: 88)
                    Circle()
                        .fill(Color.brandAccentGlow)
                        .frame(width: 62, height: 62)
                    Image(systemName: "dumbbell.fill")
                        .font(.headingLarge)
                        .foregroundStyle(Color.brandDusk)
                }
                .padding(.top, Spacing.xs) // design-lint:ignore — micro/hero spacing
            }
            .padding(22) // design-lint:ignore — micro/hero spacing
        }
        .frame(height: 170)
        .elevation(.forest)
    }
}

// MARK: - Diet (warm bone card)

private struct DietRouteCard: View {
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: Radius.xl, style: .continuous)
                .fill(Color.surfaceCard)
                .overlay(
                    RoundedRectangle(cornerRadius: Radius.xl, style: .continuous)
                        .stroke(Color.cardStroke, lineWidth: 1)
                )

            // warm corner
            RoundedRectangle(cornerRadius: Radius.xl, style: .continuous)
                .fill(
                    RadialGradient(
                        colors: [Color.brandSunrise.opacity(0.25), .clear],
                        center: .bottomLeading, startRadius: 4, endRadius: 180
                    )
                )

            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Text("02")
                        .font(.eyebrow)
                        .tracking(1.6)
                        .foregroundStyle(Color.brandEmber)
                    Spacer()
                    Image(systemName: "arrow.up.right")
                        .font(.captionBold).fontWeight(.heavy)
                        .foregroundStyle(Color.textHeadline.opacity(0.35))
                }

                Spacer()

                Image(systemName: "fork.knife.circle.fill")
                    .font(.system(size: 46)) // design-lint:ignore — SF Symbol hero icon sizing
                    .foregroundStyle(Color.brandAccent.opacity(0.2))
                    .rotationEffect(.degrees(-6))
                    .padding(.bottom, Spacing.md) // design-lint:ignore — micro/hero spacing

                VStack(alignment: .leading, spacing: 3) {
                    Text("식단").eyebrowStyle(Color.textTertiary)
                    Text("오늘 먹은 것")
                        .font(.bodyLarge).fontWeight(.bold)
                        .foregroundStyle(Color.textHeadline)
                    Text("한 끼, 한 숟갈까지")
                        .font(.captionXSmall)
                        .foregroundStyle(Color.textSecondary)
                }
            }
            .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
        .frame(height: 190)
        .elevation(.low)
    }
}

// MARK: - Body (cool accent — intentional color break)

private struct BodyRouteCard: View {
    let latestWeight: Double?

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: Radius.xl, style: .continuous)
                .fill(Color.brandDusk)

            // line topography
            TopoLines()
                .stroke(Color.brandAccent.opacity(0.22), lineWidth: 0.8)
                .clipShape(RoundedRectangle(cornerRadius: Radius.xl, style: .continuous))

            // soft glow
            RoundedRectangle(cornerRadius: Radius.xl, style: .continuous)
                .fill(
                    RadialGradient(
                        colors: [Color.brandAccent.opacity(0.35), .clear],
                        center: .topTrailing, startRadius: 4, endRadius: 180
                    )
                )

            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Text("03")
                        .font(.eyebrow)
                        .tracking(1.6)
                        .foregroundStyle(Color.brandAccentGlow)
                    Spacer()
                    Image(systemName: "arrow.up.right")
                        .font(.captionBold).fontWeight(.heavy)
                        .foregroundStyle(.white.opacity(0.5))
                }

                Spacer()

                HStack(alignment: .bottom, spacing: 4) {
                    if let w = latestWeight {
                        Text(String(format: "%.1f", w))
                            .font(.numeralHero)
                            .foregroundStyle(.white)
                            .lineLimit(1)
                            .minimumScaleFactor(0.58)
                            .allowsTightening(true)
                        Text("kg")
                            .font(.labelSmall)
                            .foregroundStyle(.white.opacity(0.55))
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                            .padding(.bottom, Spacing.sm) // design-lint:ignore — micro/hero spacing
                    } else {
                        Image(systemName: "scalemass.fill")
                            .font(.system(size: 34, weight: .heavy)) // design-lint:ignore — SF Symbol or special
                            .foregroundStyle(.white.opacity(0.45))
                    }
                }
                .padding(.bottom, Spacing.sm) // design-lint:ignore — micro/hero spacing

                VStack(alignment: .leading, spacing: 3) {
                    Text("BODY").eyebrowStyle(Color.brandAccentGlow.opacity(0.85))
                    Text("신체 변화")
                        .font(.bodyLarge).fontWeight(.bold)
                        .foregroundStyle(.white)
                    Text("작은 변화도 곡선이 된다")
                        .font(.captionXSmall)
                        .foregroundStyle(.white.opacity(0.55))
                }
            }
            .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
        .frame(height: 190)
        .elevation(.mid)
    }
}

/// Abstract topographic line pattern — evokes body/elevation.
private struct TopoLines: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        let count = 6
        for i in 0..<count {
            let t = CGFloat(i) / CGFloat(count - 1)
            let y = rect.height * (0.25 + t * 0.55)
            let amp = 6 + t * 4
            p.move(to: CGPoint(x: 0, y: y))
            for x in stride(from: 0, through: rect.width, by: 6) {
                let yy = y + sin((x / rect.width) * .pi * 2 + t * 1.2) * amp
                p.addLine(to: CGPoint(x: x, y: yy))
            }
        }
        return p
    }
}

// MARK: - Progress Photo (wide warm card)

private struct PhotoRouteCard: View {
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: Radius.xl, style: .continuous)
                .fill(
                    LinearGradient(
                        colors: [Color.backgroundPage, Color.surfaceCard],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .overlay(
                    RoundedRectangle(cornerRadius: Radius.xl, style: .continuous)
                        .stroke(Color.brandAccent.opacity(0.25), lineWidth: 1)
                )

            HStack(spacing: 20) {
                VStack(alignment: .leading, spacing: 10) {
                    HStack(spacing: 8) {
                        Text("04")
                            .font(.eyebrow)
                            .tracking(1.6)
                            .foregroundStyle(Color.brandSecondary)
                        Rectangle()
                            .fill(Color.brandSecondary.opacity(0.5))
                            .frame(width: 14, height: 1)
                        Text("PHOTO").eyebrowStyle(Color.brandSecondary.opacity(0.9))
                    }
                    Text("진행 사진")
                        .font(.displayMedium).fontWeight(.bold)
                        .foregroundStyle(Color.textHeadline)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                    Text("변화를 눈으로 확인하는 가장\n강력한 동기부여")
                        .font(.caption).fontWeight(.medium)
                        .foregroundStyle(Color.textHeadline.opacity(0.55))
                        .lineSpacing(2)
                }

                Spacer()

                ZStack {
                    Circle()
                        .fill(Color.brandAccent.opacity(0.15))
                        .frame(width: 72, height: 72)
                    Image(systemName: "camera.fill")
                        .font(.system(size: 26, weight: .semibold)) // design-lint:ignore — SF Symbol or special
                        .foregroundStyle(Color.brandSecondary)
                }
            }
            .padding(22) // design-lint:ignore — micro/hero spacing
        }
        .frame(height: 130)
        .elevation(.low)
    }
}

// MARK: - Footnote

private struct HubFootnote: View {
    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "leaf.fill")
                .font(.captionXSmall).fontWeight(.bold)
                .foregroundStyle(Color.brandAccent)
            Text("꾸준함이 성과를 만듭니다 — 하루 30초면 충분해요.")
                .font(.caption).fontWeight(.medium)
                .foregroundStyle(Color.textSecondary)
            Spacer()
        }
        .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
        .padding(.vertical, Spacing.md) // design-lint:ignore — micro/hero spacing
        .background(
            RoundedRectangle(cornerRadius: Radius.lg, style: .continuous)
                .fill(Color.surfaceCard.opacity(0.5))
        )
    }
}
