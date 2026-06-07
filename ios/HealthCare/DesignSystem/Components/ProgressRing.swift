import SwiftUI

// MARK: - ProgressRing
//
// 재사용 가능한 원형 진행 링. 3가지 크기(compact/standard/hero).
// 링 내부에 수치+단위를 직접 표시해 가독성을 높인다.
//
// 사용 예:
//   ProgressRing(progress: 0.71, gradient: .ringCalorie, size: .hero,
//                value: "1,420", unit: "kcal", label: "섭취")
//
//   ProgressRing(progress: 0.45, gradient: .ringActivity, size: .standard,
//                value: "45", unit: "min", label: "운동")

// MARK: - Size Variant

enum ProgressRingSize {
    /// 지표 보조용 — 직경 44pt, 스트로크 6pt
    case compact
    /// 카드 내 보조 링 — 직경 88pt, 스트로크 10pt
    case standard
    /// 메인 대시보드 히어로 — 직경 140pt, 스트로크 14pt
    case hero

    var diameter: CGFloat {
        switch self {
        case .compact:  return 44
        case .standard: return 88
        case .hero:     return 140
        }
    }

    var strokeWidth: CGFloat {
        switch self {
        case .compact:  return 6
        case .standard: return 10
        case .hero:     return 14
        }
    }

    /// 링 내부 수치 폰트 (numeralHero / numeralLarge / numeralMedium)
    var valueFont: Font {
        switch self {
        case .compact:  return .system(size: 12, weight: .bold,    design: .rounded)
        case .standard: return .system(size: 22, weight: .heavy,   design: .rounded)
        case .hero:     return .system(size: 36, weight: .heavy,   design: .rounded)
        }
    }

    /// 단위 폰트
    var unitFont: Font {
        switch self {
        case .compact:  return .system(size: 9,  weight: .semibold)
        case .standard: return .system(size: 11, weight: .semibold)
        case .hero:     return .system(size: 13, weight: .semibold)
        }
    }

    /// 라벨(아래 캡션) 폰트
    var labelFont: Font {
        switch self {
        case .compact:  return .system(size: 9,  weight: .medium)
        case .standard: return .system(size: 11, weight: .medium)
        case .hero:     return .system(size: 13, weight: .medium)
        }
    }

    /// 링과 하단 라벨 사이 간격
    var labelSpacing: CGFloat {
        switch self {
        case .compact:  return 5
        case .standard: return 10
        case .hero:     return 12
        }
    }

    /// spring 애니메이션 response
    var springResponse: Double {
        switch self {
        case .compact:  return 0.7
        case .standard: return 0.85
        case .hero:     return 1.0
        }
    }

    /// 링 내부 텍스트가 넘치지 않도록 허용하는 최대 너비
    var innerWidth: CGFloat {
        switch self {
        case .compact:  return diameter - strokeWidth * 2 - 4
        case .standard: return diameter - strokeWidth * 2 - 8
        case .hero:     return diameter - strokeWidth * 2 - 16
        }
    }
}

// MARK: - ProgressRing View

struct ProgressRing: View {
    /// 진행률 (0~1, 클램프는 뷰 내부에서 처리)
    let progress: Double
    let gradient: LinearGradient
    let size: ProgressRingSize

    /// 링 내부 큰 수치 텍스트 (예: "1,420"). nil이면 숫자 숨김
    var value: String? = nil
    /// 수치 옆 단위 (예: "kcal", "min"). nil이면 숨김
    var unit: String? = nil
    /// 링 아래 작은 캡션 (예: "섭취", "운동"). nil이면 숨김
    var label: String? = nil

    /// 트랙(미진행) 색상. 기본값은 light/dark 자동 적응
    var trackColor: Color = Color.ringTrack

    /// 달성 완료 시 반짝이는 pulse 효과 여부
    var showCompletionPulse: Bool = true

    private let clampedProgress: Double

    init(
        progress: Double,
        gradient: LinearGradient,
        size: ProgressRingSize,
        value: String? = nil,
        unit: String? = nil,
        label: String? = nil,
        trackColor: Color = Color.ringTrack,
        showCompletionPulse: Bool = true
    ) {
        self.progress = progress
        self.gradient = gradient
        self.size = size
        self.value = value
        self.unit = unit
        self.label = label
        self.trackColor = trackColor
        self.showCompletionPulse = showCompletionPulse
        self.clampedProgress = min(max(progress, 0), 1)
    }

