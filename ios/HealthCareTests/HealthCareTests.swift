import XCTest
@testable import HealthCare

final class HealthCareTests: XCTestCase {

    func testTokenStoreWriteRead() throws {
        let store = TokenStore()
        store.save(accessToken: "test-access", refreshToken: "test-refresh")
        XCTAssertEqual(store.accessToken, "test-access")
        XCTAssertEqual(store.refreshToken, "test-refresh")
        store.clearTokens()
        XCTAssertNil(store.accessToken)
        XCTAssertNil(store.refreshToken)
    }

    func testAuthStatusDefaultsToUnauthenticated() async throws {
        let tokenStore = TokenStore()
        tokenStore.clearTokens()
        let authState = await AuthState(tokenStore: tokenStore)
        let status = await authState.status
        XCTAssertEqual(status, .unauthenticated)
    }
}
