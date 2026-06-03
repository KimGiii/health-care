import Charts
import SwiftUI

struct BodyMeasurementView: View {
    @StateObject private var viewModel = BodyMeasurementViewModel()
    @EnvironmentObject private var container: AppContainer
    @Environment(\.dismiss) private var dismiss
    @State private var showMedicalSources = false

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            ScrollView(showsIndicators: false) {
                VStack(spacing: 0) {
                    BodyHeroSection(
                        latest: viewModel.latestMeasurement,
                        isLoading: viewModel.isLoading,
                        onDismiss: { dismiss() }
                    )

                    VStack(spacing: 16) {
                        if viewModel.isLoading {
                            ProgressView()
                                .frame(maxWidth: .infinity)
                                .padding(.top, Spacing.xxxl) // design-lint:ignore — micro/hero spacing
                        } else if viewModel.measurements.isEmpty {
                            EmptyMeasurementCard { viewModel.showAddSheet = true }
                                .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing
                        } else {
                            if let latest = viewModel.latestMeasurement {
                                LatestStatsCard(
                                    measurement: latest,
                                    onSourceTap: { showMedicalSources = true }
                                )
                                .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing
                            }
                            MeasurementTrendSection(viewModel: viewModel)
                                .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing
                            MeasurementHistorySection(
                                measurements: viewModel.measurements,
                                onDelete: { id in
                                    Task { await viewModel.delete(id: id, apiClient: container.apiClient) }
                                }
                            )
                        }
                    }
                    .padding(.top, Spacing.xl) // design-lint:ignore — micro/hero spacing
                    .padding(.bottom, 80) // design-lint:ignore — micro/hero spacing
                }
            }
            .ignoresSafeArea(edges: .top)
            .background(Color.backgroundPage)
            .refreshable {
                await viewModel.load(apiClient: container.apiClient)
                viewModel.errorMessage = nil
            }
            .sheet(isPresented: $showMedicalSources) {
                MedicalSourcesView()
            }

            // FAB
            Button { viewModel.showAddSheet = true } label: {
                Image(systemName: "plus")
                    .font(.system(size: 22, weight: .semibold)) // design-lint:ignore — SF Symbol or special
                    .foregroundStyle(.white)
                    .frame(width: 58, height: 58)
                    .background(Color.brandPrimary)
                    .clipShape(Circle())
                    .shadow(color: Color.brandPrimary.opacity(0.45), radius: 12, x: 0, y: 6)
            }
            .padding(.trailing, Spacing.xxl) // design-lint:ignore — micro/hero spacing
            .padding(.bottom, Spacing.xxl) // design-lint:ignore — micro/hero spacing
        }
        .navigationBarHidden(true)
        .sheet(isPresented: $viewModel.showAddSheet) {
            AddMeasurementView {
                viewModel.showAddSheet = false
                Task { await viewModel.measurementAdded(apiClient: container.apiClient) }
            }
            .environmentObject(container)
        }
        .alert(Text("오류"), isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button(String(localized: "common.ok"), role: .cancel) {}
        } message: {
            Text(viewModel.errorMessage ?? "")
        }
        .task { await viewModel.load(apiClient: container.apiClient) }
        .onChange(of: viewModel.selectedRange) { _ in
            Task { await viewModel.loadTrendData(apiClient: container.apiClient) }
        }
        .onChange(of: viewModel.selectedMetric) { _ in
            Task { await viewModel.loadTrendData(apiClient: container.apiClient) }
        }
        .sheet(isPresented: $showMedicalSources) { MedicalSourcesView() }
    }
}

// MARK: - Hero Section

private struct BodyHeroSection: View {
    let latest: MeasurementResponse?
    let isLoading: Bool
    let onDismiss: () -> Void

