import SwiftUI

struct ExerciseRecordView: View {
    @StateObject private var viewModel = ExerciseRecordViewModel()

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                Text("운동 기록")
                    .font(.title2.bold())
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding()
        }
        .navigationTitle("운동 기록")
        .navigationBarTitleDisplayMode(.inline)
        .task { await viewModel.load() }
    }
}
