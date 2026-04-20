import SwiftUI

struct BodyMeasurementView: View {
    @StateObject private var viewModel = BodyMeasurementViewModel()
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 0) {
                BodyHeroSection(onDismiss: { dismiss() })

                VStack(spacing: 20) {
                    // 측정 항목 안내 카드
                    measurementTypesCard

                    // Coming soon 안내
                    comingSoonCard
                }
                .padding(.horizontal, 20)
                .padding(.top, 24)
                .padding(.bottom, 56)
            }
        }
        .ignoresSafeArea(edges: .top)
        .background(Color.surfaceGrouped)
        .navigationBarHidden(true)
        .task { await viewModel.load() }
    }

    // MARK: - 측정 항목 카드

    private var measurementTypesCard: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("기록 항목")
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(Color.textPrimary)

            LazyVGrid(
                columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())],
                spacing: 12
            ) {
                MeasurementTypeCell(icon: "scalemass.fill",  label: "체중",    color: Color(hex: "#2563EB"))
                MeasurementTypeCell(icon: "percent",         label: "체지방률", color: Color(hex: "#7C3AED"))
                MeasurementTypeCell(icon: "figure.arms.open",label: "근육량",   color: Color.brandPrimary)
                MeasurementTypeCell(icon: "ruler",           label: "허리둘레", color: Color(hex: "#DC2626"))
                MeasurementTypeCell(icon: "heart.fill",      label: "BMI",     color: Color(hex: "#EA580C"))
                MeasurementTypeCell(icon: "chart.line.uptrend.xyaxis", label: "추이", color: Color.brandAccent)
            }
        }
        .padding(20)
        .background(Color.surfacePrimary)
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .shadow(color: .black.opacity(0.05), radius: 8, x: 0, y: 3)
    }

    // MARK: - Coming Soon 카드

    private var comingSoonCard: some View {
        VStack(spacing: 24) {
            ZStack {
                Circle()
                    .fill(Color(hex: "#EAF4FF"))
                    .frame(width: 96, height: 96)
                Image(systemName: "scalemass.fill")
                    .font(.system(size: 44))
                    .foregroundStyle(Color(hex: "#2563EB").opacity(0.7))
            }
            .padding(.top, 12)

            VStack(spacing: 8) {
                Text("신체 변화 기록")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(Color.textPrimary)
                Text("체중, 체지방률, 근육량 등\n신체 변화를 꾸준히 기록하고\n목표를 향한 여정을 확인하세요")
                    .font(.system(size: 14))
                    .foregroundStyle(Color.textSecondary)
                    .multilineTextAlignment(.center)
                    .lineSpacing(4)
            }

            Text("준비 중")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 28)
                .padding(.vertical, 12)
                .background(Color(hex: "#2563EB").opacity(0.75))
                .clipShape(Capsule())
                .padding(.bottom, 24)
        }
        .frame(maxWidth: .infinity)
        .background(Color.surfacePrimary)
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .shadow(color: .black.opacity(0.05), radius: 8, x: 0, y: 3)
    }
}

// MARK: - Hero Section

private struct BodyHeroSection: View {
    let onDismiss: () -> Void

    var body: some View {
        ZStack(alignment: .top) {
            BodyWaveBackground()
                .frame(height: 270)

            VStack(spacing: 0) {
                Color.clear.frame(height: 54)

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
                    Text("신체 변화")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundStyle(.white)
                    Spacer()
                    Color.clear.frame(width: 40, height: 40)
                }
                .padding(.horizontal, 20)

                Spacer(minLength: 0)

                VStack(spacing: 6) {
                    Image(systemName: "scalemass.fill")
                        .font(.system(size: 36, weight: .semibold))
                        .foregroundStyle(.white.opacity(0.9))
                    Text("몸의 변화를 기록하세요")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(.white)
                    Text("꾸준한 측정이 변화를 만듭니다")
                        .font(.system(size: 13))
                        .foregroundStyle(.white.opacity(0.65))
                }
                .padding(.bottom, 60)
            }
        }
    }
}

// MARK: - Wave Background

private struct BodyWaveBackground: View {
    var body: some View {
        GeometryReader { geo in
            ZStack {
                // 블루 계열 그라디언트
                LinearGradient(
                    colors: [Color(hex: "#1E3A5F"), Color(hex: "#2563EB").opacity(0.85)],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )

                Ellipse()
                    .fill(Color.white.opacity(0.10))
                    .frame(width: geo.size.width * 0.75, height: geo.size.height * 0.65)
                    .offset(x: geo.size.width * 0.25, y: -geo.size.height * 0.12)
                    .rotationEffect(.degrees(-18))

                BodyWaveCurve()
                    .fill(Color.surfaceGrouped)
                    .frame(height: 80)
                    .frame(maxWidth: .infinity)
                    .offset(y: geo.size.height - 40)
            }
        }
    }
}

private struct BodyWaveCurve: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 0, y: rect.height))
        path.addCurve(
            to: CGPoint(x: rect.width, y: rect.height),
            control1: CGPoint(x: rect.width * 0.28, y: 0),
            control2: CGPoint(x: rect.width * 0.72, y: rect.height * 0.55)
        )
        path.addLine(to: CGPoint(x: rect.width, y: rect.height))
        path.addLine(to: CGPoint(x: 0, y: rect.height))
        path.closeSubpath()
        return path
    }
}

// MARK: - Measurement Type Cell

private struct MeasurementTypeCell: View {
    let icon: String
    let label: String
    let color: Color

    var body: some View {
        VStack(spacing: 8) {
            ZStack {
                RoundedRectangle(cornerRadius: 14)
                    .fill(color.opacity(0.10))
                    .frame(width: 52, height: 52)
                Image(systemName: icon)
                    .font(.system(size: 20, weight: .medium))
                    .foregroundStyle(color)
            }
            Text(label)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Color.textSecondary)
        }
    }
}