    var body: some View {
        ZStack(alignment: .top) {
            BodyWaveBackground().frame(height: 220)

            VStack(spacing: 0) {
                Color.clear.frame(height: 54)

                HStack {
                    Button(action: onDismiss) {
                        Image(systemName: "chevron.left")
                            .font(.cta)
                            .foregroundStyle(.white)
                            .padding(Spacing.md) // design-lint:ignore — micro/hero spacing
                            .background(.white.opacity(0.15))
                            .clipShape(Circle())
                    }
                    Spacer()
                    Text(String(localized: "body.title"))
                        .font(.headingMedium).fontWeight(.bold)
                        .foregroundStyle(.white)
                    Spacer()
                    Color.clear.frame(width: 40, height: 40)
                }
                .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing

                if isLoading {
                    ProgressView().tint(.white).padding(.top, Spacing.lg)
                } else if let m = latest {
                    HeroStatsRow(measurement: m).padding(.top, Spacing.lg)
                } else {
                    HeroEmptyState().padding(.top, Spacing.lg)
                }
            }
        }
    }
}

private struct HeroStatsRow: View {
    let measurement: MeasurementResponse

    var body: some View {
        HStack(spacing: 28) {
            if let w = measurement.weightKg {
                HeroStatItem(value: String(format: "%.1f", w), unit: "kg", label: String(localized: "body.metric.weight"))
            }
            if measurement.weightKg != nil && measurement.bodyFatPct != nil {
                Divider().frame(width: 1, height: 36).background(Color.white.opacity(0.2))
            }
            if let bf = measurement.bodyFatPct {
                HeroStatItem(value: String(format: "%.1f", bf), unit: "%", label: String(localized: "body.metric.bodyFat"))
            }
            if measurement.bodyFatPct != nil && measurement.muscleMassKg != nil {
                Divider().frame(width: 1, height: 36).background(Color.white.opacity(0.2))
            }
            if let mm = measurement.muscleMassKg {
                HeroStatItem(value: String(format: "%.1f", mm), unit: "kg", label: String(localized: "body.metric.muscleMass"))
            }
        }
        .padding(.bottom, Spacing.lg) // design-lint:ignore — micro/hero spacing
    }
}

private struct HeroStatItem: View {
    let value: String
    let unit: String
    let label: String

    var body: some View {
        VStack(spacing: 2) {
            HStack(alignment: .lastTextBaseline, spacing: 2) {
                Text(value)
                    .font(.numeralLarge)
                    .foregroundStyle(.white)
                Text(unit)
                    .font(.bodySmall).fontWeight(.medium)
                    .foregroundStyle(.white.opacity(0.75))
            }
            Text(label)
                .font(.captionXSmall)
                .foregroundStyle(.white.opacity(0.65))
        }
    }
}

// 다크 hero 배경 안에 표시되는 빈 상태이므로 EmptyState(라이트 배경 전제) 대신
// 인라인 유지 + 카피만 통일 (docs/COPY.md §4).
private struct HeroEmptyState: View {
    var body: some View {
        VStack(spacing: 6) {
            Image(systemName: "scalemass.fill")
                .font(.system(size: 30, weight: .semibold)) // design-lint:ignore — SF Symbol size
                .foregroundStyle(.white.opacity(0.5))
            Text(String(localized: "body.empty.title"))
                .font(.bodySmall)
                .foregroundStyle(.white.opacity(0.65))
        }
        .padding(.bottom, Spacing.lg) // design-lint:ignore — micro/hero spacing
    }
}

// MARK: - Wave Background

private struct BodyWaveBackground: View {
    var body: some View {
        GeometryReader { geo in
            ZStack {
                // 브랜드 hero: GoalSetting/GoalProgress와 동일한 forest 그린 톤.
                LinearGradient(
                    colors: [Color.brandPrimary, Color.brandSecondary.opacity(0.85)],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                Ellipse()
                    .fill(Color.white.opacity(0.10))
                    .frame(width: geo.size.width * 0.75, height: geo.size.height * 0.65)
                    .offset(x: geo.size.width * 0.25, y: -geo.size.height * 0.12)
                    .rotationEffect(.degrees(-18))
                BodyWaveCurve()
                    .fill(Color.backgroundPage)
                    .frame(height: 80)
                    .frame(maxWidth: .infinity)
                    .offset(y: geo.size.height - 40)
            }
        }
    }
}

private struct BodyWaveCurve: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 0, y: rect.height))
        path.addCurve(
            to: CGPoint(x: rect.width, y: rect.height),
            control1: CGPoint(x: rect.width * 0.28, y: 0),
            control2: CGPoint(x: rect.width * 0.72, y: rect.height * 0.55)
        )
        path.addLine(to: CGPoint(x: rect.width, y: rect.height))
        path.addLine(to: CGPoint(x: 0, y: rect.height))
        path.closeSubpath()
        return path
    }
}

