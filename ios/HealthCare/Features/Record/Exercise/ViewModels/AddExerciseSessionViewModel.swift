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

    // MARK: - Draft sets (grouped by exercise)
    @Published var exerciseGroups: [ExerciseGroup] = []

    // ExerciseGroup: 운동별로 세트를 그룹핑
    struct ExerciseGroup: Identifiable {
        let id = UUID()
        var exercise: ExerciseCatalogItem
        var sets: [DraftSet]
    }

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
        var durationMinutesText: String = "" // 분 단위로 변경
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
                return Double(durationMinutesText) != nil // 분 단위 검증
            }
        }
    }

    var canSave: Bool {
        !exerciseGroups.isEmpty &&
        exerciseGroups.allSatisfy { group in
            !group.sets.isEmpty && group.sets.allSatisfy { $0.isValid }
        }
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

    func addExercise(_ exercise: ExerciseCatalogItem) {
        // 같은 운동이 이미 있는지 확인
        if let existingIndex = exerciseGroups.firstIndex(where: { $0.exercise.id == exercise.id }) {
            // 이미 있으면 새 세트 추가
            addSetToGroup(at: existingIndex)
        } else {
            // 새 운동 그룹 생성
            var draft = DraftSet(exercise: exercise)
            draft.setNumber = 1

            // 운동 타입에 따라 기본 SetType 설정
            switch exercise.exerciseType {
            case "CARDIO":     draft.setType = .cardio
            case "BODYWEIGHT": draft.setType = .bodyweight
            default:           draft.setType = .weighted
            }

            let group = ExerciseGroup(exercise: exercise, sets: [draft])
            exerciseGroups.append(group)
        }
        showCatalogPicker = false
    }

    /// 특정 그룹에 세트 추가 (마지막 세트 값 복사)
    func addSetToGroup(at groupIndex: Int) {
        guard groupIndex < exerciseGroups.count else { return }

        let group = exerciseGroups[groupIndex]
        let lastSet = group.sets.last!
        let nextSetNumber = (group.sets.map { $0.setNumber }.max() ?? 0) + 1

        var newSet = DraftSet(exercise: group.exercise)
        newSet.setNumber = nextSetNumber
        newSet.setType = lastSet.setType

        // 이전 값 복사
        newSet.weightKgText = lastSet.weightKgText
        newSet.repsText = lastSet.repsText
        newSet.durationMinutesText = lastSet.durationMinutesText
        newSet.distanceMText = lastSet.distanceMText

        exerciseGroups[groupIndex].sets.append(newSet)
    }

    /// 특정 세트 삭제
    func removeSet(groupIndex: Int, setIndex: Int) {
        guard groupIndex < exerciseGroups.count,
              setIndex < exerciseGroups[groupIndex].sets.count else { return }

        exerciseGroups[groupIndex].sets.remove(at: setIndex)

        // 세트가 모두 삭제되면 운동 그룹도 삭제
        if exerciseGroups[groupIndex].sets.isEmpty {
            exerciseGroups.remove(at: groupIndex)
        }
    }

    /// 운동 그룹 전체 삭제
    func removeExerciseGroup(at index: Int) {
        guard index < exerciseGroups.count else { return }
        exerciseGroups.remove(at: index)
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

        // 모든 그룹의 세트를 flat하게 변환
        let allSets = exerciseGroups.flatMap { $0.sets }
        let sets = allSets.enumerated().map { idx, draft -> CreateSetRequest in
            // 분을 초로 변환 (유산소 운동의 경우)
            let durationSeconds: Int? = {
                if draft.setType == .cardio,
                   let minutes = Double(draft.durationMinutesText) {
                    return Int(minutes * 60)
                }
                return nil
            }()

            return CreateSetRequest(
                exerciseCatalogId: draft.exercise.id,
                setNumber: idx + 1,
                setType: draft.setType.rawValue,
                weightKg: Double(draft.weightKgText),
                reps: Int(draft.repsText),
                durationSeconds: durationSeconds,
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
