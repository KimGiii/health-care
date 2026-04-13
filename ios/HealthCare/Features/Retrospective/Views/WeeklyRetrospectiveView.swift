import SwiftUI

struct WeeklyRetrospectiveView: View {
    @StateObject private var viewModel = WeeklyRetrospectiveViewModel()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    Text("주간 회고")
                        .font(.title2.bold())
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding()
            }
            .navigationTitle("회고")
            .navigationBarTitleDisplayMode(.large)
        }
        .task { await viewModel.load() }
    }
}
