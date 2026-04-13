import Foundation

struct ProfileSetupRequest: Encodable {
    let sex: String?
    let heightCm: Double
    let weightKg: Double
    let activityLevel: String?
    let onboardingCompleted: Bool
}

struct UserProfile: Decodable {
    let id: Int
    let email: String
    let displayName: String
    let sex: String?
    let heightCm: Double?
    let weightKg: Double?
    let activityLevel: String?
    let onboardingCompleted: Bool
}
