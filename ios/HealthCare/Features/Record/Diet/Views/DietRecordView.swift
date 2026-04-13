import SwiftUI

struct DietRecordView: View {
    @StateObject private var viewModel = DietRecordViewModel()

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                Text("식단 기록")
                    .font(.title2.bold())
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding()
        }
        .navigationTitle("식단 기록")
        .navigationBarTitleDisplayMode(.inline)
        .task { await viewModel.load() }
    }
}
