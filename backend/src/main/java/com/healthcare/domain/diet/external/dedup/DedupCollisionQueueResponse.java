package com.healthcare.domain.diet.external.dedup;

import java.util.List;

/**
 * 충돌 검토 큐 한 페이지. {@code nextCursor}(마지막 코드)가 있으면 다음 페이지가 더 있을 수 있다.
 * 전체 후보를 메모리로 읽지 않고 코드 커서로 전진하므로 대량 충돌에서도 응답 크기가 일정하다(설계 §5).
 */
public record DedupCollisionQueueResponse(
        int groupCount,
        List<DedupCollisionGroup> groups,
        String nextCursor
) {
    public DedupCollisionQueueResponse {
        groups = groups == null ? List.of() : List.copyOf(groups);
    }
}