    var body: some View {
        VStack(spacing: size.labelSpacing) {
            ZStack {
                // 트랙 (미진행 배경 원)
                Circle()
                    .stroke(trackColor, lineWidth: size.strokeWidth)

                // 진행 링
                Circle()
                    .trim(from: 0, to: max(0.005, clampedProgress))
                    .stroke(gradient, style: StrokeStyle(
                        lineWidth: size.strokeWidth,
                        lineCap: .round
                    ))
                    .rotationEffect(.degrees(-90))
                    .animation(
                        .spring(response: size.springResponse, dampingFraction: 0.82),
                        value: clampedProgress
                    )

                // 완료 pulse 오버레이
                if showCompletionPulse && clampedProgress >= 1.0 {
                    Circle()
                        .stroke(gradient, lineWidth: size.strokeWidth * 0.5)
                        .opacity(0.35)
                        .scaleEffect(1.12)
                }

                // 내부 콘텐츠 (수치 + 단위)
                innerContent
            }
            .frame(width: size.diameter, height: size.diameter)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(accessibilityDescription)
            .accessibilityValue(accessibilityValue)

            // 하단 캡션 라벨
            if let label {
                Text(label)
                    .font(size.labelFont)
                    .foregroundStyle(Color.textSecondary)
                    .tracking(0.6)
            }
        }
    }

    // MARK: - Inner content

    @ViewBuilder
    private var innerContent: some View {
        if let value {
            VStack(spacing: size == .hero ? 2 : 1) {
                HStack(alignment: .lastTextBaseline, spacing: size == .compact ? 1 : 2) {
                    Text(value)
                        .font(size.valueFont)
                        .foregroundStyle(Color.textPrimary)
                        .minimumScaleFactor(0.6)
                        .lineLimit(1)

                    if let unit, size != .compact {
                        Text(unit)
                            .font(size.unitFont)
                            .foregroundStyle(Color.textSecondary)
                            .minimumScaleFactor(0.6)
                            .lineLimit(1)
                    }
                }
                .frame(maxWidth: size.innerWidth)

                // compact에서는 단위를 수치 아래에 표시
                if let unit, size == .compact {
                    Text(unit)
                        .font(size.unitFont)
                        .foregroundStyle(Color.textSecondary)
                }
            }
            .frame(maxWidth: size.innerWidth)
        }
    }

    // MARK: - Accessibility

    private var accessibilityDescription: String {
        label ?? String(localized: "ds.progressRing.a11y")
    }

    private var accessibilityValue: String {
        let percent = Int(clampedProgress * 100)
        if let value, let unit {
            return "\(value) \(unit), \(percent)%"
        }
        return "\(percent)%"
    }
}

// MARK: - Preview

#Preview("ProgressRing — 3가지 크기") {
    ZStack {
        Color.brandBone.ignoresSafeArea()

        VStack(spacing: 40) {
            // Hero
            ProgressRing(
                progress: 0.71,
                gradient: .ringCalorie,
                size: .hero,
                value: "1,420",
                unit: "kcal",
                label: "칼로리 섭취"
            )

            HStack(spacing: 40) {
                // Standard
                ProgressRing(
                    progress: 0.75,
                    gradient: .ringActivity,
                    size: .standard,
                    value: "45",
                    unit: "min",
                    label: "운동"
                )

                // Standard (단백질)
                ProgressRing(
                    progress: 0.65,
                    gradient: LinearGradient(
                        colors: [Color.brandAccentGlow, Color.brandAccent],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    size: .standard,
                    value: "98",
                    unit: "g",
                    label: "단백질"
                )
            }

            HStack(spacing: 24) {
                // Compact — 0%, 30%, 100%
                ProgressRing(progress: 0.0,  gradient: .ringCalorie,  size: .compact, value: "0",   unit: "g")
                ProgressRing(progress: 0.3,  gradient: .ringActivity, size: .compact, value: "60",  unit: "g")
                ProgressRing(progress: 1.0,  gradient: .sunrise,      size: .compact, value: "200", unit: "g", showCompletionPulse: true)
            }
        }
        .padding(40)
    }
}
