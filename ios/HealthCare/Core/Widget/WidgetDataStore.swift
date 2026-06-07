//
//  WidgetDataStore.swift
//  HealthCare
//
//  App Group UserDefaults에 위젯 스냅샷을 읽고 쓰는 단일 진입점.
//  - 앱(메인 타겟): 기록 직후 save() 호출
//  - 위젯 익스텐션: TimelineProvider에서 load() 호출
//

import Foundation

public struct WidgetDataStore {
    private let defaults: UserDefaults
    private let encoder: JSONEncoder
    private let decoder: JSONDecoder

    public init?(appGroupID: String = WidgetConstants.appGroupID) {
        guard let suite = UserDefaults(suiteName: appGroupID) else {
            assertionFailure("App Group이 양쪽 타겟의 Entitlements에 등록되어 있어야 합니다: \(appGroupID)")
            return nil
        }
        self.defaults = suite

        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        self.encoder = encoder

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        self.decoder = decoder
    }

    // MARK: - Generic R/W

    public func save<T: Encodable>(_ value: T, forKey key: String) {
        do {
            let data = try encoder.encode(value)
            defaults.set(data, forKey: key)
        } catch {
            #if DEBUG
            print("[WidgetDataStore] save 실패 key=\(key) error=\(error)")
            #endif
        }
    }

    public func load<T: Decodable>(_ type: T.Type, forKey key: String) -> T? {
        guard let data = defaults.data(forKey: key) else { return nil }
        do {
            return try decoder.decode(type, from: data)
        } catch {
            #if DEBUG
            print("[WidgetDataStore] load 실패 key=\(key) error=\(error)")
            #endif
            return nil
        }
    }

    public func clear(forKey key: String) {
        defaults.removeObject(forKey: key)
    }

    // MARK: - Calorie

    public func saveCalorie(_ snapshot: CalorieWidgetSnapshot) {
        save(snapshot, forKey: WidgetConstants.StorageKey.calorieSnapshot)
    }

    public func loadCalorie() -> CalorieWidgetSnapshot? {
        load(CalorieWidgetSnapshot.self, forKey: WidgetConstants.StorageKey.calorieSnapshot)
    }
}
