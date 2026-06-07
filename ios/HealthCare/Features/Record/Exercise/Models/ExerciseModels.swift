import Foundation

// MARK: - Catalog

struct ExerciseCatalogItem: Decodable, Identifiable, Sendable {
    let id: Int
    let name: String
    let nameKo: String?
    let muscleGroup: String
    let exerciseType: String
    let metValue: Double?
    let custom: Bool

    /// 현재 locale 이 한국어이면 nameKo 우선, 아니면 원본 영문 name.
    /// `Locale.preferredLanguages.first` 가 AppleLanguages override 를 반영한다.
    var displayName: String {
        let prefersKo = (Locale.preferredLanguages.first ?? "").hasPrefix("ko")
        if prefersKo, let ko = nameKo, !ko.isEmpty { return ko }
        return name
    }

    var muscleGroupLabel: String {
        switch muscleGroup {
        case "CHEST":      return String(localized: "exercise.muscle.chest")
        case "BACK":       return String(localized: "exercise.muscle.back")
        case "SHOULDERS":  return String(localized: "exercise.muscle.shoulders")
        case "BICEPS":     return String(localized: "exercise.muscle.biceps")
        case "TRICEPS":    return String(localized: "exercise.muscle.triceps")
        case "FOREARMS":   return String(localized: "exercise.muscle.forearms")
        case "CORE":       return String(localized: "exercise.muscle.core")
        case "QUADRICEPS": return String(localized: "exercise.muscle.quadriceps")
        case "HAMSTRINGS": return String(localized: "exercise.muscle.hamstrings")
        case "GLUTES":     return String(localized: "exercise.muscle.glutes")
        case "CALVES":     return String(localized: "exercise.muscle.calves")
        case "FULL_BODY":  return String(localized: "exercise.muscle.fullBody")
        case "CARDIO":     return String(localized: "exercise.muscle.cardio")
        default:           return String(localized: "exercise.muscle.other")
        }
    }

    var exerciseTypeLabel: String {
        switch exerciseType {
        case "STRENGTH":    return String(localized: "exercise.type.strength")
        case "CARDIO":      return String(localized: "exercise.type.cardio")
        case "BODYWEIGHT":  return String(localized: "exercise.type.bodyweight")
        case "FLEXIBILITY": return String(localized: "exercise.type.flexibility")
        case "SPORTS":      return String(localized: "exercise.type.sports")
        default:            return exerciseType
        }
    }
}

// MARK: - Session List

struct SessionListResponse: Decodable {
    let content: [SessionSummary]
    let pageNumber: Int
    let pageSize: Int
    let totalElements: Int
    let totalPages: Int
    let first: Bool
    let last: Bool
}

struct SessionSummary: Decodable, Identifiable, Sendable {
    let sessionId: Int
    let sessionDate: String   // "yyyy-MM-dd"
    let durationMinutes: Int?
    let totalVolumeKg: Double?
    let caloriesBurned: Double?
    let calorieEstimateMethod: String
    let notes: String?

    var id: Int { sessionId }

    var formattedDate: String {
        let parser = DateFormatter()
        parser.dateFormat = "yyyy-MM-dd"
        parser.locale = Locale(identifier: "en_US_POSIX")
        guard let date = parser.date(from: sessionDate) else { return sessionDate }
        let display = DateFormatter()
        display.locale = LocaleManager.resolvedLocale
        display.dateFormat = String(localized: "diet.date.format.shortKR")
        return display.string(from: date)
    }
}

// MARK: - Session Detail

struct SessionDetail: Decodable, Identifiable, Sendable {
    let sessionId: Int
    let sessionDate: String
    let startedAt: String?
    let endedAt: String?
    let durationMinutes: Int?
    let totalVolumeKg: Double?
    let caloriesBurned: Double?
    let calorieEstimateMethod: String
    let notes: String?
    let sets: [SetDetail]

    var id: Int { sessionId }

    var formattedDate: String {
        let parser = DateFormatter()
        parser.dateFormat = "yyyy-MM-dd"
        parser.locale = Locale(identifier: "en_US_POSIX")
        guard let date = parser.date(from: sessionDate) else { return sessionDate }
        let display = DateFormatter()
        display.locale = LocaleManager.resolvedLocale
        display.dateFormat = String(localized: "diet.date.format.longKR")
        return display.string(from: date)
    }

