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

    var displayName: String { nameKo ?? name }

    var muscleGroupLabel: String {
        switch muscleGroup {
        case "CHEST":      return "가슴"
        case "BACK":       return "등"
        case "SHOULDERS":  return "어깨"
        case "BICEPS":     return "이두"
        case "TRICEPS":    return "삼두"
        case "FOREARMS":   return "전완"
        case "CORE":       return "코어"
        case "QUADRICEPS": return "대퇴사두"
        case "HAMSTRINGS": return "햄스트링"
        case "GLUTES":     return "둔근"
        case "CALVES":     return "종아리"
        case "FULL_BODY":  return "전신"
        case "CARDIO":     return "유산소"
        default:           return "기타"
        }
    }

    var exerciseTypeLabel: String {
        switch exerciseType {
        case "STRENGTH":    return "근력"
        case "CARDIO":      return "유산소"
        case "BODYWEIGHT":  return "맨몸"
        case "FLEXIBILITY": return "유연성"
        case "SPORTS":      return "스포츠"
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
        parser.locale = Locale(identifier: "ko_KR")
        guard let date = parser.date(from: sessionDate) else { return sessionDate }
        let display = DateFormatter()
        display.dateFormat = "M월 d일 (E)"
        display.locale = Locale(identifier: "ko_KR")
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
        parser.locale = Locale(identifier: "ko_KR")
        guard let date = parser.date(from: sessionDate) else { return sessionDate }
        let display = DateFormatter()
        display.dateFormat = "yyyy년 M월 d일 (E)"
        display.locale = Locale(identifier: "ko_KR")
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
    var displayExerciseName: String { exerciseNameKo ?? exerciseName ?? "알 수 없음" }

    var setDescription: String {
        switch setType {
        case "WEIGHTED":
            let w = weightKg.map { String(format: "%.1f", $0) + "kg" } ?? ""
            let r = reps.map { "\($0)회" } ?? ""
            return [w, r].filter { !$0.isEmpty }.joined(separator: " × ")
        case "BODYWEIGHT":
            return reps.map { "\($0)회" } ?? "—"
        case "CARDIO":
            var parts: [String] = []
            if let d = durationSeconds {
                let m = d / 60, s = d % 60
                parts.append(s > 0 ? "\(m)분 \(s)초" : "\(m)분")
            }
            if let dist = distanceM {
                parts.append(String(format: "%.0fm", dist))
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

    var displayName: String { exerciseNameKo ?? exerciseName }
}

// MARK: - AI 운동 추정 응답

struct AiExerciseEstimateResponse: Decodable {
    let exerciseName: String
    let muscleGroup: String
    let exerciseType: String
    let metValue: Double
    let confidence: Double
    let disclaimer: String
    let isAiEstimated: Bool
}

struct AiExerciseEstimateRequest: Encodable {
    let exerciseName: String
}
