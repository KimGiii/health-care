import Foundation

// MARK: - Photo Type

enum PhotoType: String, Codable, CaseIterable, Identifiable {
    case FRONT, BACK, SIDE_LEFT, SIDE_RIGHT, DETAIL

    var id: String { rawValue }

    var label: String {
        switch self {
        case .FRONT:      return "정면"
        case .BACK:       return "후면"
        case .SIDE_LEFT:  return "좌측"
        case .SIDE_RIGHT: return "우측"
        case .DETAIL:     return "부위"
        }
    }

    var icon: String {
        switch self {
        case .FRONT:      return "figure.stand"
        case .BACK:       return "figure.stand"
        case .SIDE_LEFT:  return "figure.walk"
        case .SIDE_RIGHT: return "figure.walk"
        case .DETAIL:     return "magnifyingglass"
        }
    }
}

// MARK: - Response Models

struct ProgressPhotoSignedUrls: Decodable {
    let original: String?
    let thumbnail150: String?
    let thumbnail400: String?
    let thumbnail800: String?
}

struct ProgressPhotoItem: Decodable, Identifiable {
    let photoId: Int
    let capturedAt: String
    let photoType: PhotoType
    let isBaseline: Bool
    let thumbnailStatus: String
    let signedUrls: ProgressPhotoSignedUrls?
    let bodyWeightKg: Double?
    let bodyFatPct: Double?
    let waistCm: Double?
    let notes: String?

    var id: Int { photoId }

    var displayDate: String {
        let parts = capturedAt.prefix(10).split(separator: "-")
        guard parts.count == 3 else { return String(capturedAt.prefix(10)) }
        return "\(parts[0]).\(parts[1]).\(parts[2])"
    }

    var thumbnailURL: URL? {
        guard let s = signedUrls?.thumbnail400 ?? signedUrls?.thumbnail150 ?? signedUrls?.original else { return nil }
        return URL(string: s)
    }

    var originalURL: URL? {
        guard let s = signedUrls?.original else { return nil }
        return URL(string: s)
    }
}

struct ProgressPhotoListResponse: Decodable {
    let content: [ProgressPhotoItem]
    let totalElements: Int
    let last: Bool
}

// MARK: - Request Models

struct InitiatePhotoUploadRequest: Encodable {
    let fileName: String
    let contentType: String
    let fileSizeBytes: Int
}

struct InitiatePhotoUploadResponse: Decodable {
    let storageKey: String
    let uploadUrl: String
}

struct RegisterProgressPhotoRequest: Encodable {
    let storageKey: String
    let contentType: String
    let capturedAt: String
    let photoType: String
    let bodyWeightKg: Double?
    let bodyFatPct: Double?
    let waistCm: Double?
    let notes: String?
    let isBaseline: Bool
    let fileSizeBytes: Int
}
