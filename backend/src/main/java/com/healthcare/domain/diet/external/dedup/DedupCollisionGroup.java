package com.healthcare.domain.diet.external.dedup;

import java.util.List;

/**
 * 같은 코드(dedup_group)를 공유하지만 이름이 달라 충돌로 표시된 대표 행들의 묶음.
 * 운영자는 한 코드 단위로 동명이품 여부를 검토해 코드 정정 또는 양쪽 유지 정책을 결정한다(설계 §9).
 */
public record DedupCollisionGroup(
        String foodCode,
        List<DedupCollisionMember> members
) {
    public DedupCollisionGroup {
        members = members == null ? List.of() : List.copyOf(members);
    }
}
