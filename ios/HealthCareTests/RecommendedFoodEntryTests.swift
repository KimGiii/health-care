import XCTest
@testable import HealthCare

final class RecommendedFoodEntryTests: XCTestCase {

    func test_displayServing_낱개라벨이있으면_개수로표시() {
        let egg = RecommendedFoodEntry(
            foodCatalogId: 11,
            name: "egg",
            nameKo: "계란",
            category: .PROTEIN_SOURCE,
            servingG: 100,
            calories: 155,
            proteinG: 13,
            carbsG: 1,
            fatG: 11,
            allergenConfidenceLevel: .UNKNOWN,
            caution: nil,
            servingLabel: "2개"
        )

        XCTAssertEqual(egg.displayServing, "2개")
    }

    func test_displayServing_라벨이없으면_그램으로표시() {
        let chicken = RecommendedFoodEntry(
            foodCatalogId: 7,
            name: "chicken",
            nameKo: "닭가슴살",
            category: .PROTEIN_SOURCE,
            servingG: 120,
            calories: 200,
            proteinG: 30,
            carbsG: 0,
            fatG: 4,
            allergenConfidenceLevel: .UNKNOWN,
            caution: nil
        )

        XCTAssertNil(chicken.servingLabel)
        XCTAssertEqual(chicken.displayServing, "120g")
    }

    func test_servingLabel_JSON에있으면_디코딩되어_개수로표시() throws {
        let json = Data("""
        {
            "foodCatalogId": 11,
            "name": "egg",
            "nameKo": "계란",
            "servingG": 100,
            "calories": 155,
            "proteinG": 13,
            "carbsG": 1,
            "fatG": 11,
            "allergenConfidenceLevel": "UNKNOWN",
            "servingLabel": "2개"
        }
        """.utf8)

        let entry = try JSONDecoder().decode(RecommendedFoodEntry.self, from: json)

        XCTAssertEqual(entry.servingLabel, "2개")
        XCTAssertEqual(entry.displayServing, "2개")
    }
}
