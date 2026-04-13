import SwiftUI

// MARK: - Splash View
/// 앱 시작 시 표시되는 스플래시 화면 (이미지 2 스타일)
/// 크림 배경 + 다크 그린 원형 아크 + 체크마크 + 잎사귀 로고
struct SplashView: View {
    @State private var ringProgress: CGFloat = 0
    @State private var checkOpacity: Double  = 0
    @State private var leafScale: CGFloat    = 0.4
    @State private var leafOpacity: Double   = 0

    var body: some View {
        ZStack {
            // ── Background ──────────────────────────────────────────
            Color(hex: "#F5F4EC")
                .ignoresSafeArea()

            // ── Brand Logo ──────────────────────────────────────────
            BrandLogoView(
                size: 180,
                color: Color.brandPrimary,
                ringProgress: ringProgress,
                checkOpacity: checkOpacity,
                leafScale: leafScale,
                leafOpacity: leafOpacity
            )
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.9)) {
                ringProgress = 1
            }
            withAnimation(.easeIn(duration: 0.4).delay(0.7)) {
                checkOpacity = 1
            }
            withAnimation(.spring(response: 0.5, dampingFraction: 0.6).delay(0.6)) {
                leafScale   = 1
                leafOpacity = 1
            }
        }
    }
}

// MARK: - Brand Logo (재사용 가능)
struct BrandLogoView: View {
    let size: CGFloat
    var color: Color        = .brandPrimary
    var ringProgress: CGFloat = 1
    var checkOpacity: Double  = 1
    var leafScale: CGFloat    = 1
    var leafOpacity: Double   = 1

    var body: some View {
        ZStack {
            // ── 외곽 원 아크 (약간 얇은 보조 아크) ──────────────────
            Circle()
                .trim(from: 0.05, to: min(0.05 + ringProgress * 0.82, 0.87))
                .stroke(
                    color.opacity(0.35),
                    style: StrokeStyle(lineWidth: size * 0.045, lineCap: .round)
                )
                .frame(width: size * 1.08, height: size * 1.08)
                .rotationEffect(.degrees(108))

            // ── 메인 원 아크 (굵은 주 아크) ─────────────────────────
            Circle()
                .trim(from: 0.0, to: min(ringProgress * 0.80, 0.80))
                .stroke(
                    color,
                    style: StrokeStyle(lineWidth: size * 0.065, lineCap: .round)
                )
                .frame(width: size, height: size)
                .rotationEffect(.degrees(118))
                .shadow(color: color.opacity(0.15), radius: 8, x: 0, y: 4)

            // ── 체크마크 ────────────────────────────────────────────
            Image(systemName: "checkmark")
                .font(.system(size: size * 0.38, weight: .bold, design: .rounded))
                .foregroundStyle(color)
                .opacity(checkOpacity)
                .scaleEffect(checkOpacity == 1 ? 1 : 0.7)
                .animation(.spring(response: 0.4, dampingFraction: 0.65), value: checkOpacity)

            // ── 잎사귀 (아크 끝 부분) ────────────────────────────────
            LeafCluster(color: color)
                .frame(width: size * 0.28, height: size * 0.28)
                .scaleEffect(leafScale)
                .opacity(leafOpacity)
                .offset(
                    x:  size * 0.41,
                    y: -size * 0.28
                )
        }
    }
}

// MARK: - Leaf Cluster (잎사귀 3개 모양)
private struct LeafCluster: View {
    let color: Color

    var body: some View {
        ZStack {
            // 중앙 큰 잎
            LeafShape()
                .fill(color)
                .frame(width: 14, height: 22)
                .rotationEffect(.degrees(-15))

            // 오른쪽 작은 잎
            LeafShape()
                .fill(color.opacity(0.75))
                .frame(width: 10, height: 16)
                .rotationEffect(.degrees(25))
                .offset(x: 9, y: 4)

            // 왼쪽 작은 잎
            LeafShape()
                .fill(color.opacity(0.75))
                .frame(width: 10, height: 16)
                .rotationEffect(.degrees(-45))
                .offset(x: -7, y: 5)
        }
    }
}

// MARK: - Leaf Shape
private struct LeafShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let midX = rect.midX
        let minY = rect.minY
        let maxY = rect.maxY

        path.move(to: CGPoint(x: midX, y: minY))
        path.addCurve(
            to: CGPoint(x: midX, y: maxY),
            control1: CGPoint(x: rect.maxX + rect.width * 0.4, y: rect.height * 0.3),
            control2: CGPoint(x: rect.maxX + rect.width * 0.2, y: rect.height * 0.8)
        )
        path.addCurve(
            to: CGPoint(x: midX, y: minY),
            control1: CGPoint(x: rect.minX - rect.width * 0.2, y: rect.height * 0.8),
            control2: CGPoint(x: rect.minX - rect.width * 0.4, y: rect.height * 0.3)
        )
        path.closeSubpath()
        return path
    }
}
