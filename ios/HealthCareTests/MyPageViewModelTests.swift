import XCTest
@testable import Gainsy

@MainActor
final class MyPageViewModelTests: XCTestCase {

    func testLoad_성공시프로필이채워진다() async {
        let profile = makeProfile(displayName: "홍길동")
        let apiClient = MockMyPageProfileManager(profile: profile)
        let vm = MyPageViewModel()

        await vm.load(apiClient: apiClient)

        XCTAssertEqual(vm.profile?.displayName, "홍길동")
        XCTAssertNil(vm.errorMessage)
        XCTAssertFalse(vm.isLoading)
    }

    func testLoad_APIError발생시에러메시지가설정된다() async {
        let apiClient = MockMyPageProfileManager(loadError: APIError.unauthorized)
        let vm = MyPageViewModel()

        await vm.load(apiClient: apiClient)

        XCTAssertNil(vm.profile)
        XCTAssertEqual(vm.errorMessage, "인증에 실패했습니다.")
        XCTAssertFalse(vm.isLoading)
    }

    func testLabel_성별과활동수준을표시용문구로변환한다() async {
        let vm = MyPageViewModel()
        let apiClient = MockMyPageProfileManager(
            profile: makeProfile(sex: "FEMALE", activityLevel: "MODERATELY_ACTIVE")
        )

        await vm.load(apiClient: apiClient)

        XCTAssertEqual(vm.sexLabel, "여성")
        XCTAssertEqual(vm.activityLevelLabel, "보통 활동")
    }

    func testPopulateEditFields_프로필값을편집필드에복사한다() async {
        let vm = MyPageViewModel()
        let apiClient = MockMyPageProfileManager(
            profile: makeProfile(
                displayName: "테스터",
                sex: "MALE",
                heightCm: 178.5,
                weightKg: 72.3,
                activityLevel: "VERY_ACTIVE"
            )
        )
        await vm.load(apiClient: apiClient)

        vm.populateEditFields()

        XCTAssertEqual(vm.editDisplayName, "테스터")
        XCTAssertEqual(vm.editSex, "MALE")
        XCTAssertEqual(vm.editHeightCm, "178.5")
        XCTAssertEqual(vm.editWeightKg, "72.3")
        XCTAssertEqual(vm.editActivityLevel, "VERY_ACTIVE")
    }

    func testSaveProfile_입력값을요청으로변환하고업데이트된프로필을반영한다() async {
        let apiClient = MockMyPageProfileManager(
            profile: makeProfile(displayName: "이전"),
            updateProfile: makeProfile(displayName: "새이름", heightCm: 180, weightKg: 75)
        )
        let vm = MyPageViewModel()
        vm.editDisplayName = "새이름"
        vm.editSex = "MALE"
        vm.editHeightCm = "180"
        vm.editWeightKg = "75"
        vm.editActivityLevel = "LIGHTLY_ACTIVE"

        await vm.saveProfile(apiClient: apiClient)

        XCTAssertEqual(vm.profile?.displayName, "새이름")
        let request = await apiClient.lastUpdateRequest
        XCTAssertEqual(request?.displayName, "새이름")
        XCTAssertEqual(request?.sex, "MALE")
        XCTAssertEqual(request?.heightCm, 180)
        XCTAssertEqual(request?.weightKg, 75)
        XCTAssertEqual(request?.activityLevel, "LIGHTLY_ACTIVE")
        XCTAssertNil(vm.errorMessage)
    }

    func testSaveProfile_빈문자열은nil요청으로보낸다() async {
        let apiClient = MockMyPageProfileManager(updateProfile: makeProfile())
        let vm = MyPageViewModel()

        await vm.saveProfile(apiClient: apiClient)

        let request = await apiClient.lastUpdateRequest
        XCTAssertNil(request?.displayName)
        XCTAssertNil(request?.sex)
        XCTAssertNil(request?.heightCm)
        XCTAssertNil(request?.weightKg)
        XCTAssertNil(request?.activityLevel)
    }

    func testDeleteAccount_성공시인증상태를초기화한다() async {
        let tokenStore = TokenStore()
        tokenStore.save(accessToken: "access", refreshToken: "refresh")
        let authState = AuthState(tokenStore: tokenStore)
        let apiClient = MockMyPageProfileManager()
        let vm = MyPageViewModel()

        await vm.deleteAccount(apiClient: apiClient, authState: authState)

        XCTAssertEqual(authState.status, .unauthenticated)
        let deleteCalled = await apiClient.deleteCalled
        XCTAssertTrue(deleteCalled)
        XCTAssertNil(vm.errorMessage)
    }

    func testDeleteAccount_실패시인증상태를유지하고에러를표시한다() async {
        let tokenStore = TokenStore()
        tokenStore.save(accessToken: "access", refreshToken: "refresh")
        let authState = AuthState(tokenStore: tokenStore)
        let apiClient = MockMyPageProfileManager(deleteError: APIError.unknown)
        let vm = MyPageViewModel()

        await vm.deleteAccount(apiClient: apiClient, authState: authState)

        XCTAssertEqual(authState.status, .authenticated)
        XCTAssertEqual(vm.errorMessage, "알 수 없는 오류가 발생했습니다.")
    }

    private func makeProfile(
        id: Int = 1,
        email: String = "user@example.com",
        displayName: String = "사용자",
        sex: String? = nil,
        heightCm: Double? = nil,
        weightKg: Double? = nil,
        activityLevel: String? = nil,
        onboardingCompleted: Bool = true
    ) -> UserProfile {
        UserProfile(
            id: id,
            email: email,
            displayName: displayName,
            sex: sex,
            dateOfBirth: nil,
            heightCm: heightCm,
            weightKg: weightKg,
            activityLevel: activityLevel,
            onboardingCompleted: onboardingCompleted,
            isPremium: nil,
            calorieTarget: nil,
            proteinTargetG: nil,
            carbTargetG: nil,
            fatTargetG: nil
        )
    }
}

private actor MockMyPageProfileManager: MyPageProfileManaging {
    private let profile: UserProfile
    private let updatedProfile: UserProfile
    private let loadError: Error?
    private let updateError: Error?
    private let deleteError: Error?

    private(set) var lastUpdateRequest: UpdateProfileRequest?
    private(set) var deleteCalled = false

    init(
        profile: UserProfile = UserProfile(
            id: 1,
            email: "user@example.com",
            displayName: "사용자",
            sex: nil,
            dateOfBirth: nil,
            heightCm: nil,
            weightKg: nil,
            activityLevel: nil,
            onboardingCompleted: true,
            isPremium: nil,
            calorieTarget: nil,
            proteinTargetG: nil,
            carbTargetG: nil,
            fatTargetG: nil
        ),
        updateProfile: UserProfile? = nil,
        loadError: Error? = nil,
        updateError: Error? = nil,
        deleteError: Error? = nil
    ) {
        self.profile = profile
        self.updatedProfile = updateProfile ?? profile
        self.loadError = loadError
        self.updateError = updateError
        self.deleteError = deleteError
    }

    func loadProfile() async throws -> UserProfile {
        if let loadError { throw loadError }
        return profile
    }

    func updateProfile(_ request: UpdateProfileRequest) async throws -> UserProfile {
        if let updateError { throw updateError }
        lastUpdateRequest = request
        return updatedProfile
    }

    func deleteAccount() async throws {
        if let deleteError { throw deleteError }
        deleteCalled = true
    }
}
