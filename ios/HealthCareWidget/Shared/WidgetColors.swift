//
//  WidgetColors.swift
//  HealthCareWidget
//
//  위젯 3종 공통 색 팔레트. 다크모드에서 어두운 forest tone이 어두운 배경 위에 묻히는
//  문제를 막기 위해 UIColor 동적 색으로 정의한다. 메인 앱의 `Color.brandPrimary` 패턴과 동일.
//
//  Light 모드: 진한 forest (#0F3B24) — 흰 배경 위에서 무게감
//  Dark 모드:  밝은 mint glow (#95E2B5) — 어두운 배경 위에서 가독성
//

import SwiftUI
import UIKit

extension Color {
    /// 주 강조색 — 큰 숫자, 라벨, 헤더.
    static let widgetBrand = Color(UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(hex8: 0x95E2B5)   // light mint glow
            : UIColor(hex8: 0x0F3B24)   // deep forest
    })

    /// 진행 강조 — 링, 프로그레스 바, 차트 라인.
    static let widgetAccent = Color(UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(hex8: 0x7FD9A3)   // mid mint
            : UIColor(hex8: 0x52B788)   // fresh mint
    })

    /// 보조 텍스트 (라벨, 부제). 시스템 `.secondary`보다 더 명확한 위젯 전용 톤.
    static let widgetSecondaryText = Color(UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(white: 1.0, alpha: 0.72)
            : UIColor(white: 0.0, alpha: 0.60)
    })

    /// 링/바 트랙 색.
    static let widgetTrack = Color(UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(white: 1.0, alpha: 0.18)
            : UIColor(hex8: 0x0F3B24).withAlphaComponent(0.12)
    })

    // MARK: 매크로 색 (다크모드에서 가독성 살짝 끌어올림)

    static let widgetProtein = Color(UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(hex8: 0xFFD18C)
            : UIColor(hex8: 0xF6C177)
    })

    static let widgetCarbs = Color(UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(hex8: 0xEF8E72)
            : UIColor(hex8: 0xE07856)
    })

    static let widgetFat = Color(UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(hex8: 0x8DBFA3)
            : UIColor(hex8: 0x6FA287)
    })

    /// 스트릭 불꽃 아이콘.
    static let widgetFlame = Color(UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(hex8: 0xFF9B73)
            : UIColor(hex8: 0xE07856)
    })
}

// MARK: - UIColor hex helper (Widget Extension local)

private extension UIColor {
    convenience init(hex8: UInt32) {
        let r = CGFloat((hex8 >> 16) & 0xFF) / 255.0
        let g = CGFloat((hex8 >> 8) & 0xFF) / 255.0
        let b = CGFloat(hex8 & 0xFF) / 255.0
        self.init(red: r, green: g, blue: b, alpha: 1.0)
    }
}