// MARK: - Latest Stats Card

private struct LatestStatsCard: View {
    let measurement: MeasurementResponse
    var onSourceTap: (() -> Void)? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text(String(localized: "body.recent.title"))
                    .font(.headingSmall).fontWeight(.bold)
                    .foregroundStyle(Color.textPrimary)
                Spacer()
                Text(measurement.formattedDate)
                    .font(.caption)
                    .foregroundStyle(Color.textSecondary)
            }

            LazyVGrid(
                columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())],
                spacing: 12
            ) {
                if let v = measurement.weightKg {
                    StatCell(icon: "scalemass.fill", color: Color(hex: "#2563EB"),
                             value: String(format: "%.1f", v), unit: "kg", label: String(localized: "body.metric.weight"))
                }
                if let v = measurement.bodyFatPct {
                    StatCell(icon: "percent", color: Color(hex: "#7C3AED"),
                             value: String(format: "%.1f", v), unit: "%", label: String(localized: "body.metric.bodyFat"))
                }
                if let v = measurement.muscleMassKg {
                    StatCell(icon: "figure.arms.open", color: Color.brandPrimary,
                             value: String(format: "%.1f", v), unit: "kg", label: String(localized: "body.metric.muscleMass"))
                }
                if let v = measurement.bmi {
                    StatCell(icon: "heart.fill", color: Color(hex: "#EA580C"),
                             value: String(format: "%.1f", v), unit: "", label: "BMI")
                }
                if let v = measurement.chestCm {
                    StatCell(icon: "ruler", color: Color(hex: "#0EA5E9"),
                             value: String(format: "%.1f", v), unit: "cm", label: String(localized: "body.circ.chest"))
                }
                if let v = measurement.waistCm {
                    StatCell(icon: "ruler", color: Color(hex: "#DC2626"),
                             value: String(format: "%.1f", v), unit: "cm", label: String(localized: "body.circ.waist"))
                }
                if let v = measurement.hipCm {
                    StatCell(icon: "ruler", color: Color(hex: "#D97706"),
                             value: String(format: "%.1f", v), unit: "cm", label: String(localized: "body.circ.hip"))
                }
                if let v = measurement.thighCm {
                    StatCell(icon: "ruler", color: Color(hex: "#059669"),
                             value: String(format: "%.1f", v), unit: "cm", label: String(localized: "body.circ.thigh"))
                }
                if let v = measurement.armCm {
                    StatCell(icon: "ruler", color: Color(hex: "#7C3AED"),
                             value: String(format: "%.1f", v), unit: "cm", label: String(localized: "body.circ.arm"))
                }
            }

            if measurement.bmi != nil, let tap = onSourceTap {
                Button(action: tap) {
                    Label(String(localized: "body.bmi.sources.short"), systemImage: "info.circle")
                        .font(.caption)
                        .foregroundStyle(Color.brandPrimary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .background(Color.surfacePrimary)
        .clipShape(RoundedRectangle(cornerRadius: Radius.xl))
        .shadow(color: .black.opacity(0.06), radius: 10, x: 0, y: 4)
    }
}

private struct StatCell: View {
    let icon: String
    let color: Color
    let value: String
    let unit: String
    let label: String

    var body: some View {
        VStack(spacing: 6) {
            Image(systemName: icon)
                .font(.bodyLarge).fontWeight(.medium)
                .foregroundStyle(color)
                .frame(width: 40, height: 40)
                .background(color.opacity(0.1))
                .clipShape(RoundedRectangle(cornerRadius: Radius.sm))

            HStack(alignment: .lastTextBaseline, spacing: 1) {
                Text(value)
                    .font(.headingSmall).fontWeight(.bold)
                    .foregroundStyle(Color.textPrimary)
                if !unit.isEmpty {
                    Text(unit)
                        .font(.captionXSmall)
                        .foregroundStyle(Color.textSecondary)
                }
            }
            Text(label)
                .font(.captionXSmall)
                .foregroundStyle(Color.textSecondary)
        }
    }
}

// MARK: - Trend Section

private struct MeasurementTrendSection: View {
    @ObservedObject var viewModel: BodyMeasurementViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(String(localized: "body.trend.title"))
                        .font(.headingMedium).fontWeight(.bold)
                        .foregroundStyle(Color.textPrimary)
                    Text(String(localized: "body.trend.subtitle"))
                        .font(.caption)
                        .foregroundStyle(Color.textSecondary)
                }
                Spacer()
                if let latestValue = viewModel.latestTrendValueText {
                    VStack(alignment: .trailing, spacing: 2) {
                        Text(latestValue + viewModel.currentMetricUnit)
                            .font(.numeralMedium).fontWeight(.bold)
                            .foregroundStyle(Color.textPrimary)
                        if let delta = viewModel.trendChangeText {
                            Text(delta + "  ·  " + viewModel.trendSummaryText)
                                .font(.caption).fontWeight(.medium)
                                .foregroundStyle(delta.hasPrefix("-") ? Color.brandDanger : Color.brandPrimary)
                        }
                    }
                }
            }

            Picker(String(localized: "body.trend.picker"), selection: $viewModel.selectedRange) {
                ForEach(MeasurementTrendRange.allCases) { range in
                    Text(range.title).tag(range)
                }
            }
            .pickerStyle(.segmented)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(MeasurementMetric.allCases) { metric in
                        MetricChip(
                            title: metric.title,
                            isSelected: viewModel.selectedMetric == metric,
                            accent: Color(hex: metric.accentHex)
                        ) {
                            viewModel.selectedMetric = metric
                        }
                    }
                }
            }

            if viewModel.isTrendLoading {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 30) // design-lint:ignore — micro/hero spacing
            } else if viewModel.hasTrendData {
                VStack(alignment: .leading, spacing: 12) {
                    Chart(viewModel.displayTrendPoints) { point in
                        LineMark(
                            x: .value("날짜", point.date),
                            y: .value(viewModel.selectedMetric.title, point.value)
                        )
                        .foregroundStyle(Color(hex: viewModel.selectedMetric.accentHex))
                        .lineStyle(.init(lineWidth: 3, lineCap: .round, lineJoin: .round))

                        AreaMark(
                            x: .value("날짜", point.date),
                            yStart: .value("하한", viewModel.yAxisDomain.lowerBound),
                            yEnd: .value(viewModel.selectedMetric.title, point.value)
                        )
                        .foregroundStyle(
                            LinearGradient(
                                colors: [
                                    Color(hex: viewModel.selectedMetric.accentHex).opacity(0.28),
                                    Color(hex: viewModel.selectedMetric.accentHex).opacity(0.03)
                                ],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )

                        PointMark(
                            x: .value("날짜", point.date),
                            y: .value(viewModel.selectedMetric.title, point.value)
                        )
                        .foregroundStyle(Color(hex: viewModel.selectedMetric.accentHex))
                    }
                    .frame(height: 220)
                    .chartYScale(domain: viewModel.yAxisDomain)
                    .chartYAxis {
                        AxisMarks(position: .leading)
                    }
                    .chartXAxis {
                        AxisMarks(values: .automatic(desiredCount: axisMarkCount)) { value in
                            AxisGridLine(stroke: StrokeStyle(lineWidth: 0.4))
                                .foregroundStyle(Color.black.opacity(0.08))
                            AxisTick()
                                .foregroundStyle(Color.black.opacity(0.15))
                            AxisValueLabel {
                                if let date = value.as(Date.self) {
                                    Text(Self.axisDateFormatter.string(from: date))
                                        .font(.caption2)
                                        .fixedSize()
                                }
                            }
                        }
                    }
                    .padding(.trailing, Spacing.sm) // design-lint:ignore — micro/hero spacing

                    HStack(spacing: 12) {
                        TrendSummaryPill(
                            title: String(localized: "body.compare.current"),
                            value: formattedValue(viewModel.displayTrendPoints.last?.value),
                            unit: viewModel.currentMetricUnit
                        )
                        TrendSummaryPill(
                            title: String(localized: "body.compare.start"),
                            value: formattedValue(viewModel.displayTrendPoints.first?.value),
                            unit: viewModel.currentMetricUnit
                        )
                    }
                }
            } else {
                VStack(spacing: 8) {
                    Image(systemName: "chart.line.uptrend.xyaxis")
                        .font(.system(size: 22, weight: .semibold)) // design-lint:ignore — SF Symbol or special
                        .foregroundStyle(Color.textSecondary.opacity(0.6))
                    Text(String(localized: "body.trend.needMore"))
                        .font(.bodyMedium).fontWeight(.semibold)
                        .foregroundStyle(Color.textPrimary)
                    Text(String(format: String(localized: "body.trend.needMore.detail"), viewModel.selectedMetric.title))
                        .font(.caption)
                        .foregroundStyle(Color.textSecondary)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, Spacing.xxl) // design-lint:ignore — micro/hero spacing
                .background(Color.surfaceSecondary)
                .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
            }
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .background(Color.surfacePrimary)
        .clipShape(RoundedRectangle(cornerRadius: Radius.xl))
        .shadow(color: .black.opacity(0.06), radius: 10, x: 0, y: 4)
    }

    private func formattedValue(_ value: Double?) -> String {
        guard let value else { return "-" }
        return String(format: "%.1f", value)
    }

    private var axisMarkCount: Int {
        switch viewModel.selectedRange {
        case .week7:  return 7
        case .month1: return 5
        case .month3: return 6
        }
    }

    private static let axisDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "M/d"
        formatter.locale = LocaleManager.resolvedLocale
        return formatter
    }()
}

