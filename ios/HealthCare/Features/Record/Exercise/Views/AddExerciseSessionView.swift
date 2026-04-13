import SwiftUI

// MARK: - Add Session View

struct AddExerciseSessionView: View {
    @StateObject private var viewModel = AddExerciseSessionViewModel()
    @EnvironmentObject private var container: AppContainer
    @Environment(\.dismiss) private var dismiss

    let onSaved: (CreateSessionResponse) -> Void

    var body: some View {
        NavigationStack {
            ScrollView(showsIndicators: false) {
                VStack(spacing: 20) {
                    // 날짜 선택
                    dateSection

                    // 세트 목록
                    setsSection

                    // 메모
                    notesSection

                    // 저장 버튼
                    saveButton

                    if let error = viewModel.errorMessage {
                        Text(error)
                            .font(.system(size: 13))
                            .foregroundStyle(Color.brandDanger)
                            .padding(.horizontal, 20)
                    }

                    Color.clear.frame(height: 20)
                }
                .padding(.top, 8)
            }
            .background(Color.surfaceGrouped)
            .navigationTitle("운동 기록 추가")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("취소") { dismiss() }
                        .foregroundStyle(Color.textSecondary)
                }
            }
            .sheet(isPresented: $viewModel.showCatalogPicker) {
                ExerciseCatalogPickerView(viewModel: viewModel)
            }
        }
    }

    // MARK: - Date Section

    private var dateSection: some View {
        VStack(alignment: .leading, spacing: 0) {
            sectionLabel("날짜")

            HStack {
                Image(systemName: "calendar")
                    .foregroundStyle(Color.brandPrimary)
                DatePicker("", selection: $viewModel.sessionDate, displayedComponents: .date)
                    .labelsHidden()
                    .environment(\.locale, Locale(identifier: "ko_KR"))
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(Color.surfacePrimary)
            .clipShape(RoundedRectangle(cornerRadius: 14))
        }
        .padding(.horizontal, 16)
    }

    // MARK: - Sets Section

    private var setsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                sectionLabel("세트 구성")
                    .padding(.horizontal, 0)
                Spacer()
                if !viewModel.draftSets.isEmpty {
                    Text("\(viewModel.draftSets.count)세트")
                        .font(.system(size: 13))
                        .foregroundStyle(Color.textSecondary)
                }
            }
            .padding(.horizontal, 16)

            if viewModel.draftSets.isEmpty {
                // 빈 상태 — 운동 추가 유도
                Button {
                    viewModel.showCatalogPicker = true
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: "plus.circle.fill")
                            .font(.system(size: 24))
                            .foregroundStyle(Color.brandPrimary)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("운동 추가하기")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundStyle(Color.brandPrimary)
                            Text("카탈로그에서 운동을 검색해 세트를 구성하세요")
                                .font(.system(size: 12))
                                .foregroundStyle(Color.textSecondary)
                        }
                        Spacer()
                    }
                    .padding(16)
                    .background(Color.brandLight)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                    .overlay(
                        RoundedRectangle(cornerRadius: 14)
                            .stroke(Color.brandAccent.opacity(0.4), lineWidth: 1.5)
                    )
                }
                .padding(.horizontal, 16)
            } else {
                // 세트 카드들
                VStack(spacing: 8) {
                    ForEach(Array($viewModel.draftSets.enumerated()), id: \.element.id) { idx, $draft in
                        DraftSetCard(index: idx, draft: $draft) {
                            viewModel.draftSets.remove(at: idx)
                        }
                    }
                }
                .padding(.horizontal, 16)

                // 운동 추가 버튼
                Button {
                    viewModel.showCatalogPicker = true
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "plus")
                            .font(.system(size: 14, weight: .semibold))
                        Text("운동 추가")
                            .font(.system(size: 14, weight: .semibold))
                    }
                    .foregroundStyle(Color.brandPrimary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Color.brandLight)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .padding(.horizontal, 16)
            }
        }
    }

    // MARK: - Notes Section

    private var notesSection: some View {
        VStack(alignment: .leading, spacing: 0) {
            sectionLabel("메모 (선택)")

            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "note.text")
                    .foregroundStyle(Color.textSecondary)
                    .padding(.top, 2)
                TextField("오늘 운동 느낌이나 컨디션 메모...", text: $viewModel.sessionNotes, axis: .vertical)
                    .font(.system(size: 15))
                    .lineLimit(2...4)
            }
            .padding(14)
            .background(Color.surfacePrimary)
            .clipShape(RoundedRectangle(cornerRadius: 14))
        }
        .padding(.horizontal, 16)
    }

    // MARK: - Save Button

    private var saveButton: some View {
        Button {
            Task {
                await viewModel.save(apiClient: container.apiClient) { response in
                    onSaved(response)
                    dismiss()
                }
            }
        } label: {
            Group {
                if viewModel.isSaving {
                    ProgressView().tint(.white)
                } else {
                    Text("기록 저장")
                        .font(.system(size: 17, weight: .semibold))
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(
                viewModel.canSave
                    ? Color.brandPrimary
                    : Color.brandPrimary.opacity(0.35)
            )
            .foregroundStyle(.white)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .shadow(
                color: viewModel.canSave ? Color.brandPrimary.opacity(0.35) : .clear,
                radius: 10, x: 0, y: 4
            )
        }
        .disabled(!viewModel.canSave || viewModel.isSaving)
        .padding(.horizontal, 16)
        .padding(.top, 4)
    }

    // MARK: - Helpers

    private func sectionLabel(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 12, weight: .semibold))
            .foregroundStyle(Color.textSecondary)
            .textCase(.uppercase)
            .tracking(0.5)
            .padding(.horizontal, 4)
            .padding(.bottom, 8)
    }
}

