# AI 기반 식품 검색 시스템 제안서

## 개요
기존 키워드 기반 검색을 AI/임베딩 기반 의미론적 검색으로 전환하여 사용자 경험 개선

---

## 1. 현재 시스템 (AS-IS)

### 검색 방식
```sql
SELECT * FROM food_catalog 
WHERE LOWER(name) LIKE LOWER('%{query}%') 
   OR LOWER(name_ko) LIKE LOWER('%{query}%')
```

### 한계점
- ❌ 오타 처리 불가 ("닭갈비" ≠ "닭가슴살")
- ❌ 유사어 미지원 ("단백질" 검색 시 "프로틴바" 누락)
- ❌ 자연어 쿼리 불가 ("저칼로리 간식 추천해줘")
- ❌ 의도 파악 불가 ("다이어트 음식" → 고단백/저칼로리 매칭 실패)

---

## 2. 제안 시스템 (TO-BE)

### 아키텍처

```
┌──────────────────────────────────────────────────────────────┐
│                         사용자 입력                           │
│  "단백질 많은 음식", "저칼로리 간식", "닭가슴살"              │
└────────────────────────┬─────────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────────┐
│              Query Understanding Layer (Optional)             │
│  LLM이 사용자 의도 파악 & 검색 파라미터 추출                 │
│  입력: "단백질 많은 음식"                                     │
│  출력: {intent: "high_protein", filters: {minProtein: 20}}    │
└────────────────────────┬─────────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────────┐
│                    Embedding Service                          │
│  OpenAI text-embedding-3-small / Sentence Transformers        │
│  "닭가슴살" → [0.23, 0.81, -0.45, ..., 0.67] (1536 dim)      │
└────────────────────────┬─────────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────────┐
│                Vector Database Search                         │
│  PostgreSQL + pgvector / Pinecone / Qdrant / Milvus          │
│  코사인 유사도로 Top-K 검색 (K=20)                            │
└────────────────────────┬─────────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────────┐
│                  Hybrid Ranking (Optional)                    │
│  • 벡터 검색 결과 (의미론적 유사도)                           │
│  • 키워드 검색 결과 (정확한 매칭)                             │
│  • 사용자 선호도 (최근 기록 식품)                             │
│  → Weighted scoring & re-ranking                              │
└────────────────────────┬─────────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────────┐
│                    Post-filtering                             │
│  • 영양소 필터 (minProtein, maxCalories 등)                   │
│  • 사용자 알러지 제외                                         │
│  • 최종 Top-10 반환                                           │
└────────────────────────┬─────────────────────────────────────┘
                         │
                         ▼
                    최종 결과 반환
```

---

## 3. 구현 옵션 비교

### Option A: PostgreSQL + pgvector (추천)
**복잡도**: ⭐⭐⭐  
**비용**: $0 (기존 DB 활용)  
**성능**: 10K 식품 기준 ~10ms

```sql
-- 1. Extension 설치
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. 벡터 컬럼 추가
ALTER TABLE food_catalog 
ADD COLUMN name_embedding vector(1536);

-- 3. 인덱스 생성
CREATE INDEX ON food_catalog 
USING ivfflat (name_embedding vector_cosine_ops)
WITH (lists = 100);

-- 4. 검색 쿼리
SELECT id, name, name_ko, 
       1 - (name_embedding <=> '[0.23, 0.81, ...]'::vector) as similarity
FROM food_catalog
WHERE deleted_at IS NULL
ORDER BY name_embedding <=> '[0.23, 0.81, ...]'::vector
LIMIT 10;
```

**장점**:
- ✅ 인프라 추가 불필요
- ✅ 트랜잭션 지원
- ✅ 기존 SQL 쿼리와 결합 가능

**단점**:
- ⚠️ 100K+ 규모에서는 전용 벡터 DB보다 느림
- ⚠️ 실시간 인덱싱 부담

---

### Option B: Pinecone (Serverless Vector DB)
**복잡도**: ⭐⭐  
**비용**: 무료 티어 100K vectors, 유료 ~$70/월  
**성능**: 10ms 이하 (최적화됨)

```kotlin
// Kotlin/Spring 예시
@Service
class PineconeFoodSearchService(
    private val pineconeClient: PineconeClient,
    private val openAIClient: OpenAIClient
) {
    suspend fun semanticSearch(query: String, topK: Int = 10): List<FoodItem> {
        // 1. 쿼리 임베딩
        val queryVector = openAIClient.createEmbedding(
            model = "text-embedding-3-small",
            input = query
        ).data[0].embedding
        
        // 2. 벡터 검색
        val results = pineconeClient.query(
            namespace = "food_catalog",
            vector = queryVector,
            topK = topK,
            includeMetadata = true
        )
        
        // 3. DB에서 상세 정보 조회
        val foodIds = results.matches.map { it.id.toLong() }
        return foodRepository.findAllById(foodIds)
    }
}
```