private struct MetricChip: View {
    let title: String
    let isSelected: Bool
    let accent: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.labelSmall)
                .foregroundStyle(isSelected ? .white : accent)
                .padding(.horizontal, Spacing.lg) // design-lint:ignore — micro/hero spacing
                .padding(.vertical, 9) // design-lint:ignore — micro/hero spacing
                .background(isSelected ? accent : accent.opacity(0.12))
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

private struct TrendSummaryPill: View {
    let title: String
    let value: String
    let unit: String

    var body: some View {
        HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.captionXSmall)
                    .foregroundStyle(Color.textSecondary)
                HStack(alignment: .lastTextBaseline, spacing: 2) {
                    Text(value)
                        .font(.system(size: 16, weight: .bold, design: .rounded)) // design-lint:ignore — SF Symbol or special
                        .foregroundStyle(Color.textPrimary)
                    Text(unit)
                        .font(.captionXSmall)
                        .foregroundStyle(Color.textSecondary)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(Spacing.md) // design-lint:ignore — micro/hero spacing
        .frame(maxWidth: .infinity)
        .background(Color.surfaceSecondary)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
    }
}

// MARK: - History Section

private struct MeasurementHistorySection: View {
    let measurements: [MeasurementResponse]
    let onDelete: (Int) -> Void