// MARK: - Draft Set Card

private struct DraftSetCard: View {
    let index: Int
    @Binding var draft: AddExerciseSessionViewModel.DraftSet
    let onDelete: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // 헤더: 운동명 + 세트 번호 + 삭제
            HStack(spacing: 10) {
                // 세트 번호 배지
                Text("세트 \(draft.setNumber)")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(Color.brandPrimary)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.brandSurface)
                    .clipShape(Capsule())

                VStack(alignment: .leading, spacing: 1) {
                    Text(draft.exercise.displayName)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Color.textPrimary)
                    Text(draft.exercise.muscleGroupLabel)
                        .font(.system(size: 11))
                        .foregroundStyle(Color.textSecondary)
                }

                Spacer()

                Button(action: onDelete) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 20))
                        .foregroundStyle(Color.textSecondary.opacity(0.4))
                }
            }

            // 세트 타입 피커
            HStack(spacing: 6) {
                ForEach(AddExerciseSessionViewModel.DraftSet.SetTypeOption.allCases, id: \.self) { opt in
                    Button {
                        draft.setType = opt
                    } label: {
                        Text(opt.label)
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(draft.setType == opt ? .white : Color.brandPrimary)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(
                                draft.setType == opt
                                    ? Color.brandPrimary
                                    : Color.brandLight
                            )
                            .clipShape(Capsule())
                    }
                }
            }

            // 입력 필드
            switch draft.setType {
            case .weighted:
                HStack(spacing: 10) {
                    inputField("무게", unit: "kg", text: $draft.weightKgText, keyboard: .decimalPad)
                    inputField("횟수", unit: "회", text: $draft.repsText, keyboard: .numberPad)
                }
            case .bodyweight:
                inputField("횟수", unit: "회", text: $draft.repsText, keyboard: .numberPad)
            case .cardio:
                HStack(spacing: 10) {
                    inputField("시간", unit: "초", text: $draft.durationSecondsText, keyboard: .numberPad)
                    inputField("거리", unit: "m (선택)", text: $draft.distanceMText, keyboard: .decimalPad)
                }
            }
        }
        .padding(14)
        .background(Color.surfacePrimary)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .shadow(color: .black.opacity(0.04), radius: 4, x: 0, y: 2)
    }

    private func inputField(
        _ placeholder: String,
        unit: String,
        text: Binding<String>,
        keyboard: UIKeyboardType
    ) -> some View {
        HStack(spacing: 4) {
            TextField(placeholder, text: text)
                .keyboardType(keyboard)
                .font(.system(size: 16, weight: .medium))
                .multilineTextAlignment(.trailing)
            Text(unit)
                .font(.system(size: 12))
                .foregroundStyle(Color.textSecondary)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(Color.surfaceSecondary)
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}

// MARK: - Catalog Picker Sheet

struct ExerciseCatalogPickerView: View {
    @ObservedObject var viewModel: AddExerciseSessionViewModel
    @EnvironmentObject private var container: AppContainer
    @Environment(\.dismiss) private var dismiss
    @FocusState private var searchFocused: Bool

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // 검색 바
                searchBar
                    .padding(16)
                    .background(Color.surfacePrimary)

                Divider()

                // 결과
                Group {
                    if viewModel.isSearchingCatalog {
                        ProgressView()
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else if viewModel.catalogResults.isEmpty && !viewModel.catalogQuery.isEmpty {
                        emptySearchResult
                    } else if viewModel.catalogResults.isEmpty {
                        searchPrompt
                    } else {
                        catalogList
                    }
                }
            }
            .background(Color.surfaceGrouped)
            .navigationTitle("운동 선택")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("닫기") { dismiss() }
                        .foregroundStyle(Color.textSecondary)
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("검색") {
                        Task { await viewModel.searchCatalog(apiClient: container.apiClient) }
                    }
                    .fontWeight(.semibold)
                    .foregroundStyle(Color.brandPrimary)
                }
            }
            .onAppear {
                searchFocused = true
                Task { await viewModel.searchCatalog(apiClient: container.apiClient) }
            }
        }
    }

    private var searchBar: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(Color.textSecondary)

            TextField("운동 검색 (예: 벤치프레스, 스쿼트)", text: $viewModel.catalogQuery)
                .focused($searchFocused)
                .submitLabel(.search)
                .onSubmit {
                    Task { await viewModel.searchCatalog(apiClient: container.apiClient) }
                }

            if !viewModel.catalogQuery.isEmpty {
                Button {
                    viewModel.catalogQuery = ""
                    viewModel.catalogResults = []
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(Color.textSecondary.opacity(0.6))
                }
            }
        }
        .padding(12)
        .background(Color.surfaceSecondary)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private var catalogList: some View {
        List(viewModel.catalogResults) { item in
            Button {
                viewModel.addSet(exercise: item)
                dismiss()
            } label: {
                CatalogRow(item: item)
            }
            .listRowBackground(Color.surfacePrimary)
            .listRowSeparatorTint(Color(uiColor: .separator).opacity(0.5))
        }
        .listStyle(.plain)
    }

    private var searchPrompt: some View {
        VStack(spacing: 14) {
            Image(systemName: "dumbbell.fill")
                .font(.system(size: 44))
                .foregroundStyle(Color.brandPrimary.opacity(0.25))
            Text("운동 이름으로 검색하세요")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Color.textPrimary)
            Text("빈 칸으로 검색하면 전체 목록을 볼 수 있습니다")
                .font(.system(size: 13))
                .foregroundStyle(Color.textSecondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var emptySearchResult: some View {
        VStack(spacing: 14) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 44))
                .foregroundStyle(Color.textSecondary.opacity(0.3))
            Text("'\(viewModel.catalogQuery)'에 대한 결과 없음")
                .font(.system(size: 15))
                .foregroundStyle(Color.textSecondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Catalog Row

private struct CatalogRow: View {
    let item: ExerciseCatalogItem

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: exerciseIcon(for: item.exerciseType))
                .font(.system(size: 18))
                .foregroundStyle(Color.brandPrimary)
                .frame(width: 40, height: 40)
                .background(Color.brandSurface)
                .clipShape(RoundedRectangle(cornerRadius: 10))

            VStack(alignment: .leading, spacing: 3) {
                Text(item.displayName)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color.textPrimary)
                HStack(spacing: 5) {
                    Text(item.muscleGroupLabel)
                        .font(.system(size: 12))
                        .foregroundStyle(Color.textSecondary)
                    Text("·")
                        .foregroundStyle(Color.textSecondary.opacity(0.5))
                    Text(item.exerciseTypeLabel)
                        .font(.system(size: 12))
                        .foregroundStyle(Color.brandAccent)
                }
            }

            Spacer()

            if item.custom {
                Text("커스텀")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Color.brandWarning)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 3)
                    .background(Color.brandWarning.opacity(0.12))
                    .clipShape(Capsule())
            }
        }
        .padding(.vertical, 6)
    }

    private func exerciseIcon(for type: String) -> String {
        switch type {
        case "CARDIO":      return "figure.run"
        case "BODYWEIGHT":  return "figure.gymnastics"
        case "FLEXIBILITY": return "figure.flexibility"
        default:            return "dumbbell.fill"
        }
    }
}
