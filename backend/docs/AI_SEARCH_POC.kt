// AI 기반 식품 검색 Proof of Concept
// PostgreSQL + pgvector + OpenAI Embeddings

package com.healthcare.domain.diet.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

// ============================================================
// 1. OpenAI Embedding Service
// ============================================================

@Service
class OpenAIEmbeddingService(
    private val openAIApiKey: String // application.yml에서 주입
) {
    private val webClient = WebClient.builder()
        .baseUrl("https://api.openai.com/v1")
        .defaultHeader("Authorization", "Bearer $openAIApiKey")
        .build()

    suspend fun createEmbedding(text: String): List<Float> {
        val request = EmbeddingRequest(
            model = "text-embedding-3-small", // $0.02 / 1M tokens
            input = text
        )

        val response = webClient.post()
            .uri("/embeddings")
            .bodyValue(request)
            .retrieve()
            .awaitBody<EmbeddingResponse>()

        return response.data[0].embedding
    }

    data class EmbeddingRequest(
        val model: String,
        val input: String
    )

    data class EmbeddingResponse(
        val data: List<EmbeddingData>
    )

    data class EmbeddingData(
        val embedding: List<Float>
    )
}

// ============================================================
// 2. Vector Search Service
// ============================================================

@Service
class VectorFoodSearchService(
    private val jdbcTemplate: JdbcTemplate,
    private val embeddingService: OpenAIEmbeddingService,
    private val foodRepository: FoodCatalogRepository
) {

    /**
     * 의미론적 검색
     *
     * 예시:
     * - "단백질" → [닭가슴살, 소고기, 두부, 프로틴바]
     * - "저칼로리 간식" → [블루베리, 그릭요거트, 당근]
     */
    suspend fun semanticSearch(
        query: String,
        topK: Int = 10,
        similarityThreshold: Float = 0.7f
    ): List<FoodCatalogResponse> {
        // 1. 쿼리를 벡터로 변환
        val queryVector = embeddingService.createEmbedding(query)

        // 2. 벡터 유사도 검색 (PostgreSQL + pgvector)
        val sql = """
            SELECT id, name, name_ko,
                   1 - (name_embedding <=> ?::vector) as similarity
            FROM food_catalog
            WHERE deleted_at IS NULL
              AND 1 - (name_embedding <=> ?::vector) > ?
            ORDER BY name_embedding <=> ?::vector
            LIMIT ?
        """.trimIndent()

        val vectorStr = "[${queryVector.joinToString(",")}]"

        val results = jdbcTemplate.query(sql,
            vectorStr, vectorStr, similarityThreshold, vectorStr, topK
        ) { rs, _ ->
            SearchResult(
                id = rs.getLong("id"),
                name = rs.getString("name"),
                nameKo = rs.getString("name_ko"),
                similarity = rs.getFloat("similarity")
            )
        }

        // 3. 상세 정보 조회
        val foodIds = results.map { it.id }
        val foods = foodRepository.findAllById(foodIds)

        return foods.map { FoodCatalogResponse.from(it) }
    }

    /**
     * 하이브리드 검색: 키워드 + 벡터
     *
     * 정확한 매칭을 우선하되, 의미론적 유사도도 고려
     */
    suspend fun hybridSearch(
        query: String,
        topK: Int = 10
    ): List<FoodCatalogResponse> {
        // 1. 키워드 검색 (기존 방식)
        val keywordResults = foodRepository.findAccessibleToUser(
            userId = null, // 글로벌 검색
            query = query,
            category = null,
            customOnly = false
        ).take(topK / 2)

        // 2. 벡터 검색
        val vectorResults = semanticSearch(query, topK / 2)

        // 3. 결합 & 중복 제거
        val combined = (keywordResults.map { FoodCatalogResponse.from(it) } + vectorResults)
            .distinctBy { it.id }
            .take(topK)

        return combined
    }

    data class SearchResult(
        val id: Long,
        val name: String,
        val nameKo: String?,
        val similarity: Float
    )
}

// ============================================================
// 3. Batch Embedding Generator (초기 데이터 임베딩)
// ============================================================

@Service
class FoodEmbeddingBatchService(
    private val foodRepository: FoodCatalogRepository,
    private val embeddingService: OpenAIEmbeddingService,
    private val jdbcTemplate: JdbcTemplate
) {

    /**
     * 모든 식품의 name + nameKo를 임베딩하여 DB에 저장
     *
     * 실행: 앱 시작 시 1회만 실행하거나, 수동 트리거
     */
    @Transactional
    suspend fun generateEmbeddingsForAllFoods() {
        val foods = foodRepository.findAll()

        println("📊 총 ${foods.size}개 식품 임베딩 생성 시작...")

        foods.forEachIndexed { index, food ->
            try {
                // 한글 + 영문 조합으로 임베딩
                val text = listOfNotNull(food.name, food.nameKo)
                    .joinToString(" ")

                val embedding = embeddingService.createEmbedding(text)

                // DB에 저장
                val vectorStr = "[${embedding.joinToString(",")}]"
                jdbcTemplate.update(
                    "UPDATE food_catalog SET name_embedding = ?::vector WHERE id = ?",
                    vectorStr, food.id
                )

                if ((index + 1) % 10 == 0) {
                    println("✅ 진행: ${index + 1}/${foods.size}")
                }

                // Rate limiting (OpenAI: 3000 RPM)
                kotlinx.coroutines.delay(20) // 50 req/sec = 3000 req/min

            } catch (e: Exception) {
                println("❌ 실패: ${food.name} - ${e.message}")
            }
        }

        println("🎉 임베딩 생성 완료!")
    }
}

