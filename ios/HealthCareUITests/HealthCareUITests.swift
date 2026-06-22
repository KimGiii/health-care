import XCTest

@MainActor
final class HealthCareUITests: XCTestCase {
    let app = XCUIApplication()

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testOnboardingAppears() throws {
        launchApp()
        XCTAssertTrue(app.staticTexts["Gainsy"].waitForExistence(timeout: 8))
        XCTAssertTrue(app.buttons["로그인하기"].exists)
    }

    func testLoginScreenAppears() throws {
        launchApp(arguments: ["UI_TEST_LOGIN_SCREEN"])
        XCTAssertTrue(app.staticTexts["다시 만나서 반가워요"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.textFields["이메일"].exists)
        XCTAssertTrue(app.secureTextFields["비밀번호"].exists)
    }

    func testMainTabsAppearWhenAuthenticated() throws {
        launchApp(arguments: ["UI_TEST_AUTHENTICATED", "UI_TEST_STUB_NETWORK"])

        XCTAssertTrue(app.tabBars.buttons["대시보드"].waitForExistence(timeout: 8))
        XCTAssertTrue(app.tabBars.buttons["다이어리"].exists)
        XCTAssertTrue(app.tabBars.buttons["기록"].exists)
        XCTAssertTrue(app.tabBars.buttons["탐색"].exists)
        XCTAssertTrue(app.tabBars.buttons["마이"].exists)
    }

    private func launchApp(arguments: [String] = []) {
        app.launchArguments = ["UI_TEST_RESET_STATE"] + arguments
        app.launch()
    }
}
