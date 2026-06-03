import Foundation
import UIKit

protocol ProgressPhotoAPIClient: Sendable {
    func loadPhotos() async throws -> ProgressPhotoListResponse
    func deletePhoto(id: Int) async throws
}

extension APIClient: ProgressPhotoAPIClient {
    func loadPhotos() async throws -> ProgressPhotoListResponse {
        try await request(.getProgressPhotos(photoType: nil, page: 0, size: 100))
    }
    func deletePhoto(id: Int) async throws {
        try await requestVoid(.deleteProgressPhoto(id: id))
    }
}

@MainActor
final class ProgressPhotoViewModel: ObservableObject {
    @Published var photosByType: [PhotoType: [ProgressPhotoItem]] = [:]
    @Published var selectedType: PhotoType = .FRONT
    @Published var isLoading = false
    @Published var isUploading = false
    @Published var uploadProgress: Double = 0
    @Published var errorMessage: String?

    // MARK: - Upload Failure / Retry

    @Published var uploadFailed = false

    var uploadFailureMessage: String {
        failedStep?.message ?? String(localized: "body.photo.error.upload")
    }

    private var failedStep: UploadStep?
    private var pendingUpload: PendingUpload?

    private enum UploadStep {
        case initiating, transferring, registering

        var message: String {
            switch self {
            case .initiating:
                return String(localized: "body.photo.error.network")
            case .transferring:
                return String(localized: "body.photo.error.transfer")
            case .registering:
                return String(localized: "body.photo.error.register")
            }
        }
    }

    private struct PendingUpload {
        let image: UIImage
        let photoType: PhotoType
        let bodyWeightKg: Double?
        let waistCm: Double?
        let notes: String
        let isBaseline: Bool
    }

    func retryUpload(apiClient: APIClient) async {
        guard let pending = pendingUpload else { return }
        await upload(
            image: pending.image,
            photoType: pending.photoType,
            bodyWeightKg: pending.bodyWeightKg,
            waistCm: pending.waistCm,
            notes: pending.notes,
            isBaseline: pending.isBaseline,
            apiClient: apiClient
        )
    }

    // MARK: - 비교 모드
    @Published var isCompareMode = false
    @Published var compareSelection: [ProgressPhotoItem] = []

    var photosForSelectedType: [ProgressPhotoItem] {
        photosByType[selectedType] ?? []
    }

    // MARK: - Load

    func loadAll(apiClient: any ProgressPhotoAPIClient) async {
        isLoading = true
        defer { isLoading = false }
        do {
            let response = try await apiClient.loadPhotos()
            var grouped: [PhotoType: [ProgressPhotoItem]] = [:]
            for photo in response.content {
                grouped[photo.photoType, default: []].append(photo)
            }
            photosByType = grouped
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = String(localized: "body.photo.error.load")
        }
    }

    // MARK: - Delete

    func deletePhoto(photoId: Int, apiClient: any ProgressPhotoAPIClient) async {
        do {
            try await apiClient.deletePhoto(id: photoId)
            for type in photosByType.keys {
                photosByType[type]?.removeAll { $0.photoId == photoId }
            }
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = String(localized: "body.photo.error.delete")
        }
    }

    // MARK: - Compare Mode

    func toggleCompareMode() {
        isCompareMode.toggle()
        compareSelection.removeAll()
    }

    func toggleCompareSelection(_ photo: ProgressPhotoItem) {
        if let idx = compareSelection.firstIndex(where: { $0.photoId == photo.photoId }) {
            compareSelection.remove(at: idx)
        } else if compareSelection.count < 2 {
            compareSelection.append(photo)
        }
    }

    func isSelectedForCompare(_ photo: ProgressPhotoItem) -> Bool {
        compareSelection.contains { $0.photoId == photo.photoId }
    }

    // MARK: - Upload Flow

    /// 1) 업로드 URL 발급 → 2) S3 PUT → 3) 메타데이터 등록
    func upload(
        image: UIImage,
        photoType: PhotoType,
        bodyWeightKg: Double?,
        waistCm: Double?,
        notes: String,
        isBaseline: Bool,
        apiClient: APIClient
    ) async {
        guard let imageData = image.jpegData(compressionQuality: 0.85) else {
            errorMessage = String(localized: "body.photo.error.processImage")
            return
        }

        uploadFailed = false
        failedStep = nil
        pendingUpload = PendingUpload(
            image: image,
            photoType: photoType,
            bodyWeightKg: bodyWeightKg,
            waistCm: waistCm,
            notes: notes,
            isBaseline: isBaseline
        )

        isUploading = true
        uploadProgress = 0
        defer { isUploading = false }

        var currentStep: UploadStep = .initiating
        do {
            // Step 1: pre-signed URL 발급
            currentStep = .initiating
            let fileName = "progress_\(Int(Date().timeIntervalSince1970)).jpg"
            let initiateReq = InitiatePhotoUploadRequest(
                fileName: fileName,
                contentType: "image/jpeg",
                fileSizeBytes: imageData.count
            )
            let initiateBody = try apiClient.encode(initiateReq)
            let uploadInfo: InitiatePhotoUploadResponse = try await apiClient.request(
                .initiatePhotoUpload(body: initiateBody)
            )
            uploadProgress = 0.3

            // Step 2: S3 직접 PUT
            currentStep = .transferring
            guard let uploadURL = URL(string: uploadInfo.uploadUrl) else {
                throw URLError(.badURL)
            }
            var s3Request = URLRequest(url: uploadURL)
            s3Request.httpMethod = "PUT"
            s3Request.setValue("image/jpeg", forHTTPHeaderField: "Content-Type")
            s3Request.httpBody = imageData
            let (_, s3Response) = try await URLSession.shared.data(for: s3Request)
            guard let http = s3Response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
                throw URLError(.badServerResponse)
            }
            uploadProgress = 0.75

            // Step 3: 메타데이터 등록
            currentStep = .registering
            let iso = ISO8601DateFormatter()
            iso.formatOptions = [.withInternetDateTime]
            let capturedAt = iso.string(from: Date())

            let registerReq = RegisterProgressPhotoRequest(
                storageKey: uploadInfo.storageKey,
                contentType: "image/jpeg",
                capturedAt: capturedAt,
                photoType: photoType.rawValue,
                bodyWeightKg: bodyWeightKg,
                bodyFatPct: nil,
                waistCm: waistCm,
                notes: notes.isEmpty ? nil : notes,
                isBaseline: isBaseline,
                fileSizeBytes: imageData.count
            )
            let registerBody = try apiClient.encode(registerReq)
            let newPhoto: ProgressPhotoItem = try await apiClient.request(
                .registerProgressPhoto(body: registerBody)
            )
            uploadProgress = 1.0

            // 갤러리에 즉시 반영
            photosByType[newPhoto.photoType, default: []].insert(newPhoto, at: 0)
            selectedType = newPhoto.photoType
            pendingUpload = nil

        } catch {
            failedStep = currentStep
            uploadFailed = true
        }
    }
}
