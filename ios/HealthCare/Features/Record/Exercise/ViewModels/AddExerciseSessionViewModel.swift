import Foundation

@MainActor
final class AddExerciseSessionViewModel: ObservableObject {

    // MARK: - Session fields
    @Published var sessionDate = Date()
    @Published var sessionNotes = ""

    // MARK: - Catalog search
    @Published var catalogQuery = ""
    @Published var catalogResults: [ExerciseCatalogItem] = []
    @Published var isSearchingCatalog = false
    @Published var showCatalogPicker = false

    // MARK: - Draft sets
    @Published var draftSets: [DraftSet] = []

    // MARK: - Submission
    @Published var isSaving = false
    @Published var errorMessage: String?

    // MARK: - Draft Set Model
    struct DraftSet: Identifiable {
        let id = UUID()
        var exercise: ExerciseCatalogItem
        var setType: SetTypeOption = .weighted
        var weightKgText: String = ""
        var repsText: String = ""
        var durationSecondsText: String = ""
        var distanceMText: String = ""

        var setNumber: Int = 1

        enum SetTypeOption: String, CaseIterable {
            case weighted  = "WEIGHTED"
            case bodyweight = "BODYWEIGHT"
            case cardio    = "CARDIO"

            var label: String {
                switch self {
                case .weighted:   return "중량"
                case .bodyweight: return "맨몸"
                case .cardio:     return "유산소"
                }
            }
        }

        var isValid: Bool {
            switch setType {
            case .weighted:
                return Double(weightKgText) != nil && Int(repsText) != nil
            case .bodyweight:
                return Int(repsText) != nil
            case .cardio:
                return Int(durationSecondsText) != nil
            }
        }
    }

    var canSave: Bool {
        !draftSets.isEmpty && draftSets.allSatisfy { $0.isValid }
    }

    // MARK: - Catalog Search

    func searchCatalog(apiClient: APIClient) async {
        let q = catalogQuery.trimmingCharacters(in: .whitespaces)
        isSearchingCatalog = true
        defer { isSearchingCatalog = false }

        do {
            let results: [ExerciseCatalogItem] = try await apiClient.request(
                .getExerciseCatalog(query: q.isEmpty ? nil : q)
            )
            catalogResults = results
        } catch {
            catalogResults = []
        }
    }

    // MARK: - Draft Sets Management

    func addSet(exercise: ExerciseCatalogItem) {
        // 같은 운동이 이미 있으면 setNumber 이어서 증가
        let existingCount = draftSets.filter { $0.exercise.id == exercise.id }.count
        var draft = DraftSet(exercise: exercise)
        draft.setNumber = existingCount + 1

        // 운동 타입에 따라 기본 SetType 설정
        switch exercise.exerciseType {
        case "CARDIO":     draft.setType = .cardio
        case "BODYWEIGHT": draft.setType = .bodyweight
        default:           draft.setType = .weighted
        }

        draftSets.append(draft)
        showCatalogPicker = false
    }

    func removeSet(at offsets: IndexSet) {
        draftSets.remove(atOffsets: offsets)
    }

    // MARK: - Save

    func save(apiClient: APIClient, onSuccess: @escaping @MainActor (CreateSessionResponse) -> Void) async {
        guard canSave else {
            errorMessage = "모든 세트 정보를 올바르게 입력해주세요."
            return
        }

        isSaving = true
        errorMessage = nil
        defer { isSaving = false }

        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy-MM-dd"

        let sets = draftSets.enumerated().map { idx, draft -> CreateSetRequest in
            CreateSetRequest(
                exerciseCatalogId: draft.exercise.id,
                setNumber: idx + 1,
                setType: draft.setType.rawValue,
                weightKg: Double(draft.weightKgText),
                reps: Int(draft.repsText),
                durationSeconds: Int(draft.durationSecondsText),
                distanceM: Double(draft.distanceMText),
                restSeconds: nil,
                notes: nil
            )
        }

        let request = CreateSessionRequest(
            sessionDate: dateFormatter.string(from: sessionDate),
            startedAt: nil,
            endedAt: nil,
            notes: sessionNotes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : sessionNotes,
            sets: sets
        )

        do {
            let body = try apiClient.encode(request)
            let response: CreateSessionResponse = try await apiClient.request(
                .createExerciseSession(body: body)
            )
            onSuccess(response)
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = "저장 중 오류가 발생했습니다."
        }
    }
}
