import SwiftUI

// MARK: - ViewModel (inline)

@MainActor
final class DietLogDetailViewModel: ObservableObject {
    @Published var detail: DietLogDetailResponse?
    @Published var isLoading = false
    @Published var errorMessage: String?

    func load(id: Int, apiClient: APIClient) async {
        isLoading = true
        defer { isLoading = false }
        do {
            detail = try await apiClient.request(.getDietLog(id: id))
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = "상세 정보를 불러오지 못했습니다."
        }
    }
}

// MARK: - DietLogDetailView

struct DietLogDetailView: View {
    let logId: Int
    let mealType: MealType
    let logDate: String

    @EnvironmentObject private var container: AppContainer
    @StateObject private var viewModel = DietLogDetailViewModel()
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack(alignment: .top) {
            Color.brandLight.ignoresSafeArea()
            if viewModel.isLoading {
                VStack { Spacer(); ProgressView(); Spacer() }
            } else if let detail = viewModel.detail {
                ScrollView {
                    VStack(spacing: 0) {
                        DietDetailHeader(detail: detail)
                        VStack(spacing: 16) {
                            nutritionCard(detail: detail)
                            entriesSection(detail: detail)
                            if let notes = detail.notes, !notes.isEmpty {
                                notesCard(notes: notes)
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.top, 20)
                        .padding(.bottom, 32)
                    }
                }
                .ignoresSafeArea(edges: .top)
            }
        }
        .navigationBarHidden(true)
        .overlay(alignment: .topLeading) {
            Button { dismiss() } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundColor(.white)
                    .padding(10)
                    .background(Color.black.opacity(0.25))
                    .clipShape(Circle())
            }
            .padding(.leading, 16)
            .padding(.top, 56)
        }
        .task { await viewModel.load(id: logId, apiClient: container.apiClient) }
    }

    private func nutritionCard(detail: DietLogDetailResponse) -> some View {
        VStack(spacing: 14) {
            HStack {
                Text("영양 정보")
                    .font(.subheadline.bold())
                    .foregroundColor(.brandPrimary)
                Spacer()
            }
            HStack(spacing: 0) {
                NutritionStatCell(
                    label: "칼로리",
                    value: String(format: "%.0f", detail.totalCalories ?? 0),
                    unit: "kcal",
                    color: .brandAccent
                )
                Divider().frame(height: 40)
                NutritionStatCell(
                    label: "단백질",
                    value: String(format: "%.1f", detail.totalProteinG ?? 0),
                    unit: "g",
                    color: .blue
                )
                Divider().frame(height: 40)
                NutritionStatCell(
                    label: "탄수화물",
                    value: String(format: "%.1f", detail.totalCarbsG ?? 0),
                    unit: "g",
                    color: .orange
                )
                Divider().frame(height: 40)
                NutritionStatCell(
                    label: "지방",
                    value: String(format: "%.1f", detail.totalFatG ?? 0),
                    unit: "g",
                    color: .pink
                )
            }
        }
        .padding(16)
        .background(Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .shadow(color: .black.opacity(0.05), radius: 4, y: 2)
    }

    private func entriesSection(detail: DietLogDetailResponse) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("식품 목록")
                .font(.subheadline.bold())
                .foregroundColor(.secondary)
            VStack(spacing: 1) {
                ForEach(Array(detail.entries.enumerated()), id: \.element.id) { idx, entry in
                    FoodEntryRow(entry: entry)
                    if idx < detail.entries.count - 1 {
                        Divider().padding(.leading, 56)
                    }
                }
            }
            .background(Color(.systemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .shadow(color: .black.opacity(0.05), radius: 4, y: 2)
        }
    }

    private func notesCard(notes: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("메모", systemImage: "note.text")
                .font(.subheadline.bold())
                .foregroundColor(.secondary)
            Text(notes)
                .font(.body)
                .foregroundColor(.primary)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(16)
        .background(Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .shadow(color: .black.opacity(0.05), radius: 4, y: 2)
    }
}

// MARK: - DietDetailHeader (Wave 헤더)

private struct DietDetailHeader: View {
    let detail: DietLogDetailResponse

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.brandPrimary
            DietDetailWaveCurve()
                .fill(Color.brandLight)
                .frame(height: 40)
                .offset(y: 1)

            VStack(spacing: 6) {
                Text(detail.mealType.emoji)
                    .font(.system(size: 44))
                Text(detail.mealType.displayName)
                    .font(.title2.bold())
                    .foregroundColor(.white)
                Text(formattedDate(detail.logDate))
                    .font(.subheadline)
                    .foregroundColor(.white.opacity(0.8))
            }
            .padding(.top, 80)
            .padding(.bottom, 48)
        }
        .frame(maxWidth: .infinity)
    }

    private func formattedDate(_ s: String) -> String {
        let parts = s.split(separator: "-")
        guard parts.count == 3 else { return s }
        return "\(parts[0])년 \(parts[1])월 \(parts[2])일"
    }
}

private struct DietDetailWaveCurve: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 0, y: rect.maxY))
        path.addCurve(
            to: CGPoint(x: rect.maxX, y: rect.maxY),
            control1: CGPoint(x: rect.width * 0.3, y: rect.minY),
            control2: CGPoint(x: rect.width * 0.7, y: rect.minY)
        )
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}

// MARK: - NutritionStatCell

private struct NutritionStatCell: View {
    let label: String
    let value: String
    let unit: String
    let color: Color

    var body: some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.subheadline.bold())
                .foregroundColor(color)
            Text(unit)
                .font(.caption2)
                .foregroundColor(.secondary)
            Text(label)
                .font(.caption2)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - FoodEntryRow

private struct FoodEntryRow: View {
    let entry: FoodEntryResponse

    var body: some View {
        HStack(spacing: 12) {
            Text(entry.category?.emoji ?? "🍽")
                .font(.title3)
                .frame(width: 40, height: 40)
                .background(Color.brandSurface)
                .clipShape(RoundedRectangle(cornerRadius: 8))

            VStack(alignment: .leading, spacing: 3) {
                Text(entry.displayName)
                    .font(.subheadline.bold())
                Text(String(format: "%.0fg", entry.servingG))
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            Spacer()
            Text(entry.calories.map { String(format: "%.0f kcal", $0) } ?? "-")
                .font(.subheadline.bold())
                .foregroundColor(.brandAccent)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
    }
}

private extension Optional where Wrapped == Double {
    func map(_ transform: (Double) -> String) -> String? {
        guard let self = self else { return nil }
        return transform(self)
    }
}
