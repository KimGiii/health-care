import Foundation
import UIKit

@MainActor
final class ProgressPhotoViewModel: ObservableObject {
    @Published var photosByType: [PhotoType: [ProgressPhotoItem]] = [:]
    @Published var selectedType: PhotoType = .FRONT
    @Published var isLoading = false
    @Published var isUploading = false
    @Published var uploadProgress: Double = 0
    @Published var errorMessage: String?

    var photosForSelectedType: [ProgressPhotoItem] {
        photosByType[selectedType] ?? []
    }

    // MARK: - Load

    func loadAll(apiClient: APIClient) async {
        isLoading = true
        defer { isLoading = false }
        do {
            let response: ProgressPhotoListResponse = try await apiClient.request(
                .getProgressPhotos(photoType: nil, page: 0, size: 100)
            )
            var grouped: [PhotoType: [ProgressPhotoItem]] = [:]
            for photo in response.content {
                grouped[photo.photoType, default: []].append(photo)
            }
            photosByType = grouped
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = "사진을 불러오지 못했습니다."
        }
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
            errorMessage = "이미지를 처리할 수 없습니다."
            return
        }

        isUploading = true
        uploadProgress = 0
        defer { isUploading = false }

        do {
            // Step 1: pre-signed URL 발급
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

        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = "업로드 중 오류가 발생했습니다."
        }
    }
}