// ============================================================
// 4. Controller (API Endpoint)
// ============================================================

@RestController
@RequestMapping("/api/v1/diet/catalog")
class FoodCatalogController(
    private val vectorSearchService: VectorFoodSearchService,
    private val traditionalSearchService: FoodCatalogService // 기존 서비스
) {

    /**
     * GET /api/v1/diet/catalog/smart-search?query=단백질
     *
     * AI 기반 스마트 검색
     */
    @GetMapping("/smart-search")
    suspend fun smartSearch(
        @RequestParam query: String,
        @RequestParam(defaultValue = "hybrid") mode: String // vector, keyword, hybrid
    ): ResponseEntity<ApiResponse<List<FoodCatalogResponse>>> {

        val results = when (mode) {
            "vector" -> vectorSearchService.semanticSearch(query)
            "hybrid" -> vectorSearchService.hybridSearch(query)
            else -> traditionalSearchService.searchFoods(null, FoodSearchParams.of(query, null, false))
                .map { it } // 이미 FoodCatalogResponse
        }

        return ResponseEntity.ok(ApiResponse.ok(results))
    }
}

// ============================================================
// 5. Database Migration (SQL)
// ============================================================

/*
-- V5__add_vector_search.sql

-- 1. pgvector extension 설치
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. 벡터 컬럼 추가 (1536 차원 = text-embedding-3-small)
ALTER TABLE food_catalog
ADD COLUMN IF NOT EXISTS name_embedding vector(1536);

-- 3. 벡터 인덱스 생성 (IVFFlat 알고리즘)
CREATE INDEX IF NOT EXISTS idx_food_catalog_name_embedding
ON food_catalog
USING ivfflat (name_embedding vector_cosine_ops)
WITH (lists = 100);

-- 4. 기존 인덱스 유지 (키워드 검색용)
-- idx_food_catalog_category (이미 존재)
-- idx_food_catalog_custom (이미 존재)

COMMENT ON COLUMN food_catalog.name_embedding IS
'OpenAI text-embedding-3-small 벡터 (1536d) - 의미론적 검색용';
*/

// ============================================================
// 6. iOS Integration
// ============================================================

/*
// Swift - iOS

enum SearchMode: String {
    case keyword = "keyword"  // 기존 방식
    case vector = "vector"    // AI 벡터 검색
    case hybrid = "hybrid"    // 하이브리드 (추천)
}

extension APIEndpoint {
    case smartFoodSearch(query: String, mode: SearchMode)

    var path: String {
        switch self {
        case .smartFoodSearch:
            return "/api/v1/diet/catalog/smart-search"
        // ...
        }
    }

    var queryItems: [URLQueryItem]? {
        switch self {
        case .smartFoodSearch(let q, let mode):
            return [
                .init(name: "query", value: q),
                .init(name: "mode", value: mode.rawValue)
            ]
        // ...
        }
    }
}

// ViewModel
@MainActor
final class AddDietLogViewModel: ObservableObject {
    func searchCatalog(apiClient: APIClient) async {
        // ...
        do {
            let results: [FoodCatalogItem] = try await apiClient.request(
                .smartFoodSearch(query: searchQuery, mode: .hybrid)
            )
            catalogResults = results
        } catch {
            // ...
        }
    }
}
*/

// ============================================================
// 7. 성능 최적화 팁
// ============================================================

/*
1. **캐싱**:
   - Redis에 자주 검색되는 쿼리 결과 캐싱
   - TTL: 1시간

2. **배치 임베딩**:
   - 신규 식품 추가 시에만 임베딩 생성
   - 기존 데이터는 1회만 임베딩

3. **인덱스 최적화**:
   - lists 파라미터 조정 (데이터 양에 따라)
   - HNSW 알고리즘 고려 (더 빠름, 메모리 많이 사용)

4. **비동기 처리**:
   - 임베딩 생성을 백그라운드 작업으로 처리
   - Kafka/RabbitMQ 큐 활용
*/

// ============================================================
// 8. A/B 테스트 가이드
// ============================================================

/*
1. **그룹 분할**:
   - 그룹 A: 기존 키워드 검색 (50%)
   - 그룹 B: 하이브리드 검색 (50%)

2. **측정 지표**:
   - 첫 검색에서 원하는 결과 찾는 비율
   - 평균 검색 횟수
   - 식품 기록 완료율
   - 검색 응답 시간

3. **기간**:
   - 최소 2주 (충분한 데이터 수집)

4. **의사결정 기준**:
   - 검색 만족도 20% 이상 개선 → 전면 적용
   - 응답 시간 2배 이상 증가 → 재검토
*/
