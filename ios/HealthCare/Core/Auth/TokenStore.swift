import Foundation
import Security

final class TokenStore: @unchecked Sendable {
    private enum Key {
        static let accessToken  = "com.healthcare.ios.accessToken"
        static let refreshToken = "com.healthcare.ios.refreshToken"
    }

    var accessToken: String?  { read(key: Key.accessToken) }
    var refreshToken: String? { read(key: Key.refreshToken) }

    func save(accessToken: String, refreshToken: String) {
        write(value: accessToken,  key: Key.accessToken)
        write(value: refreshToken, key: Key.refreshToken)
    }

    func clearTokens() {
        delete(key: Key.accessToken)
        delete(key: Key.refreshToken)
    }

    private func write(value: String, key: String) {
        guard let data = value.data(using: .utf8) else { return }
        let query: [CFString: Any] = [
            kSecClass:              kSecClassGenericPassword,
            kSecAttrAccount:        key,
            kSecValueData:          data,
            kSecAttrAccessible:     kSecAttrAccessibleAfterFirstUnlock
        ]
        SecItemDelete(query as CFDictionary)
        SecItemAdd(query as CFDictionary, nil)
    }

    private func read(key: String) -> String? {
        let query: [CFString: Any] = [
            kSecClass:       kSecClassGenericPassword,
            kSecAttrAccount: key,
            kSecReturnData:  true,
            kSecMatchLimit:  kSecMatchLimitOne
        ]
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data,
              let string = String(data: data, encoding: .utf8) else { return nil }
        return string
    }

    private func delete(key: String) {
        let query: [CFString: Any] = [
            kSecClass:       kSecClassGenericPassword,
            kSecAttrAccount: key
        ]
        SecItemDelete(query as CFDictionary)
    }
}