**장점**:
- ✅ 설정 간편 (Managed Service)
- ✅ 높은 성능
- ✅ 실시간 업데이트 용이

**단점**:
- ⚠️ 외부 의존성
- ⚠️ 유료 (스케일에 따라 비용 증가)

---

### Option C: Qdrant (Self-hosted)
**복잡도**: ⭐⭐⭐⭐  
**비용**: $50-100/월 (서버 비용)  
**성능**: Pinecone과 유사

**장점**:
- ✅ 오픈소스
- ✅ 데이터 주권 유지
- ✅ 필터링 성능 우수

**단점**:
- ⚠️ 인프라 관리 필요
- ⚠️ DevOps 부담

---

## 4. 단계별 구현 로드맵

### Phase 1: MVP (2주)
```
1. PostgreSQL + pgvector 설치
2. 기존 식품 데이터 임베딩 생성 (배치 작업)
3. 간단한 벡터 검색 API 구현
4. iOS 앱에서 기존 검색과 병렬 테스트
```

### Phase 2: 하이브리드 검색 (1주)
```
1. 키워드 검색 + 벡터 검색 결합
2. Re-ranking 로직 추가
3. A/B 테스트로 효과 측정
```

### Phase 3: 고급 기능 (2-3주)
```
1. 자연어 쿼리 이해 (LLM)
   - "다이어트 음식" → filters: {maxCalories: 200}
   - "근육 키우기" → filters: {minProtein: 25}

2. 개인화
   - 사용자 최근 기록 기반 랭킹 조정
   - 선호도 학습

3. 멀티모달 (선택)
   - 사진 업로드 → 음식 인식
   - GPT-4 Vision API 활용
```

---

## 5. 비용 추정

### 초기 Setup (1회)
- 기존 50개 식품 임베딩: $0.01 (OpenAI)
- 추가 10K 공공 데이터 임베딩: ~$2

### 운영 비용 (월간)
| 항목 | Option A (pgvector) | Option B (Pinecone) |
|------|---------------------|---------------------|
| 벡터 DB | $0 | $0 (무료 티어) |
| Embedding API | $10-20 | $10-20 |
| 서버 비용 | $0 (기존) | $0 |
| **합계** | **$10-20** | **$10-20** |

*1만 검색/월 기준 (새 식품 추가 시에만 임베딩)*

---

## 6. 예상 효과

### 검색 품질 개선
- 오타 허용: "닭갈비" → "닭가슴살" 매칭
- 유사어 지원: "단백질" → "프로틴 바", "계란", "두부" 모두 검색
- 자연어 쿼리: "저칼로리 간식 추천" 작동

### 사용자 경험
- **평균 검색 시간 30% 감소** (불필요한 재검색 줄어듦)
- **검색 만족도 40% 증가** (첫 검색에서 원하는 결과 발견)
- **이탈률 15% 감소** (식품 기록 완료율 상승)

---

## 7. 리스크 & 완화 전략

### 리스크 1: 임베딩 비용 증가
**완화**: 
- 캐싱 전략 (동일 쿼리 재사용)
- 배치 임베딩 (신규 식품만)

### 리스크 2: 응답 속도 저하
**완화**:
- 벡터 인덱스 최적화
- 검색 결과 캐싱 (Redis)
- 비동기 처리

### 리스크 3: 정확도 문제
**완화**:
- 하이브리드 접근 (키워드 + 벡터)
- 사용자 피드백 수집
- 주기적 모델 파인튜닝

---

## 8. 권장 사항

### 👍 시작하기 좋은 옵션
**PostgreSQL + pgvector + OpenAI Embeddings**

이유:
1. ✅ 기존 인프라 활용 (추가 비용 $0)
2. ✅ 빠른 프로토타이핑 (2주 이내)
3. ✅ 낮은 운영 복잡도
4. ✅ 점진적 개선 가능 (나중에 Pinecone 전환 쉬움)

### 구현 순서
```
Week 1-2:  pgvector MVP
Week 3:    하이브리드 검색
Week 4-5:  A/B 테스트 & 최적화
Week 6+:   자연어 쿼리 (선택)
```

---

## 9. 참고 자료

- [pgvector GitHub](https://github.com/pgvector/pgvector)
- [OpenAI Embeddings Guide](https://platform.openai.com/docs/guides/embeddings)
- [Pinecone Documentation](https://docs.pinecone.io/)
- [Qdrant Documentation](https://qdrant.tech/documentation/)

---

**작성일**: 2026-04-16  
**작성자**: Claude Sonnet 4.5  
**문의**: 추가 구현 세부사항 필요 시 요청
