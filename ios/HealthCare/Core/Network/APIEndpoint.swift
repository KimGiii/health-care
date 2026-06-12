import Foundation

enum HTTPMethod: String {
    case GET, POST, PUT, PATCH, DELETE
}

/// 소셜로그인 제공자. raw value 는 백엔드 path variable 로 사용된다 (`/api/v1/auth/social-login/{provider}`).
enum SocialAuthProvider: String, Sendable {
    case apple = "APPLE"
    case google = "GOOGLE"
}

enum APIEndpoint {
    // Auth
    case register(body: Data)
    case login(body: Data)
    case socialLogin(provider: SocialAuthProvider, body: Data)
    case socialLoginCheck(provider: SocialAuthProvider, body: Data)
    case socialLoginCommit(provider: SocialAuthProvider, body: Data)
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
    case getExerciseCatalog(query: String?, muscleGroup: String?)
    case createCustomExercise(body: Data)

    // Diet - Logs
    case createDietLog(body: Data)
    case updateDietLog(id: Int, body: Data)
    case getDietLogs(from: String?, to: String?, page: Int, size: Int)
    case getDietLog(id: Int)
    case deleteDietLog(id: Int)
    case initiateMealPhotoAnalysis(body: Data)
    case analyzeMealPhoto(id: Int, body: Data)
    case getMealPhotoAnalysis(id: Int)
    case confirmMealPhotoAnalysis(id: Int, body: Data)
    // Diet - Catalog
    case getFoodCatalog(query: String?)
    case createCustomFood(body: Data)

    // Body Measurement
    case createBodyMeasurement(body: Data)
    case getBodyMeasurements(page: Int, size: Int)
    case getBodyMeasurementsRange(from: String, to: String)
    case getLatestBodyMeasurement
    case getBodyMeasurementAtOrBefore(date: String)
    case getBodyMeasurement(id: Int)
    case deleteBodyMeasurement(id: Int)
    case initiatePhotoUpload(body: Data)
    case registerProgressPhoto(body: Data)
    case getProgressPhotos(photoType: String?, page: Int, size: Int)
    case deleteProgressPhoto(id: Int)

    // Goal
    case createGoal(body: Data)
    case getGoals
    case getGoal(id: Int)
    case updateGoal(id: Int, body: Data)
    case deleteGoal(id: Int)
    case getGoalProgress(id: Int)

    // AI Estimation
    case aiEstimateFood(body: Data)
    case aiEstimateExercise(body: Data)

    // Insights
    case getWeeklySummary(weekOffset: Int)
    case getChangeAnalysis(from: String, to: String)

    // Notifications (인앱 알림 센터)
    case getNotifications(page: Int, size: Int)
    case getNotificationsUnreadCount
    case markNotificationRead(id: Int)
    case markAllNotificationsRead
    case deleteNotification(id: Int)
}

extension APIEndpoint {
    var path: String {
        switch self {
        case .register:                          return "/api/v1/auth/register"
        case .login:                             return "/api/v1/auth/login"
        case .socialLogin(let provider, _):      return "/api/v1/auth/social-login/\(provider.rawValue)"
        case .socialLoginCheck(let provider, _): return "/api/v1/auth/social-login/\(provider.rawValue)/check"
        case .socialLoginCommit(let provider, _):return "/api/v1/auth/social-login/\(provider.rawValue)/commit"
        case .refreshToken:                      return "/api/v1/auth/token/refresh"
        case .logout:                            return "/api/v1/auth/logout"
        case .getProfile, .updateProfile, .deleteAccount:
                                                 return "/api/v1/users/me"
        case .createExerciseSession, .getExerciseSessions:
                                                 return "/api/v1/exercise/sessions"
        case .getExerciseSession(let id),
             .deleteExerciseSession(let id):     return "/api/v1/exercise/sessions/\(id)"
        case .getExerciseCatalog, .createCustomExercise: return "/api/v1/exercise/catalog"
        case .createDietLog, .getDietLogs:       return "/api/v1/diet/logs"
        case .getDietLog(let id),
             .updateDietLog(let id, _),
             .deleteDietLog(let id):             return "/api/v1/diet/logs/\(id)"
        case .initiateMealPhotoAnalysis:         return "/api/v1/diet/photo-analyses/initiate"
        case .analyzeMealPhoto(let id, _):       return "/api/v1/diet/photo-analyses/\(id)/analyze"
        case .getMealPhotoAnalysis(let id):      return "/api/v1/diet/photo-analyses/\(id)"
        case .confirmMealPhotoAnalysis(let id, _):
                                                 return "/api/v1/diet/photo-analyses/\(id)/confirm"
        case .getFoodCatalog, .createCustomFood: return "/api/v1/diet/catalog"
        case .createBodyMeasurement, .getBodyMeasurements:
                                                 return "/api/v1/body-measurements"
        case .getBodyMeasurementsRange:          return "/api/v1/body-measurements/range"
        case .getLatestBodyMeasurement:          return "/api/v1/body-measurements/latest"
        case .getBodyMeasurementAtOrBefore:      return "/api/v1/body-measurements/at-or-before"
        case .getBodyMeasurement(let id),
             .deleteBodyMeasurement(let id):     return "/api/v1/body-measurements/\(id)"
        case .initiatePhotoUpload:               return "/api/v1/body-measurements/photos/upload-url"
        case .registerProgressPhoto, .getProgressPhotos:
                                                 return "/api/v1/body-measurements/photos"
        case .deleteProgressPhoto(let id):       return "/api/v1/body-measurements/photos/\(id)"
        case .createGoal, .getGoals:             return "/api/v1/goals"
        case .getGoal(let id),
             .updateGoal(let id, _),
             .deleteGoal(let id):                return "/api/v1/goals/\(id)"
        case .getGoalProgress(let id):           return "/api/v1/goals/\(id)/progress"
        case .aiEstimateFood:                    return "/api/v1/diet/ai-estimate"
        case .aiEstimateExercise:                return "/api/v1/exercise/ai-estimate"
        case .getWeeklySummary:                  return "/api/v1/insights/weekly-summary"
        case .getChangeAnalysis:                 return "/api/v1/insights/change-analysis"
        case .getNotifications:                  return "/api/v1/notifications"
        case .getNotificationsUnreadCount:       return "/api/v1/notifications/unread-count"
        case .markNotificationRead(let id):      return "/api/v1/notifications/\(id)/read"
        case .markAllNotificationsRead:          return "/api/v1/notifications/read-all"
        case .deleteNotification(let id):        return "/api/v1/notifications/\(id)"
        }
    }

