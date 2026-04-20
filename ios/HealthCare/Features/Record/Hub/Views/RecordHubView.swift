import SwiftUI

struct RecordHubView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 0) {
                HubHeroSection(onDismiss: { dismiss() })

                VStack(spacing: 20) {
                    HStack {
                        Text("무엇을 기록할까요?")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundStyle(Color.textPrimary)
                        Spacer()
                    }

                    // 운동 기록 — 대형 다크 카드
                    NavigationLink(destination: ExerciseRecordView()) {
                        ExerciseRouteCard()
                    }
                    .buttonStyle(.plain)

                    // 식단 + 신체 변화 — 2열 카드
                    HStack(spacing: 16) {
                        NavigationLink(destination: DietRecordView()) {
                            DietRouteCard()
                        }
                        .buttonStyle(.plain)

                        NavigationLink(destination: BodyMeasurementView()) {
                            BodyRouteCard()
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 24)
                .padding(.bottom, 56)
            }
        }
        .ignoresSafeArea(edges: .top)
        .background(Color.surfaceGrouped)
        .navigationBarHidden(true)
    }
}

// MARK: - Hero Section

private struct HubHeroSection: View {
    let onDismiss: () -> Void

    private var todayText: String {
        let f = DateFormatter()
        f.dateFormat = "M월 d일 EEEE"
        f.locale = Locale(identifier: "ko_KR")
        return f.string(from: Date())
    }

    var body: some View {
        ZStack(alignment: .top) {
            HubWaveBackground()
                .frame(height: 290)

            VStack(spacing: 0) {
                Color.clear.frame(height: 54)

                // 내비게이션 바
                HStack {
                    Button(action: onDismiss) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(.white)
                            .padding(10)
                            .background(.white.opacity(0.15))
                            .clipShape(Circle())
                    }
                    Spacer()
                    Text("VITALITY")
                        .font(.system(size: 18, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                    Spacer()
                    Color.clear.frame(width: 40, height: 40)
                }
                .padding(.horizontal, 20)

                Spacer(minLength: 0)

                VStack(spacing: 6) {
                    Text(todayText)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(.white.opacity(0.7))
                    Text("기록")
                        .font(.system(size: 32, weight: .bold))
                        .foregroundStyle(.white)
                    Text("꾸준한 기록이 건강을 만듭니다")
                        .font(.system(size: 14))
                        .foregroundStyle(.white.opacity(0.65))
                }
                .padding(.bottom, 64)
            }
        }
    }
}

// MARK: - Wave Background

private struct HubWaveBackground: View {
    var body: some View {
        GeometryReader { geo in
            ZStack {
                Color.brandPrimary

                Ellipse()
                    .fill(Color.brandSecondary.opacity(0.55))
                    .frame(width: geo.size.width * 0.75, height: geo.size.height * 0.65)
                    .offset(x: geo.size.width * 0.25, y: -geo.size.height * 0.12)
                    .rotationEffect(.degrees(-18))

                HubWaveCurve()
                    .fill(Color.surfaceGrouped)
                    .frame(height: 80)
                    .frame(maxWidth: .infinity)
                    .offset(y: geo.size.height - 40)
            }
        }
    }
}

private struct HubWaveCurve: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 0, y: rect.height))
        path.addCurve(
            to: CGPoint(x: rect.width, y: rect.height),
            control1: CGPoint(x: rect.width * 0.25, y: 0),
            control2: CGPoint(x: rect.width * 0.75, y: rect.height * 0.6)
        )
        path.addLine(to: CGPoint(x: rect.width, y: rect.height))
        path.addLine(to: CGPoint(x: 0, y: rect.height))
        path.closeSubpath()
        return path
    }
}

// MARK: - 운동 기록 카드 (대형 다크)

private struct ExerciseRouteCard: View {
    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            RoundedRectangle(cornerRadius: 24)
                .fill(Color.brandPrimary)
                .frame(height: 156)

            Ellipse()
                .fill(Color.brandSecondary.opacity(0.45))
                .frame(width: 180, height: 120)
                .rotationEffect(.degrees(-25))
                .offset(x: 50, y: 18)

            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 10) {
                    HStack(spacing: 8) {
                        Image(systemName: "figure.strengthtraining.traditional")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(.white)
                            .padding(10)
                            .background(.white.opacity(0.15))
                            .clipShape(Circle())
                        Text("운동 기록")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(.white.opacity(0.75))
                    }
                    Text("오늘의 운동을\n기록해보세요")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(.white)
                        .lineSpacing(2)
                }
                Spacer()
                Image(systemName: "arrow.right.circle.fill")
                    .font(.system(size: 38))
                    .foregroundStyle(.white.opacity(0.8))
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 20)
        }
        .clipped()
        .shadow(color: Color.brandPrimary.opacity(0.40), radius: 16, x: 0, y: 8)
    }
}

// MARK: - 식단 기록 카드

private struct DietRouteCard: View {
    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            RoundedRectangle(cornerRadius: 20)
                .fill(Color.brandSurface)

            Circle()
                .fill(Color.brandAccent.opacity(0.28))
                .frame(width: 100, height: 100)
                .offset(x: 26, y: 30)

            VStack(alignment: .leading, spacing: 0) {
                Image(systemName: "fork.knife")
                    .font(.system(size: 19, weight: .semibold))
                    .foregroundStyle(Color.brandPrimary)
                    .padding(11)
                    .background(Color.white.opacity(0.85))
                    .clipShape(Circle())

                Spacer()

                VStack(alignment: .leading, spacing: 4) {
                    Text("식단 기록")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(Color.brandPrimary)
                    Text("오늘 먹은 것을\n기록하세요")
                        .font(.system(size: 12))
                        .foregroundStyle(Color.textSecondary)
                        .lineSpacing(2)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(18)
        }
        .frame(height: 174)
        .clipped()
        .shadow(color: .black.opacity(0.07), radius: 10, x: 0, y: 4)
    }
}

// MARK: - 신체 변화 카드

private struct BodyRouteCard: View {
    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            RoundedRectangle(cornerRadius: 20)
                .fill(Color(hex: "#EAF4FF"))

            Circle()
                .fill(Color.blue.opacity(0.14))
                .frame(width: 100, height: 100)
                .offset(x: 26, y: 30)

            VStack(alignment: .leading, spacing: 0) {
                Image(systemName: "scalemass.fill")
                    .font(.system(size: 19, weight: .semibold))
                    .foregroundStyle(Color(hex: "#2563EB"))
                    .padding(11)
                    .background(Color.white.opacity(0.85))
                    .clipShape(Circle())

                Spacer()

                VStack(alignment: .leading, spacing: 4) {
                    Text("신체 변화")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(Color(hex: "#1E40AF"))
                    Text("몸의 변화를\n추적하세요")
                        .font(.system(size: 12))
                        .foregroundStyle(Color.textSecondary)
                        .lineSpacing(2)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(18)
        }
        .frame(height: 174)
        .clipped()
        .shadow(color: .black.opacity(0.07), radius: 10, x: 0, y: 4)
    }
}
