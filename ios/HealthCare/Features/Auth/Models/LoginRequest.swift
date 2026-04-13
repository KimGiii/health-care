import Foundation

struct LoginRequest: Encodable {
    let email: String
    let password: String
}

struct TokenResponse: Decodable {
    let accessToken: String
    let refreshToken: String
    let tokenType: String
}
