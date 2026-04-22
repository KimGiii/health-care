import Foundation

enum HTTPMethod: String {
    case GET, POST, PATCH, DELETE
}

enum APIEndpoint {
    // Auth
    case register(body: Data)
    case login(body: Data)
    case refreshToken(body: Data)
    case logout

    // User
    case getProfile
    case updateProfile(body: Data)
    case deleteAccount

    // Exercise
    case createExerciseSession(body: Data)
    case getExerciseSessions(from: String?, to: String?, page: Int, size: Int)
    case getExerciseSession(id: Int)
    case deleteExerciseSession(id: Int)
    case getExerciseCatalog(query: String?)

    // Diet - Logs
    case createDietLog(body: Data)
    case getDietLogs(from: String?, to: String?, page: Int, size: Int)
    case getDietLog(id: Int)
    case deleteDietLog(id: Int)
    case initiateMealPhotoAnalysis(body: Data)
    case analyzeMealPhoto(id: Int, body: Data)
    case getMealPhotoAnalysis(id: Int)
    case confirmMealPhotoAnalysis(id: Int, body: Data)
    // Diet - Catalog
    case getFoodCatalog(query: String?)
    // Diet - External Foods
    case searchExternalFoods(query: String, source: String, page: Int, size: Int)
    case importExternalFood(body: Data)

    // Body Measurement
    case createBodyMeasurement(body: Data)
    case getBodyMeasurements(page: Int, size: Int)
    case getLatestBodyMeasurement
    case getBodyMeasurementAtOrBefore(date: String)
    case getBodyMeasurement(id: Int)
    case deleteBodyMeasurement(id: Int)
    case initiatePhotoUpload(body: Data)
    case registerProgressPhoto(body: Data)
    case getProgressPhotos(photoType: String?, page: Int, size: Int)

    // Goal
    case createGoal(body: Data)
    case getGoals
    case getGoal(id: Int)
    case updateGoal(id: Int, body: Data)
    case deleteGoal(id: Int)
    case getGoalProgress(id: Int)
}

extension APIEndpoint {
    var path: String {
        switch self {
        case .register:                          return "/api/v1/auth/register"
        case .login:                             return "/api/v1/auth/login"
        case .refreshToken:                      return "/api/v1/auth/token/refresh"
        case .logout:                            return "/api/v1/auth/logout"
        case .getProfile, .updateProfile, .deleteAccount:
                                                 return "/api/v1/users/me"
        case .createExerciseSession, .getExerciseSessions:
                                                 return "/api/v1/exercise/sessions"
        case .getExerciseSession(let id),
             .deleteExerciseSession(let id):     return "/api/v1/exercise/sessions/\(id)"
        case .getExerciseCatalog:                return "/api/v1/exercise/catalog"
        case .createDietLog, .getDietLogs:       return "/api/v1/diet/logs"
        case .getDietLog(let id),
             .deleteDietLog(let id):             return "/api/v1/diet/logs/\(id)"
        case .initiateMealPhotoAnalysis:         return "/api/v1/diet/photo-analyses/initiate"
        case .analyzeMealPhoto(let id, _):       return "/api/v1/diet/photo-analyses/\(id)/analyze"
        case .getMealPhotoAnalysis(let id):      return "/api/v1/diet/photo-analyses/\(id)"
        case .confirmMealPhotoAnalysis(let id, _):
                                                 return "/api/v1/diet/photo-analyses/\(id)/confirm"
        case .getFoodCatalog:                    return "/api/v1/diet/catalog"
        case .searchExternalFoods:               return "/api/v1/diet/external-foods/search"
        case .importExternalFood:                return "/api/v1/diet/external-foods/import"
        case .createBodyMeasurement, .getBodyMeasurements:
                                                 return "/api/v1/body-measurements"
        case .getLatestBodyMeasurement:          return "/api/v1/body-measurements/latest"
        case .getBodyMeasurementAtOrBefore:      return "/api/v1/body-measurements/at-or-before"
        case .getBodyMeasurement(let id),
             .deleteBodyMeasurement(let id):     return "/api/v1/body-measurements/\(id)"
        case .initiatePhotoUpload:               return "/api/v1/body-measurements/photos/upload-url"
        case .registerProgressPhoto, .getProgressPhotos:
                                                 return "/api/v1/body-measurements/photos"
        case .createGoal, .getGoals:             return "/api/v1/goals"
        case .getGoal(let id),
             .updateGoal(let id, _),
             .deleteGoal(let id):                return "/api/v1/goals/\(id)"
        case .getGoalProgress(let id):           return "/api/v1/goals/\(id)/progress"
        }
    }

