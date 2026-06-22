import Foundation

/// 식품 검색·AI 추정·오류를 단일 열거형으로 표현한다.
/// 동시에 검색 중이면서 AI 추정 중인 상태는 불가능하므로 sum type으로 강제한다.
enum FoodEntryState {
    case idle
    case searching(query: String)
    case results(query: String, items: [FoodCatalogItem])
    case aiEstimating(query: String, items: [FoodCatalogItem])
    case aiEstimated(query: String, items: [FoodCatalogItem], estimate: AiNutritionEstimateResponse)
    case failed(query: String, items: [FoodCatalogItem], message: String)

    var query: String {
        switch self {
        case .idle:
            return ""
        case .searching(let q):
            return q
        case .results(let q, _):
            return q
        case .aiEstimating(let q, _):
            return q
        case .aiEstimated(let q, _, _):
            return q
        case .failed(let q, _, _):
            return q
        }
    }

    var items: [FoodCatalogItem] {
        switch self {
        case .idle, .searching:
            return []
        case .results(_, let i):
            return i
        case .aiEstimating(_, let i):
            return i
        case .aiEstimated(_, let i, _):
            return i
        case .failed(_, let i, _):
            return i
        }
    }

    var aiEstimate: AiNutritionEstimateResponse? {
        if case .aiEstimated(_, _, let e) = self { return e }
        return nil
    }

    var isSearching: Bool {
        if case .searching = self { return true }
        return false
    }

    var isAiEstimating: Bool {
        if case .aiEstimating = self { return true }
        return false
    }

    var failureMessage: String? {
        if case .failed(_, _, let m) = self { return m }
        return nil
    }
}
