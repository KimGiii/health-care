import Foundation
import SwiftUI

enum AppLanguage: String, CaseIterable, Identifiable {
    case system
    case ko
    case en

    var id: String { rawValue }

    var bundleCode: String? {
        switch self {
        case .system: return nil
        case .ko: return "ko"
        case .en: return "en"
        }
    }

    var displayNameKey: LocalizedStringKey {
        switch self {
        case .system: return "settings.language.system"
        case .ko: return "settings.language.korean"
        case .en: return "settings.language.english"
        }
    }
}

@MainActor
final class LocaleManager: ObservableObject {
    static let shared = LocaleManager()

    private enum Keys {
        static let preferredLanguage = "preferredLanguage"
        static let didInitializeDefault = "preferredLanguage.didInitializeDefault"
        static let appleLanguages = "AppleLanguages"
    }

    @Published private(set) var current: AppLanguage

    private let defaults: UserDefaults

    private init(defaults: UserDefaults = .standard) {
        self.defaults = defaults

        if !defaults.bool(forKey: Keys.didInitializeDefault) {
            let initial = Self.regionBasedDefault()
            defaults.set(initial.rawValue, forKey: Keys.preferredLanguage)
            defaults.set(true, forKey: Keys.didInitializeDefault)
            Self.applyToAppleLanguages(initial, defaults: defaults)
            self.current = initial
        } else {
            let raw = defaults.string(forKey: Keys.preferredLanguage) ?? AppLanguage.system.rawValue
            let resolved = AppLanguage(rawValue: raw) ?? .system
            self.current = resolved
            Self.applyToAppleLanguages(resolved, defaults: defaults)
        }
    }

    func setLanguage(_ language: AppLanguage) {
        defaults.set(language.rawValue, forKey: Keys.preferredLanguage)
        Self.applyToAppleLanguages(language, defaults: defaults)
        current = language
    }

    /// Locale used for backend `Accept-Language` header.
    var effectiveLocale: Locale {
        switch current {
        case .system:
            return Locale.current
        case .ko:
            return Locale(identifier: "ko_KR")
        case .en:
            return Locale(identifier: "en_US")
        }
    }

    var acceptLanguageHeader: String {
        switch current {
        case .system:
            // Non-Korea regions default to en; Korea defaults to ko.
            return Self.regionBasedDefault() == .ko ? "ko" : "en"
        case .ko:
            return "ko"
        case .en:
            return "en"
        }
    }

    // MARK: - Helpers

    /// Region 또는 선호 언어 중 하나라도 한국어/한국이면 ko 로 기본값 설정.
    /// 시뮬레이터는 region 이 US 로 기본 설정돼 한국어 시스템 사용자도 en 으로 빠지는 문제를 보완하고,
    /// 한국어를 쓰는 해외 거주 사용자도 자연스럽게 한국어로 진입하게 한다.
    private static func regionBasedDefault() -> AppLanguage {
        let regionCode: String?
        if #available(iOS 16.0, *) {
            regionCode = Locale.current.region?.identifier
        } else {
            regionCode = Locale.current.regionCode
        }
        let preferredLanguage = Locale.preferredLanguages.first ?? ""
        let prefersKorean = preferredLanguage.hasPrefix("ko")
        return (regionCode == "KR" || prefersKorean) ? .ko : .en
    }

    private static func applyToAppleLanguages(_ language: AppLanguage, defaults: UserDefaults) {
        guard let code = language.bundleCode else {
            defaults.removeObject(forKey: Keys.appleLanguages)
            return
        }
        defaults.set([code], forKey: Keys.appleLanguages)
    }
}
