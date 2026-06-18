import SwiftUI
import WidgetKit

extension View {
    @ViewBuilder
    func widgetContainerBackgroundIfAvailable() -> some View {
        if #available(iOSApplicationExtension 17.0, *) {
            self.containerBackground(.background, for: .widget)
        } else {
            self
        }
    }
}
