import SwiftUI

/// Google 브랜드 가이드라인을 따르는 로그인 버튼(흰 배경, 회색 보더, 컬러 G 로고, 검정 텍스트).
/// G 로고는 SF Symbol 에 없어 SwiftUI 의 벡터 도형 조합으로 그려 자산 추가 없이 사용한다.
struct GoogleSignInButton: View {
    let isLoading: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.sm) {
                if isLoading {
                    ProgressView()
                        .progressViewStyle(.circular)
                        .tint(Color.textPrimary)
                } else {
                    GoogleGlyph()
                        .frame(width: 18, height: 18) // design-lint:ignore Google 가이드라인(아이콘 18pt)
                }
                Text("Google로 계속하기")
                    .font(.cta)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.lg)
            .background(Color.white)
            .foregroundStyle(Color.black)
            .overlay(
                RoundedRectangle(cornerRadius: Radius.lg, style: .continuous)
                    .stroke(Color.black.opacity(0.12), lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radius.lg, style: .continuous))
        }
        .disabled(isLoading)
    }
}

/// Google "G" 로고 단순화 버전(4색 분할 원형).
/// 브랜드 가이드 색상값을 사용하며, 자산 파일 추가 없이 코드로 렌더한다.
private struct GoogleGlyph: View {
    private let blue   = Color(red: 0.26, green: 0.52, blue: 0.96)
    private let green  = Color(red: 0.20, green: 0.66, blue: 0.33)
    private let yellow = Color(red: 0.98, green: 0.74, blue: 0.02)
    private let red    = Color(red: 0.92, green: 0.26, blue: 0.21)

    var body: some View {
        GeometryReader { proxy in
            let size = min(proxy.size.width, proxy.size.height)
            ZStack {
                // 4분할 호 — Google 로고를 단순화한 표현.
                ArcSlice(start: -45,  end: 45).fill(red)
                ArcSlice(start: 45,   end: 135).fill(yellow)
                ArcSlice(start: 135,  end: 225).fill(green)
                ArcSlice(start: 225,  end: 315).fill(blue)
                Circle()
                    .fill(Color.white)
                    .frame(width: size * 0.42, height: size * 0.42)
                Rectangle()
                    .fill(Color.white)
                    .frame(width: size * 0.5, height: size * 0.12)
                    .offset(x: size * 0.18)
            }
        }
    }
}

private struct ArcSlice: Shape {
    let start: Double
    let end: Double

    func path(in rect: CGRect) -> Path {
        var path = Path()
        let center = CGPoint(x: rect.midX, y: rect.midY)
        let radius = min(rect.width, rect.height) / 2
        path.move(to: center)
        path.addArc(
            center: center,
            radius: radius,
            startAngle: .degrees(start),
            endAngle: .degrees(end),
            clockwise: false
        )
        path.closeSubpath()
        return path
    }
}
