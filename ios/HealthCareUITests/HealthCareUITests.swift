import XCTest

@MainActor
final class HealthCareUITests: XCTestCase {
    let app = XCUIApplication()

    override func setUpWithError() throws {
        continueAfterFailure = false
        app.launch()
    }

    func testOnboardingAppears() throws {
        XCTAssertTrue(app.staticTexts["HealthCare"].waitForExistence(timeout: 5))
    }
}
