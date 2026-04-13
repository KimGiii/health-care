import SwiftUI

struct HistoryCalendarView: View {
    @StateObject private var viewModel = HistoryCalendarViewModel()

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                Text("히스토리 / 캘린더")
                    .font(.title2.bold())
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding()
        }
        .navigationTitle("히스토리")
        .navigationBarTitleDisplayMode(.inline)
        .task { await viewModel.load() }
    }
}
