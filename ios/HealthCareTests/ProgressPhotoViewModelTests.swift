import XCTest
@testable import Gainsy

@MainActor
final class ProgressPhotoViewModelTests: XCTestCase {

    // MARK: - loadAll

    func testLoadAll_사진을타입별로그룹화한다() async {
        let photos = [
            makePhoto(id: 1, type: .FRONT),
            makePhoto(id: 2, type: .FRONT),
            makePhoto(id: 3, type: .BACK)
        ]
        let loader = MockProgressPhotoAPIClient(photos: photos)
        let vm = ProgressPhotoViewModel()

        await vm.loadAll(apiClient: loader)

        XCTAssertEqual(vm.photosByType[.FRONT]?.count, 2)
        XCTAssertEqual(vm.photosByType[.BACK]?.count, 1)
        XCTAssertFalse(vm.isLoading)
        XCTAssertNil(vm.errorMessage)
    }

    func testLoadAll_오류발생시에러메시지가설정된다() async {
        let loader = MockProgressPhotoAPIClient(error: URLError(.notConnectedToInternet))
        let vm = ProgressPhotoViewModel()

        await vm.loadAll(apiClient: loader)

        XCTAssertTrue(vm.photosByType.isEmpty)
        XCTAssertEqual(vm.errorMessage, "사진을 불러오지 못했습니다.")
    }

    // MARK: - deletePhoto

    func testDeletePhoto_성공시로컬상태에서즉시제거된다() async {
        let photo = makePhoto(id: 10, type: .FRONT)
        let loader = MockProgressPhotoAPIClient(photos: [photo])
        let vm = ProgressPhotoViewModel()
        await vm.loadAll(apiClient: loader)
        XCTAssertEqual(vm.photosByType[.FRONT]?.count, 1)

        await vm.deletePhoto(photoId: 10, apiClient: loader)

        XCTAssertTrue(vm.photosByType[.FRONT]?.isEmpty == true)
        XCTAssertNil(vm.errorMessage)
    }

    func testDeletePhoto_다른타입의사진은영향받지않는다() async {
        let front = makePhoto(id: 1, type: .FRONT)
        let back = makePhoto(id: 2, type: .BACK)
        let loader = MockProgressPhotoAPIClient(photos: [front, back])
        let vm = ProgressPhotoViewModel()
        await vm.loadAll(apiClient: loader)

        await vm.deletePhoto(photoId: 1, apiClient: loader)

        XCTAssertTrue(vm.photosByType[.FRONT]?.isEmpty == true)
        XCTAssertEqual(vm.photosByType[.BACK]?.count, 1)
    }

    func testDeletePhoto_실패시에러메시지가설정된다() async {
        let loader = MockProgressPhotoAPIClient(
            photos: [makePhoto(id: 5, type: .SIDE_LEFT)],
            deleteError: APIError.serverError(statusCode: 403, code: nil, message: nil)
        )
        let vm = ProgressPhotoViewModel()
        await vm.loadAll(apiClient: loader)

        await vm.deletePhoto(photoId: 5, apiClient: loader)

        XCTAssertEqual(vm.photosByType[.SIDE_LEFT]?.count, 1)
        XCTAssertNotNil(vm.errorMessage)
    }

    // MARK: - 비교 모드

    func testToggleCompareMode_켜고끄면선택이초기화된다() async {
        let photos = [makePhoto(id: 1, type: .FRONT), makePhoto(id: 2, type: .FRONT)]
        let loader = MockProgressPhotoAPIClient(photos: photos)
        let vm = ProgressPhotoViewModel()
        await vm.loadAll(apiClient: loader)

        vm.toggleCompareMode()
        vm.toggleCompareSelection(photos[0])
        XCTAssertTrue(vm.isCompareMode)
        XCTAssertEqual(vm.compareSelection.count, 1)

        vm.toggleCompareMode()
        XCTAssertFalse(vm.isCompareMode)
        XCTAssertTrue(vm.compareSelection.isEmpty)
    }

    func testToggleCompareSelection_같은사진을두번누르면선택해제된다() {
        let photo = makePhoto(id: 3, type: .FRONT)
        let vm = ProgressPhotoViewModel()

        vm.toggleCompareSelection(photo)
        XCTAssertEqual(vm.compareSelection.count, 1)

        vm.toggleCompareSelection(photo)
        XCTAssertTrue(vm.compareSelection.isEmpty)
    }

    func testToggleCompareSelection_최대2장까지만선택된다() {
        let p1 = makePhoto(id: 1, type: .FRONT)
        let p2 = makePhoto(id: 2, type: .FRONT)
        let p3 = makePhoto(id: 3, type: .FRONT)
        let vm = ProgressPhotoViewModel()

        vm.toggleCompareSelection(p1)
        vm.toggleCompareSelection(p2)
        vm.toggleCompareSelection(p3)

        XCTAssertEqual(vm.compareSelection.count, 2)
        XCTAssertFalse(vm.compareSelection.contains { $0.photoId == 3 })
    }

    func testIsSelectedForCompare_선택된사진만true를반환한다() {
        let selected = makePhoto(id: 10, type: .BACK)
        let notSelected = makePhoto(id: 20, type: .BACK)
        let vm = ProgressPhotoViewModel()

        vm.toggleCompareSelection(selected)

        XCTAssertTrue(vm.isSelectedForCompare(selected))
        XCTAssertFalse(vm.isSelectedForCompare(notSelected))
    }

    // MARK: - photosForSelectedType

    func testPhotosForSelectedType_selectedType에맞는사진만반환한다() async {
        let front = makePhoto(id: 1, type: .FRONT)
        let back = makePhoto(id: 2, type: .BACK)
        let loader = MockProgressPhotoAPIClient(photos: [front, back])
        let vm = ProgressPhotoViewModel()
        await vm.loadAll(apiClient: loader)

        vm.selectedType = .FRONT
        XCTAssertEqual(vm.photosForSelectedType.map(\.photoId), [1])

        vm.selectedType = .BACK
        XCTAssertEqual(vm.photosForSelectedType.map(\.photoId), [2])
    }

    // MARK: - Helpers

    private func makePhoto(id: Int, type: PhotoType) -> ProgressPhotoItem {
        ProgressPhotoItem(
            photoId: id,
            capturedAt: "2026-01-01T10:00:00Z",
            photoType: type,
            isBaseline: false,
            thumbnailStatus: "READY",
            signedUrls: nil,
            bodyWeightKg: nil,
            bodyFatPct: nil,
            waistCm: nil,
            notes: nil
        )
    }
}

// MARK: - Mock

private actor MockProgressPhotoAPIClient: ProgressPhotoAPIClient {
    private let photos: [ProgressPhotoItem]
    private let loadError: Error?
    private let deleteError: Error?

    init(
        photos: [ProgressPhotoItem] = [],
        error: Error? = nil,
        deleteError: Error? = nil
    ) {
        self.photos = photos
        self.loadError = error
        self.deleteError = deleteError
    }

    func loadPhotos() async throws -> ProgressPhotoListResponse {
        if let loadError { throw loadError }
        return ProgressPhotoListResponse(content: photos, totalElements: photos.count, last: true)
    }

    func deletePhoto(id: Int) async throws {
        if let deleteError { throw deleteError }
    }
}
