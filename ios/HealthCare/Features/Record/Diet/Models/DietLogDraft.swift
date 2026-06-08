import Foundation

enum DietLogDraft {
    case manual([DraftFoodEntry])
    case photoAnalysis(id: Int, entries: [DraftFoodEntry], warnings: [String], previewURL: String?)

    var entries: [DraftFoodEntry] {
        switch self {
        case .manual(let e):                        return e
        case .photoAnalysis(_, let e, _, _):        return e
        }
    }

    var isEmpty: Bool { entries.isEmpty }

    func entry(id: DraftFoodEntry.ID) -> DraftFoodEntry? {
        entries.first { $0.id == id }
    }

    mutating func append(_ entry: DraftFoodEntry) {
        guard case .manual(var entries) = self else { return }
        entries.append(entry)
        self = .manual(entries)
    }

    mutating func remove(at offsets: IndexSet) {
        switch self {
        case .manual(var entries):
            entries.remove(atOffsets: offsets)
            self = .manual(entries)
        case .photoAnalysis(let id, var entries, let warnings, let previewURL):
            entries.remove(atOffsets: offsets)
            self = .photoAnalysis(id: id, entries: entries, warnings: warnings, previewURL: previewURL)
        }
    }

    mutating func remove(id entryId: DraftFoodEntry.ID) {
        switch self {
        case .manual(var entries):
            entries.removeAll { $0.id == entryId }
            self = .manual(entries)
        case .photoAnalysis(let id, var entries, let warnings, let previewURL):
            entries.removeAll { $0.id == entryId }
            self = .photoAnalysis(id: id, entries: entries, warnings: warnings, previewURL: previewURL)
        }
    }

    mutating func updateEntry(at index: Int, with newValue: DraftFoodEntry) {
        switch self {
        case .manual(var entries):
            guard entries.indices.contains(index) else { return }
            entries[index] = newValue
            self = .manual(entries)
        case .photoAnalysis(let id, var entries, let warnings, let previewURL):
            guard entries.indices.contains(index) else { return }
            entries[index] = newValue
            self = .photoAnalysis(id: id, entries: entries, warnings: warnings, previewURL: previewURL)
        }
    }

    mutating func updateEntry(id entryId: DraftFoodEntry.ID, with newValue: DraftFoodEntry) {
        switch self {
        case .manual(var entries):
            guard let index = entries.firstIndex(where: { $0.id == entryId }) else { return }
            entries[index] = newValue
            self = .manual(entries)
        case .photoAnalysis(let id, var entries, let warnings, let previewURL):
            guard let index = entries.firstIndex(where: { $0.id == entryId }) else { return }
            entries[index] = newValue
            self = .photoAnalysis(id: id, entries: entries, warnings: warnings, previewURL: previewURL)
        }
    }
}