    @State private var isExpanded = false
    private let visibleLimit = 5

    private var visibleMeasurements: [MeasurementResponse] {
        if isExpanded || measurements.count <= visibleLimit {
            return measurements
        }
        return Array(measurements.prefix(visibleLimit))
    }

    private var hasMore: Bool { measurements.count > visibleLimit }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(String(localized: "body.records.title"))
                .font(.headingMedium).fontWeight(.bold)
                .foregroundStyle(Color.textPrimary)
                .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing

            VStack(spacing: 10) {
                ForEach(visibleMeasurements) { m in
                    MeasurementRow(measurement: m, onDelete: { onDelete(m.id) })
                        .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing
                }

                if hasMore {
                    Button {
                        withAnimation(.easeInOut(duration: 0.25)) {
                            isExpanded.toggle()
                        }
                    } label: {
                        HStack(spacing: 6) {
                            Text(isExpanded
                                 ? String(localized: "body.records.collapse")
                                 : String(format: String(localized: "body.records.showMore"), measurements.count - visibleLimit))
                                .font(.bodyMedium).fontWeight(.semibold)
                            Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                                .font(.captionBold)
                        }
                        .foregroundStyle(Color.brandPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, Spacing.md) // design-lint:ignore — micro/hero spacing
                        .background(Color.brandPrimary.opacity(0.08))
                        .clipShape(RoundedRectangle(cornerRadius: Radius.md))
                    }
                    .padding(.horizontal, Spacing.xl) // design-lint:ignore — micro/hero spacing
                }
            }
        }
    }
}

