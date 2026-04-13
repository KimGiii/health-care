import SwiftUI

struct SectionHeader: View {
    let title: String
    var actionTitle: String? = nil
    var action: (() -> Void)? = nil

    var body: some View {
        HStack {
            Text(title)
                .font(.headingMedium)
            Spacer()
            if let actionTitle, let action {
                Button(actionTitle, action: action)
                    .font(.bodyMedium)
            }
        }
    }
}