    /// 운동 카탈로그 ID 기준으로 세트를 그룹핑
    var setsByExercise: [(exerciseName: String, sets: [SetDetail])] {
        var order: [Int] = []
        var groups: [Int: (name: String, sets: [SetDetail])] = [:]
        for s in sets {
            if groups[s.exerciseCatalogId] == nil {
                order.append(s.exerciseCatalogId)
                groups[s.exerciseCatalogId] = (s.displayExerciseName, [])
            }
            groups[s.exerciseCatalogId]!.sets.append(s)
        }
        return order.compactMap { id in
            groups[id].map { (exerciseName: $0.name, sets: $0.sets) }
        }
    }
}

struct SetDetail: Decodable, Identifiable, Sendable {
    let setId: Int
    let exerciseCatalogId: Int
    let exerciseName: String?
    let exerciseNameKo: String?
    let muscleGroup: String?
    let setNumber: Int
    let setType: String
    let weightKg: Double?
    let reps: Int?
    let durationSeconds: Int?
    let distanceM: Double?
    let restSeconds: Int?
    let personalRecord: Bool

    var id: Int { setId }
    var displayExerciseName: String {
        let prefersKo = (Locale.preferredLanguages.first ?? "").hasPrefix("ko")
        if prefersKo, let ko = exerciseNameKo, !ko.isEmpty { return ko }
        return exerciseName ?? String(localized: "exercise.set.unknownName")
    }

    var setDescription: String {
        switch setType {
        case "WEIGHTED":
            let w = weightKg.map { String(format: String(localized: "exercise.set.weight"), $0) } ?? ""
            let r = reps.map { String(format: String(localized: "exercise.set.reps"), $0) } ?? ""
            return [w, r].filter { !$0.isEmpty }.joined(separator: " × ")
        case "BODYWEIGHT":
            return reps.map { String(format: String(localized: "exercise.set.reps"), $0) } ?? "—"
        case "CARDIO":
            var parts: [String] = []
            if let d = durationSeconds {
                let m = d / 60, s = d % 60
                parts.append(s > 0
                    ? String(format: String(localized: "exercise.set.duration.minSec"), m, s)
                    : String(format: String(localized: "exercise.set.duration.min"), m))
            }
            if let dist = distanceM {
                parts.append(String(format: String(localized: "exercise.set.dist"), dist))
            }
            return parts.isEmpty ? "—" : parts.joined(separator: " / ")
        default:
            return "—"
        }
    }
}

// MARK: - Create Session

struct CreateSessionRequest: Encodable {
    let sessionDate: String     // "yyyy-MM-dd"
    let startedAt: String?
    let endedAt: String?
    let notes: String?
    let sets: [CreateSetRequest]
}

struct CreateSetRequest: Encodable {
    let exerciseCatalogId: Int
    let setNumber: Int
    let setType: String
    let weightKg: Double?
    let reps: Int?
    let durationSeconds: Int?
    let distanceM: Double?
    let restSeconds: Int?
    let notes: String?
}

// MARK: - Create Session Response

struct CreateSessionResponse: Decodable, Sendable {
    let sessionId: Int
    let sessionDate: String
    let durationMinutes: Int?
    let totalVolumeKg: Double?
    let caloriesBurned: Double?
    let calorieEstimateMethod: String
    let setCount: Int
    let newPersonalRecords: [PersonalRecordInfo]
}

struct PersonalRecordInfo: Decodable, Sendable {
    let exerciseCatalogId: Int
    let exerciseName: String
    let exerciseNameKo: String?
    let weightKg: Double?
    let reps: Int?

    var displayName: String {
        let prefersKo = (Locale.preferredLanguages.first ?? "").hasPrefix("ko")
        if prefersKo, let ko = exerciseNameKo, !ko.isEmpty { return ko }
        return exerciseName
    }
}

// MARK: - AI 운동 추정 응답

struct AiExerciseEstimateResponse: Decodable {
    let exerciseName: String
    let muscleGroup: String
    let exerciseType: String
    let metValue: Double
    let confidence: Double
    let disclaimer: String
    let aiEstimated: Bool
}

struct AiExerciseEstimateRequest: Encodable {
    let exerciseName: String
}