private struct MeasurementRow: View {
    let measurement: MeasurementResponse
    let onDelete: () -> Void
    @State private var showDeleteConfirm = false

    var body: some View {
        HStack(spacing: 14) {
            VStack(alignment: .leading, spacing: 2) {
                Text(measurement.formattedDate)
                    .font(.bodyMedium).fontWeight(.semibold)
                    .foregroundStyle(Color.textPrimary)
                Text(summaryText)
                    .font(.caption)
                    .foregroundStyle(Color.textSecondary)
                    .lineLimit(1)
            }

            Spacer()

            Button { showDeleteConfirm = true } label: {
                Image(systemName: "ellipsis")
                    .font(.bodyMedium)
                    .foregroundStyle(Color.textSecondary)
                    .frame(width: 32, height: 32)
                    .background(Color.surfaceSecondary)
                    .clipShape(Circle())
            }
        }
        .padding(Spacing.lg) // design-lint:ignore — micro/hero spacing
        .background(Color.surfacePrimary)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
        .shadow(color: .black.opacity(0.04), radius: 6, x: 0, y: 2)
        .confirmationDialog(Text("body.records.deleteConfirm.title"), isPresented: $showDeleteConfirm) {
            Button(String(localized: "common.delete.button"), role: .destructive) { onDelete() }
            Button(String(localized: "common.cancel"), role: .cancel) {}
        } message: {
            Text(String(localized: "body.records.deleteConfirm.message"))
        }
    }

    private var summaryText: String {
        var parts: [String] = []
        if let w = measurement.weightKg  { parts.append(String(format: String(localized: "body.summary.weight"), w)) }
        if let bf = measurement.bodyFatPct { parts.append(String(format: String(localized: "body.summary.bodyFat"), bf)) }
        if let mm = measurement.muscleMassKg { parts.append(String(format: String(localized: "body.summary.muscle"), mm)) }
        return parts.isEmpty ? String(localized: "body.summary.empty") : parts.joined(separator: "  ·  ")
    }
}

// MARK: - Empty State

private struct EmptyMeasurementCard: View {
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 16) {
                ZStack {
                    Circle().fill(Color.brandSurface).frame(width: 72, height: 72)
                    Image(systemName: "plus.circle.fill")
                        .font(.system(size: 32)) // design-lint:ignore — SF Symbol or special
                        .foregroundStyle(Color.brandPrimary)
                }
                VStack(spacing: 6) {
                    Text(String(localized: "body.empty.firstTitle"))
                        .font(.bodyLarge).fontWeight(.bold)
                        .foregroundStyle(Color.textPrimary)
                    Text(String(localized: "body.empty.firstMessage"))
                        .font(.bodySmall)
                        .foregroundStyle(Color.textSecondary)
                        .multilineTextAlignment(.center)
                        .lineSpacing(3)
                }
                Text(String(localized: "body.empty.firstAction"))
                    .font(.bodyMedium).fontWeight(.semibold)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 30) // design-lint:ignore — micro/hero spacing
                    .padding(.vertical, 11) // design-lint:ignore — micro/hero spacing
                    .background(Color.brandPrimary)
                    .clipShape(Capsule())
            }
            .frame(maxWidth: .infinity)
            .padding(Spacing.xxl) // design-lint:ignore — micro/hero spacing
            .background(Color.surfacePrimary)
            .clipShape(RoundedRectangle(cornerRadius: Radius.xl))
            .shadow(color: .black.opacity(0.06), radius: 10, x: 0, y: 4)
        }
        .buttonStyle(.plain)
    }
}
