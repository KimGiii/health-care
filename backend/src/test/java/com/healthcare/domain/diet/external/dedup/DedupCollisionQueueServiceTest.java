package com.healthcare.domain.diet.external.dedup;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.DedupState;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("DedupCollisionQueueService — 같은 코드·다른 이름 충돌 검토 큐(cursor pagination)")
class DedupCollisionQueueServiceTest {

    @Mock private FoodCatalogRepository repository;

    @Captor private ArgumentCaptor<String> cursorCaptor;
    @Captor private ArgumentCaptor<Integer> limitCaptor;

    private DedupCollisionQueueService service;

    @BeforeEach
    void setUp() {
        service = new DedupCollisionQueueService(repository);
    }

    @Test
    @DisplayName("충돌 행을 코드(dedup_group)별로 묶어 멤버 목록으로 반환한다")
    void queue_groupsCollisionRowsByCode() {
        given(repository.findCollisionGroupsAfter(any(), anyInt()))
                .willReturn(List.of("X001", "X002"));
        given(repository.findCanonicalsByDedupStateAndGroups(eq(DedupState.COLLISION), any()))
                .willReturn(List.of(
                        collision(1L, FoodCatalogSource.MFDS_STANDARD_PROCESSED, "X001", "사과"),
                        collision(2L, FoodCatalogSource.MFDS_STANDARD_DISH, "X001", "배"),
                        collision(3L, FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB, "X002", "우유"),
                        collision(4L, FoodCatalogSource.MFDS_STANDARD_DISH, "X002", "두유")
                ));

        DedupCollisionQueueResponse response = service.queue(null, 50);

        assertThat(response.groupCount()).isEqualTo(2);
        assertThat(response.groups()).hasSize(2);
        DedupCollisionGroup first = response.groups().get(0);
        assertThat(first.foodCode()).isEqualTo("X001");
        assertThat(first.members()).hasSize(2);
        assertThat(first.members()).extracting(DedupCollisionMember::nameKo)
                .containsExactly("사과", "배");
        assertThat(first.members().get(0).source())
                .isEqualTo(FoodCatalogSource.MFDS_STANDARD_PROCESSED.name());
    }

    @Test
    @DisplayName("충돌이 없으면 빈 큐와 nextCursor 없음을 반환한다")
    void queue_emptyWhenNoCollisions() {
        given(repository.findCollisionGroupsAfter(any(), anyInt())).willReturn(List.of());

        DedupCollisionQueueResponse response = service.queue(null, 50);

        assertThat(response.groupCount()).isZero();
        assertThat(response.groups()).isEmpty();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("페이지가 가득 차면 마지막 코드를 nextCursor로 제공한다")
    void queue_setsNextCursorWhenPageFull() {
        given(repository.findCollisionGroupsAfter(any(), anyInt()))
                .willReturn(List.of("X001", "X002"));
        given(repository.findCanonicalsByDedupStateAndGroups(eq(DedupState.COLLISION), any()))
                .willReturn(List.of(collision(1L, FoodCatalogSource.MFDS_STANDARD_PROCESSED, "X002", "배")));

        DedupCollisionQueueResponse response = service.queue(null, 2);

        // 반환 코드 수(2)가 limit(2)과 같으면 다음 페이지가 있을 수 있어 커서를 준다.
        assertThat(response.nextCursor()).isEqualTo("X002");
    }

    @Test
    @DisplayName("반환 코드 수가 limit보다 적으면 마지막 페이지이므로 nextCursor가 없다")
    void queue_noNextCursorOnLastPage() {
        given(repository.findCollisionGroupsAfter(any(), anyInt()))
                .willReturn(List.of("X001"));
        given(repository.findCanonicalsByDedupStateAndGroups(eq(DedupState.COLLISION), any()))
                .willReturn(List.of(collision(1L, FoodCatalogSource.MFDS_STANDARD_PROCESSED, "X001", "사과")));

        DedupCollisionQueueResponse response = service.queue(null, 50);

        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("afterCode가 null이면 빈 문자열 커서로 처음부터 조회한다")
    void queue_nullCursorStartsFromBeginning() {
        given(repository.findCollisionGroupsAfter(any(), anyInt())).willReturn(List.of());

        service.queue(null, 50);

        verify(repository).findCollisionGroupsAfter(cursorCaptor.capture(), anyInt());
        assertThat(cursorCaptor.getValue()).isEmpty();
    }

    @Test
    @DisplayName("limit을 1~MAX 범위로 클램프한다")
    void queue_clampsLimit() {
        given(repository.findCollisionGroupsAfter(any(), anyInt())).willReturn(List.of());

        service.queue(null, 100_000);
        service.queue(null, 0);

        verify(repository, org.mockito.Mockito.times(2))
                .findCollisionGroupsAfter(any(), limitCaptor.capture());
        assertThat(limitCaptor.getAllValues().get(0)).isEqualTo(DedupCollisionQueueService.MAX_LIMIT);
        assertThat(limitCaptor.getAllValues().get(1)).isEqualTo(1);
    }

    private FoodCatalog collision(long id, FoodCatalogSource source, String code, String nameKo) {
        return FoodCatalog.builder()
                .id(id)
                .source(source)
                .foodCode(code)
                .name(nameKo)
                .nameKo(nameKo)
                .category(FoodCategory.OTHER)
                .caloriesPer100g(100.0)
                .dedupGroup(code)
                .dedupNameKey(nameKo)
                .dedupState(DedupState.COLLISION)
                .build();
    }
}
