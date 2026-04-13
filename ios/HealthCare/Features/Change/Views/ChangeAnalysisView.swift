import SwiftUI

struct ChangeAnalysisView: View {
    @StateObject private var viewModel = ChangeAnalysisViewModel()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    Text("변화 분석")
                        .font(.title2.bold())
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding()
            }
            .navigationTitle("변화")
            .navigationBarTitleDisplayMode(.large)
        }
        .task { await viewModel.load() }
    }
}
