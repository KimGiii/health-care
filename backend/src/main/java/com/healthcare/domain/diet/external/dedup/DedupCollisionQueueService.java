package com.healthcare.domain.diet.external.dedup;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 같은 코드·다른 이름키로 남은 COLLISION canonical 행을 운영 검토 큐로 페이지네이션한다.
 */
@Service
@RequiredArgsConstructor
public class DedupCollisionQueueService {

    public static final int MAX_LIMIT = 200;

    private final FoodCatalogRepository foodCatalogRepository;

    @Transactional(readOnly = true)
    public DedupCollisionQueueResponse queue(String afterCode, int limit) {
        int pageSize = Math.max(1, Math.min(limit, MAX_LIMIT));
        String cursor = afterCode == null ? "" : afterCode;

        List<String> groups = foodCatalogRepository.findCollisionGroupsAfter(cursor, pageSize);
        if (groups.isEmpty()) {
            return new DedupCollisionQueueResponse(0, List.of(), null);
        }

        List<FoodCatalog> rows = foodCatalogRepository.findCanonicalsByDedupStateAndGroups(
                FoodCatalog.DedupState.COLLISION,
                groups
        );
        Map<String, List<DedupCollisionMember>> membersByGroup = new LinkedHashMap<>();
        for (String group : groups) {
            membersByGroup.put(group, rows.stream()
                    .filter(row -> group.equals(row.getDedupGroup()))
                    .map(DedupCollisionMember::from)
                    .toList());
        }

        List<DedupCollisionGroup> collisionGroups = membersByGroup.entrySet().stream()
                .map(entry -> new DedupCollisionGroup(entry.getKey(), entry.getValue()))
                .toList();
        String nextCursor = groups.size() == pageSize ? groups.get(groups.size() - 1) : null;

        return new DedupCollisionQueueResponse(collisionGroups.size(), collisionGroups, nextCursor);
    }
}