    var method: HTTPMethod {
        switch self {
        case .register, .login, .refreshToken, .logout,
             .createExerciseSession, .createDietLog, .initiateMealPhotoAnalysis,
             .analyzeMealPhoto, .confirmMealPhotoAnalysis, .importExternalFood,
             .createBodyMeasurement, .initiatePhotoUpload, .registerProgressPhoto, .createGoal:
            return .POST
        case .updateProfile, .updateGoal:
            return .PATCH
        case .deleteAccount, .deleteExerciseSession, .deleteDietLog,
             .deleteGoal, .deleteBodyMeasurement:
            return .DELETE
        default:
            return .GET
        }
    }

    var body: Data? {
        switch self {
        case .register(let b), .login(let b), .refreshToken(let b),
             .updateProfile(let b),
             .createExerciseSession(let b),
             .createDietLog(let b), .initiateMealPhotoAnalysis(let b),
             .analyzeMealPhoto(_, let b), .confirmMealPhotoAnalysis(_, let b),
             .importExternalFood(let b),
             .createBodyMeasurement(let b), .initiatePhotoUpload(let b), .registerProgressPhoto(let b),
             .createGoal(let b), .updateGoal(_, let b):
            return b
        default:
            return nil
        }
    }

    var queryItems: [URLQueryItem]? {
        switch self {
        case .getExerciseSessions(let from, let to, let page, let size):
            var items: [URLQueryItem] = [
                .init(name: "page", value: "\(page)"),
                .init(name: "size", value: "\(size)")
            ]
            if let from { items.append(.init(name: "from", value: from)) }
            if let to   { items.append(.init(name: "to",   value: to))   }
            return items
        case .getExerciseCatalog(let q):
            return q.map { [.init(name: "query", value: $0)] }
        case .getFoodCatalog(let q):
            return q.map { [.init(name: "query", value: $0)] }
        case .getDietLogs(let from, let to, let page, let size):
            var items: [URLQueryItem] = [
                .init(name: "page", value: "\(page)"),
                .init(name: "size", value: "\(size)")
            ]
            if let from { items.append(.init(name: "from", value: from)) }
            if let to   { items.append(.init(name: "to",   value: to))   }
            return items
        case .searchExternalFoods(let q, let source, let page, let size):
            return [
                .init(name: "q",      value: q),
                .init(name: "source", value: source),
                .init(name: "page",   value: "\(page)"),
                .init(name: "size",   value: "\(size)")
            ]
        case .getBodyMeasurements(let page, let size):
            return [
                .init(name: "page", value: "\(page)"),
                .init(name: "size", value: "\(size)")
            ]
        case .getBodyMeasurementAtOrBefore(let date):
            return [.init(name: "date", value: date)]
        case .getProgressPhotos(let photoType, let page, let size):
            var items: [URLQueryItem] = [
                .init(name: "page", value: "\(page)"),
                .init(name: "size", value: "\(size)")
            ]
            if let t = photoType { items.append(.init(name: "photoType", value: t)) }
            return items
        default: return nil
        }
    }

    var requiresAuth: Bool {
        switch self {
        case .register, .login, .refreshToken: return false
        default: return true
        }
    }
}
