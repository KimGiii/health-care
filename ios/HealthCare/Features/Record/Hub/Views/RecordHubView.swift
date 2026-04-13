import SwiftUI

struct RecordHubView: View {
    @StateObject private var viewModel = RecordHubViewModel()

    var body: some View {
        VStack(spacing: 20) {
            NavigationLink(destination: ExerciseRecordView()) {
                recordCell(title: "운동 기록", icon: "figure.strengthtraining.traditional", color: .orange)
            }
            NavigationLink(destination: DietRecordView()) {
                recordCell(title: "식단 기록", icon: "fork.knife", color: .green)
            }
            NavigationLink(destination: BodyMeasurementView()) {
                recordCell(title: "신체 변화 기록", icon: "scalemass", color: .blue)
            }
            Spacer()
        }
        .padding()
        .navigationTitle("기록")
        .navigationBarTitleDisplayMode(.large)
    }

    private func recordCell(title: String, icon: String, color: Color) -> some View {
        HStack(spacing: 16) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundStyle(color)
                .frame(width: 44, height: 44)
                .background(color.opacity(0.1))
                .clipShape(RoundedRectangle(cornerRadius: 10))

            Text(title)
                .font(.headline)
                .foregroundStyle(.primary)

            Spacer()
            Image(systemName: "chevron.right")
                .foregroundStyle(.tertiary)
        }
        .padding()
        .background(Color.surfaceSecondary)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}
