import Foundation

@MainActor
final class DietRestrictionViewModel: ObservableObject {
    @Published var restrictions: [DietRestriction] = []
    @Published var isLoading = false
    @Published var errorMessage: String?

    func load(apiClient: APIClient) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            let result: [DietRestriction] = try await apiClient.request(.listDietRestrictions)
            restrictions = result
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = String(localized: "restriction.error.load")
        }
    }

    func addAllergenTag(_ tag: AllergenTag, type: RestrictionType, apiClient: APIClient) async {
        guard !hasAllergenTag(tag) else { return }
        await add(
            CreateDietRestrictionRequest(
                restrictionType: type.rawValue,
                targetType: TargetType.ALLERGEN_TAG.rawValue,
                foodCatalogId: nil,
                category: nil,
                keyword: nil,
                allergenTag: tag.rawValue
            ),
            apiClient: apiClient
        )
    }

    func addCategory(_ category: FoodCategory, type: RestrictionType, apiClient: APIClient) async {
        guard !hasCategory(category) else { return }
        await add(
            CreateDietRestrictionRequest(
                restrictionType: type.rawValue,
                targetType: TargetType.CATEGORY.rawValue,
                foodCatalogId: nil,
                category: category.rawValue,
                keyword: nil,
                allergenTag: nil
            ),
            apiClient: apiClient
        )
    }

    func addKeyword(_ keyword: String, apiClient: APIClient) async {
        let trimmed = keyword.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        await add(
            CreateDietRestrictionRequest(
                restrictionType: RestrictionType.AVOID.rawValue,
                targetType: TargetType.KEYWORD.rawValue,
                foodCatalogId: nil,
                category: nil,
                keyword: trimmed,
                allergenTag: nil
            ),
            apiClient: apiClient
        )
    }

    func delete(restriction: DietRestriction, apiClient: APIClient) async {
        do {
            let _: EmptyResponse = try await apiClient.request(.deleteDietRestriction(id: restriction.id))
            restrictions.removeAll { $0.id == restriction.id }
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = String(localized: "restriction.error.delete")
        }
    }

    // MARK: - Helpers

    func hasAllergenTag(_ tag: AllergenTag) -> Bool {
        restrictions.contains { $0.targetType == .ALLERGEN_TAG && $0.allergenTag == tag }
    }

    func hasCategory(_ category: FoodCategory) -> Bool {
        restrictions.contains { $0.targetType == .CATEGORY && $0.category == category }
    }

    func allergenRestrictions() -> [DietRestriction] {
        restrictions.filter { $0.targetType == .ALLERGEN_TAG }
    }

    func categoryRestrictions() -> [DietRestriction] {
        restrictions.filter { $0.targetType == .CATEGORY }
    }

    func keywordRestrictions() -> [DietRestriction] {
        restrictions.filter { $0.targetType == .KEYWORD }
    }

    // MARK: - Private

    private func add(_ request: CreateDietRestrictionRequest, apiClient: APIClient) async {
        do {
            let body = try JSONEncoder().encode(request)
            let created: DietRestriction = try await apiClient.request(.createDietRestriction(body: body))
            restrictions.append(created)
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = String(localized: "restriction.error.add")
        }
    }
}

private struct EmptyResponse: Decodable {}
