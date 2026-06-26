import XCTest

// Core flow UI tests: Record Hub navigation, Add form sheets opening.
// All tests run against a mock-authenticated session (no real API calls).

@MainActor
final class CoreFlowUITests: XCTestCase {
    let app = XCUIApplication()

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    // MARK: - Record Hub

    func testRecordHubShowsAllCards() throws {
        launchAuthenticated()

        app.tabBars.buttons["기록"].tap()

        XCTAssertTrue(app.staticTexts["오늘의 운동"].waitForExistence(timeout: 6))
        XCTAssertTrue(app.staticTexts["오늘 먹은 것"].exists)
        XCTAssertTrue(app.staticTexts["신체 변화"].exists)
        XCTAssertTrue(app.staticTexts["진행 사진"].exists)
    }

    // MARK: - Exercise Flow

    func testExerciseRecordViewOpensFromHub() throws {
        launchAuthenticated()
        navigateToHub()

        app.staticTexts["오늘의 운동"].tap()

        XCTAssertTrue(app.staticTexts["운동 기록"].waitForExistence(timeout: 5))
    }

    func testAddExerciseSessionSheetOpens() throws {
        launchAuthenticated()
        navigateToHub()

        app.staticTexts["오늘의 운동"].tap()
        XCTAssertTrue(app.staticTexts["운동 기록"].waitForExistence(timeout: 5))

        tapAddButton(
            primaryLabel: "운동 기록 시작",
            fallbackLabel: "Add"
        )

        XCTAssertTrue(app.navigationBars["운동 기록 추가"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["운동 추가하기"].exists)
        XCTAssertTrue(app.buttons["취소"].exists)
    }

    func testAddExerciseFormSaveButtonDisabledWhenEmpty() throws {
        launchAuthenticated()
        navigateToHub()

        app.staticTexts["오늘의 운동"].tap()
        XCTAssertTrue(app.staticTexts["운동 기록"].waitForExistence(timeout: 5))

        tapAddButton(primaryLabel: "운동 기록 시작", fallbackLabel: "Add")

        XCTAssertTrue(app.navigationBars["운동 기록 추가"].waitForExistence(timeout: 5))

        let saveButton = app.buttons["기록 저장"]
        XCTAssertTrue(saveButton.waitForExistence(timeout: 3))
        XCTAssertFalse(saveButton.isEnabled)
    }

    // MARK: - Diet Flow

    func testDietRecordViewOpensFromHub() throws {
        launchAuthenticated()
        navigateToHub()

        app.staticTexts["오늘 먹은 것"].tap()

        XCTAssertTrue(app.staticTexts["식단 기록"].waitForExistence(timeout: 5))
    }

    func testAddDietLogSheetOpens() throws {
        launchAuthenticated()
        navigateToHub()

        app.staticTexts["오늘 먹은 것"].tap()
        XCTAssertTrue(app.staticTexts["식단 기록"].waitForExistence(timeout: 5))

        tapAddButton(primaryLabel: "식단 기록하기", fallbackLabel: "Add")

        // Sheet presents AddDietLogView ("식단 기록" title in its own NavigationStack)
        XCTAssertTrue(app.buttons["식품 추가"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["취소"].exists)
    }

    func testAddDietFormSaveButtonDisabledWhenEmpty() throws {
        launchAuthenticated()
        navigateToHub()

        app.staticTexts["오늘 먹은 것"].tap()
        XCTAssertTrue(app.staticTexts["식단 기록"].waitForExistence(timeout: 5))

        tapAddButton(primaryLabel: "식단 기록하기", fallbackLabel: "Add")

        XCTAssertTrue(app.buttons["식품 추가"].waitForExistence(timeout: 5))

        let saveButton = app.buttons["저장"]
        XCTAssertTrue(saveButton.waitForExistence(timeout: 3))
        XCTAssertFalse(saveButton.isEnabled)
    }

    // MARK: - Body Measurement Flow

    func testBodyMeasurementViewOpensFromHub() throws {
        launchAuthenticated()
        navigateToHub()

        app.staticTexts["신체 변화"].tap()

        XCTAssertTrue(app.staticTexts["측정 기록이 아직 없어요"].waitForExistence(timeout: 5))
    }

    func testAddBodyMeasurementSheetOpens() throws {
        launchAuthenticated()
        navigateToHub()

        app.staticTexts["신체 변화"].tap()
        XCTAssertTrue(app.staticTexts["측정 기록이 아직 없어요"].waitForExistence(timeout: 5))

        tapAddButton(primaryLabel: "신체 측정 기록", fallbackLabel: "Add")

        XCTAssertTrue(app.navigationBars["신체 측정 기록"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["취소"].exists)
    }

    func testAddMeasurementFormSaveButtonDisabledWhenEmpty() throws {
        launchAuthenticated()
        navigateToHub()

        app.staticTexts["신체 변화"].tap()
        XCTAssertTrue(app.staticTexts["측정 기록이 아직 없어요"].waitForExistence(timeout: 5))

        tapAddButton(primaryLabel: "신체 측정 기록", fallbackLabel: "Add")

        XCTAssertTrue(app.navigationBars["신체 측정 기록"].waitForExistence(timeout: 5))

        let saveButton = app.buttons["저장"]
        XCTAssertTrue(saveButton.waitForExistence(timeout: 3))
        XCTAssertFalse(saveButton.isEnabled)
    }

    // MARK: - Helpers

    private func launchAuthenticated() {
        app.launchArguments = ["UI_TEST_RESET_STATE", "UI_TEST_AUTHENTICATED", "UI_TEST_STUB_NETWORK"]
        app.launch()
        XCTAssertTrue(app.tabBars.buttons["기록"].waitForExistence(timeout: 8))
    }

    private func navigateToHub() {
        app.tabBars.buttons["기록"].tap()
        XCTAssertTrue(app.staticTexts["오늘의 운동"].waitForExistence(timeout: 6))
    }

    /// Taps `primaryLabel` button if it exists; otherwise taps `fallbackLabel`.
    private func tapAddButton(primaryLabel: String, fallbackLabel: String) {
        let primary = app.buttons[primaryLabel]
        if primary.waitForExistence(timeout: 3) {
            primary.tap()
        } else {
            let fallback = app.buttons[fallbackLabel]
            XCTAssertTrue(fallback.waitForExistence(timeout: 3))
            fallback.tap()
        }
    }
}
