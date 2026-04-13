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
    case getExerciseSessions
    case getExerciseSession(id: Int)
    case updateExerciseSession(id: Int, body: Data)
    case deleteExerciseSession(id: Int)
    case getDailyExerciseSummary(date: String)
    case getWeeklyExerciseSummary(weekStart: String)
    case getExerciseCatalog(query: String?)

    // Diet
    case createMeal(body: Data)
    case getMeals(date: String)
    case updateMeal(id: Int, body: Data)
    case deleteMeal(id: Int)
    case addMealItem(mealId: Int, body: Data)
    case deleteMealItem(mealId: Int, itemId: Int)
    case searchFood(query: String)
    case getDailyDietSummary(date: String)

    // Measurement
    case logMeasurement(body: Data)
    case getMeasurementHistory
    case uploadProgressPhoto(body: Data)
    case getProgressPhotos

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
             .updateExerciseSession(let id, _),
             .deleteExerciseSession(let id):     return "/api/v1/exercise/sessions/\(id)"
        case .getDailyExerciseSummary:           return "/api/v1/exercise/summary/daily"
        case .getWeeklyExerciseSummary:          return "/api/v1/exercise/summary/weekly"
        case .getExerciseCatalog:                return "/api/v1/exercise/catalog"
        case .createMeal, .getMeals:             return "/api/v1/diet/meals"
        case .updateMeal(let id, _),
             .deleteMeal(let id):                return "/api/v1/diet/meals/\(id)"
        case .addMealItem(let mealId, _):        return "/api/v1/diet/meals/\(mealId)/items"
        case .deleteMealItem(let mealId, let itemId):
                                                 return "/api/v1/diet/meals/\(mealId)/items/\(itemId)"
        case .searchFood:                        return "/api/v1/diet/food/search"
        case .getDailyDietSummary:               return "/api/v1/diet/summary/daily"
        case .logMeasurement, .getMeasurementHistory:
                                                 return "/api/v1/measurements"
        case .uploadProgressPhoto, .getProgressPhotos:
                                                 return "/api/v1/measurements/photos"
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
             .createExerciseSession, .createMeal, .addMealItem,
             .logMeasurement, .uploadProgressPhoto, .createGoal:
            return .POST
        case .updateProfile, .updateExerciseSession, .updateMeal, .updateGoal:
            return .PATCH
        case .deleteAccount, .deleteExerciseSession, .deleteMeal,
             .deleteMealItem, .deleteGoal:
            return .DELETE
        default:
            return .GET
        }
    }

    var body: Data? {
        switch self {
        case .register(let b), .login(let b), .refreshToken(let b),
             .updateProfile(let b),
             .createExerciseSession(let b), .updateExerciseSession(_, let b),
             .createMeal(let b), .updateMeal(_, let b), .addMealItem(_, let b),
             .logMeasurement(let b), .uploadProgressPhoto(let b),
             .createGoal(let b), .updateGoal(_, let b):
            return b
        default:
            return nil
        }
    }

    var queryItems: [URLQueryItem]? {
        switch self {
        case .getDailyExerciseSummary(let date):   return [.init(name: "date", value: date)]
        case .getWeeklyExerciseSummary(let w):     return [.init(name: "weekStart", value: w)]
        case .getExerciseCatalog(let q):           return q.map { [.init(name: "query", value: $0)] }
        case .getMeals(let date):                  return [.init(name: "date", value: date)]
        case .searchFood(let q):                   return [.init(name: "q", value: q)]
        case .getDailyDietSummary(let date):       return [.init(name: "date", value: date)]
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
