import Foundation

enum Constants {
    enum API {
        static let defaultBaseURL      = "https://api.healthcare.app"
        static let timeoutInterval: TimeInterval = 30
    }

    enum Keychain {
        static let accessTokenKey  = "com.healthcare.ios.accessToken"
        static let refreshTokenKey = "com.healthcare.ios.refreshToken"
    }

    enum Notification {
        static let fcmTokenRefreshed = "fcmTokenRefreshed"
    }

    enum DateFormat {
        static let apiDate     = "yyyy-MM-dd"
        static let displayDate = "M월 d일"
        static let displayFull = "yyyy년 M월 d일 EEEE"
    }
}