    var method: HTTPMethod {
        switch self {
        case .register, .login, .socialLogin, .socialLoginCheck, .socialLoginCommit, .refreshToken, .logout,
             .createExerciseSession, .createDietLog, .initiateMealPhotoAnalysis,
             .analyzeMealPhoto, .confirmMealPhotoAnalysis,
             .createBodyMeasurement, .initiatePhotoUpload, .registerProgressPhoto, .createGoal,
             .aiEstimateFood, .aiEstimateExercise, .createCustomFood, .createCustomExercise:
            return .POST
        case .updateProfile, .updateGoal,
             .markNotificationRead, .markAllNotificationsRead:
            return .PATCH
        case .updateDietLog:
            return .PUT
        case .deleteAccount, .deleteExerciseSession, .deleteDietLog,
             .deleteGoal, .deleteBodyMeasurement, .deleteProgressPhoto,
             .deleteNotification:
            return .DELETE
        default:
            return .GET
        }
    }

    var body: Data? {
        switch self {
        case .register(let b), .login(let b), .socialLogin(_, let b),
             .socialLoginCheck(_, let b), .socialLoginCommit(_, let b), .refreshToken(let b),
             .updateProfile(let b),
             .createExerciseSession(let b),
             .createDietLog(let b), .updateDietLog(_, let b), .initiateMealPhotoAnalysis(let b),
             .analyzeMealPhoto(_, let b), .confirmMealPhotoAnalysis(_, let b),
             .createBodyMeasurement(let b), .initiatePhotoUpload(let b), .registerProgressPhoto(let b),
             .createGoal(let b), .updateGoal(_, let b),
             .aiEstimateFood(let b), .aiEstimateExercise(let b),
             .createCustomFood(let b), .createCustomExercise(let b):
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
        case .getExerciseCatalog(let q, let mg):
            var items: [URLQueryItem] = []
            if let q  { items.append(.init(name: "query",       value: q))  }
            if let mg { items.append(.init(name: "muscleGroup",  value: mg)) }
            return items.isEmpty ? nil : items
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
        case .getBodyMeasurements(let page, let size):
            return [
                .init(name: "page", value: "\(page)"),
                .init(name: "size", value: "\(size)")
            ]
        case .getBodyMeasurementsRange(let from, let to):
            return [
                .init(name: "from", value: from),
                .init(name: "to", value: to)
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
        case .getWeeklySummary(let weekOffset):
            return [.init(name: "weekOffset", value: "\(weekOffset)")]
        case .getChangeAnalysis(let from, let to):
            return [.init(name: "from", value: from), .init(name: "to", value: to)]
        case .getNotifications(let page, let size):
            return [
                .init(name: "page", value: "\(page)"),
                .init(name: "size", value: "\(size)")
            ]
        default: return nil
        }
    }

    var requiresAuth: Bool {
        switch self {
        case .register, .login, .socialLogin, .socialLoginCheck, .socialLoginCommit, .refreshToken: return false
        default: return true
        }
    }
}
