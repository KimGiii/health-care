package com.healthcare.domain.diet.recommendation.engine;

import com.healthcare.domain.diet.entity.DietLog.MealType;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.recommendation.candidate.DietRecommendationCandidate;
import com.healthcare.domain.diet.recommendation.dto.RecommendedFoodEntry;
import com.healthcare.domain.diet.recommendation.dto.RecommendedMeal;
import com.healthcare.domain.nutrition.policy.NutrientBudget;
import com.healthcare.domain.nutrition.policy.RemainingNutritionBudget;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

import static com.healthcare.domain.diet.entity.DietLog.MealType.*;
import static com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory.*;
import static com.healthcare.domain.diet.recommendation.engine.RecommendationFailureReason.*;
import static com.healthcare.domain.diet.recommendation.engine.RecommendationResult.*;

/**
 * 제약 최적화 추천 엔진 (추천 최적화 계획 §8, Phase 3).
 *
 * <p>순수 Java 결정적 beam search로 <b>남은 하루 전체</b>를 목표별 hard constraint
 * ({@link RemainingNutritionBudget})에 맞춰 공동 최적화한다. 외부 솔버 의존성은 없다(ADR-0003).
 *
 * <p>핵심 불변식:
 * <ul>
 *   <li>반환하는 모든 해는 budget hard constraint를 충족한다. 근접 식단을 성공처럼 노출하지 않는다.</li>
 *   <li>같은 {@code foodCatalogId}는 하루 전체에서 최대 1회(끼니 간 중복 hard 금지).</li>
 *   <li>선택된 각 끼니는 최소 1개 식품을 가진다.</li>
 *   <li>동일 입력은 동일 결과(tie-break은 rotationKey·stableKey로 고정).</li>
 * </ul>
 *
 * <p>feasible 해를 찾지 못하면 탐색 한도 도달 여부를 구분해 구조화된 실패를 반환한다.
 */
@Component
public class ConstraintRecommendationEngine {

    private static final Map<MealType, Double> BASE_RATIOS = Map.of(
            BREAKFAST, 0.25,
            LUNCH, 0.35,
            DINNER, 0.30,
            SNACK, 0.10
    );

    private static final Map<MealType, List<FoodCategory>> PREFERRED_CATEGORIES = Map.of(
            BREAKFAST, List.of(GRAIN, DAIRY, FRUIT, PROTEIN_SOURCE),
            LUNCH, List.of(PROTEIN_SOURCE, GRAIN, VEGETABLE),
            DINNER, List.of(PROTEIN_SOURCE, VEGETABLE, GRAIN),
            SNACK, List.of(FRUIT, DAIRY, PROCESSED)
    );

    /** 끼니당 최대 아이템 수 — 실패 사유 재분류 휴리스틱(use case)이 같은 값을 참조한다(드리프트 방지). */
    public static final int MAX_ITEMS_PER_MAIN_MEAL = 3;
    public static final int MAX_ITEMS_PER_SNACK = 2;

    // 탐색 파라미터 (계획 리뷰 P0-2). beam이 feasible 0이면 단계적으로 키운다.
    private static final int[] BEAM_WIDTH_LADDER = {24, 48, 96};
    private static final int[] VARIATIONS_LADDER = {8, 12, 16};
    // 매크로 적합 변형 수: 끼니별 매크로 목표에 가장 가까운 조합 + 날짜 회전 변형 소수.
    private static final int MACRO_FIT_VARIATIONS = 3;
    private static final int MAX_EXPANDED_STATES = 20000;
    private static final double INFINITE_MAX_AIM_RATIO = 1.05;
    // 끼니당 제공량 조합 탐색 상한. 식품별 제공량 옵션의 곱이 이를 넘으면 절단한다.
    private static final int MAX_SERVING_COMBINATIONS = 256;

    /**
     * 남은 하루를 공동 최적화한다.
     *
     * @param eligible      macro 완전성을 갖춘 후보(use case가 사전 필터). 비어 있으면 안 된다.
     * @param maxSolutions  primary 포함 반환할 최대 해 수(1 이상)
     * @param policyVersion 근거에 표기할 정책 버전
     */
    public RecommendationResult recommend(
            LocalDate date,
            RemainingNutritionBudget budget,
            List<MealType> selectedMeals,
            List<DietRecommendationCandidate> eligible,
            UserRepetitionPolicy repetitionPolicy,
            OnlinePreferencePolicy onlinePolicy,
            int maxSolutions,
            String policyVersion
    ) {
        if (!budget.feasible()) {
            return failure(CALORIE_PROTEIN_CONFLICT);
        }
        List<DietRecommendationCandidate> complete = eligible.stream()
                .filter(DietRecommendationCandidate::macroDataComplete)
                .toList();
        if (complete.isEmpty()) {
            return failure(REMAINING_MEALS_INFEASIBLE);
        }

        double aimCalories = aim(budget.calories());

        for (int rung = 0; rung < BEAM_WIDTH_LADDER.length; rung++) {
            SearchRun run = search(date, budget, selectedMeals, complete, aimCalories,
                    repetitionPolicy, onlinePolicy, BEAM_WIDTH_LADDER[rung], VARIATIONS_LADDER[rung]);
            List<SearchState> feasible = run.feasibleStates();
            if (!feasible.isEmpty()) {
                return success(
                        buildSolutions(feasible, budget, repetitionPolicy, onlinePolicy,
                                maxSolutions, policyVersion));
            }
            if (run.truncated()) {
                return failure(SEARCH_LIMIT_REACHED);
            }
        }
        return failure(REMAINING_MEALS_INFEASIBLE);
    }

    // ─── beam search ───

    private SearchRun search(
            LocalDate date,
            RemainingNutritionBudget budget,
            List<MealType> selectedMeals,
            List<DietRecommendationCandidate> complete,
            double aimCalories,
            UserRepetitionPolicy repetitionPolicy,
            OnlinePreferencePolicy onlinePolicy,
            int beamWidth,
            int variations
    ) {
        List<SearchState> beam = List.of(SearchState.empty());
        int expanded = 0;
        boolean truncated = false;
        // 선택된 끼니 비율을 합이 1이 되도록 정규화한다. 그래야 끼니 목표 합이 aim과 같아져
        // 칼로리 하한(atLeast·between)을 채울 수 있다.
        double totalRatio = selectedMeals.stream()
                .mapToDouble(m -> BASE_RATIOS.getOrDefault(m, 0.0)).sum();

        // 끼니별 매크로 목표. 칼로리뿐 아니라 단백·탄수·지방 목표를 향해 후보를 고르면
        // 날짜 시드 운에 기대지 않고 매크로 밴드를 안정적으로 충족한다. 0이면 비제약(무시).
        MealMacroAim macroAim = new MealMacroAim(
                aim(budget.protein()), aim(budget.carbs()), aim(budget.fat()));

        for (MealType mealType : selectedMeals) {
            double mealRatio = totalRatio > 0 ? BASE_RATIOS.getOrDefault(mealType, 0.0) / totalRatio : 0.0;
            double mealCalorieTarget = aimCalories * mealRatio;
            MealMacroAim mealMacroAim = macroAim.scaled(mealRatio);
            List<SearchState> next = new ArrayList<>();
            for (SearchState state : beam) {
                List<List<DietRecommendationCandidate>> fills = mealFillVariations(
                        date, mealType, complete, state.usedFoodIds(),
                        mealCalorieTarget, mealMacroAim, variations);
                for (List<DietRecommendationCandidate> fill : fills) {
                    if (fill.isEmpty()) {
                        continue;
                    }
                    next.add(state.withMeal(buildMeal(mealType, fill, mealCalorieTarget, mealMacroAim)));
                    if (++expanded >= MAX_EXPANDED_STATES) {
                        truncated = true;
                        break;
                    }
                }
                if (truncated) {
                    break;
                }
            }
            if (next.isEmpty()) {
                // 이 끼니에 넣을 후보가 고갈됨(중복 hard 제약). 더 진행 불가.
                return new SearchRun(List.of(), truncated);
            }
            beam = pruneBeam(next, aimCalories, totalRatio, repetitionPolicy, onlinePolicy, beamWidth);
            if (truncated) {
                break;
            }
        }

        boolean allMealsBuilt = !truncated;
        List<SearchState> feasible = beam.stream()
                .filter(state -> allMealsBuilt && state.meals().size() == selectedMeals.size())
                .filter(state -> satisfies(budget, state))
                .toList();
        return new SearchRun(feasible, truncated);
    }

    /**
     * 한 끼니에 대한 서로 다른 식품 조합 변형들을 생성한다. 이미 쓴 foodId는 hard 제외한다.
     * 변형은 선호 카테고리 선택의 시작 오프셋을 돌려 다양성을 확보하되 결정적이다.
     */
    private List<List<DietRecommendationCandidate>> mealFillVariations(
            LocalDate date,
            MealType mealType,
            List<DietRecommendationCandidate> complete,
            Set<Long> usedFoodIds,
            double mealCalorieTarget,
            MealMacroAim mealMacroAim,
            int variations
    ) {
        List<DietRecommendationCandidate> pool = complete.stream()
                .filter(food -> !usedFoodIds.contains(food.foodCatalogId()))
                .sorted(orderingFor(date))
                .toList();
        if (pool.isEmpty()) {
            return List.of();
        }
        int maxItems = mealType == SNACK ? MAX_ITEMS_PER_SNACK : MAX_ITEMS_PER_MAIN_MEAL;
        List<FoodCategory> preferred = PREFERRED_CATEGORIES.getOrDefault(mealType, List.of());

        List<List<DietRecommendationCandidate>> fills = new ArrayList<>();
        Set<List<Long>> seen = new HashSet<>();

        // 1) 매크로 스패닝 변형: 단백·탄수·지방 각각 밀도 높은 식품을 한 개씩 모은다.
        //    배분기(allocateServings)가 매크로별로 독립 조정할 지렛대를 확보해 밴드 도달을 보장한다.
        addFill(fills, seen, macroSpanningFill(pool, maxItems, mealMacroAim));

        // 2) 매크로 적합 변형: 끼니 매크로 목표 비율에 가까운 식품을 카테고리별로 골라 둔다.
        List<DietRecommendationCandidate> macroOrdered = pool.stream()
                .sorted(macroFitOrdering(date, mealMacroAim))
                .toList();
        for (int offset = 0; offset < MACRO_FIT_VARIATIONS && fills.size() < variations; offset++) {
            addFill(fills, seen, selectItems(macroOrdered, preferred, maxItems, offset));
        }

        // 2) 기존 다양성 변형: 날짜 회전 정렬에서 아이템 수·오프셋을 변형한다.
        //    작은 끼니도 만들어 두어야 후보가 적을 때 뒤 끼니가 굶지 않는다(분배 가능성 확보).
        for (int itemCount = maxItems; itemCount >= 1 && fills.size() < variations; itemCount--) {
            for (int offset = 0; offset < variations && fills.size() < variations; offset++) {
                addFill(fills, seen, selectItems(pool, preferred, itemCount, offset));
            }
        }
        return fills;
    }

    /**
     * 제약 매크로별로 밀도가 가장 높은 식품을 한 개씩 모아 배분기에 독립 조정 지렛대를 준다.
     * 비제약 매크로는 건너뛰고, 남는 슬롯은 칼로리 밀도순으로 채운다.
     */
    private List<DietRecommendationCandidate> macroSpanningFill(
            List<DietRecommendationCandidate> pool, int maxItems, MealMacroAim aim) {
        List<DietRecommendationCandidate> selected = new ArrayList<>();
        Set<Long> usedIds = new HashSet<>();

        List<ToDoubleFunction<DietRecommendationCandidate>> levers = new ArrayList<>();
        if (aim.proteinAim() > 0) {
            levers.add(DietRecommendationCandidate::proteinPer100g);
        }
        if (aim.carbAim() > 0) {
            levers.add(DietRecommendationCandidate::carbsPer100g);
        }
        if (aim.fatAim() > 0) {
            levers.add(DietRecommendationCandidate::fatPer100g);
        }
        if (levers.isEmpty()) {
            levers.add(DietRecommendationCandidate::caloriesPer100g);
        }

        for (var lever : levers) {
            if (selected.size() >= maxItems) {
                break;
            }
            pickTopBy(pool, lever, usedIds).ifPresent(food -> {
                selected.add(food);
                usedIds.add(food.foodCatalogId());
            });
        }
        // 남는 슬롯은 칼로리 밀도순으로 보충(끼니 칼로리 목표 도달 여력 확보).
        while (selected.size() < maxItems) {
            var next = pickTopBy(pool, DietRecommendationCandidate::caloriesPer100g, usedIds);
            if (next.isEmpty()) {
                break;
            }
            selected.add(next.get());
            usedIds.add(next.get().foodCatalogId());
        }
        return selected;
    }

    private java.util.Optional<DietRecommendationCandidate> pickTopBy(
            List<DietRecommendationCandidate> pool,
            ToDoubleFunction<DietRecommendationCandidate> density,
            Set<Long> usedIds) {
        return pool.stream()
                .filter(food -> !usedIds.contains(food.foodCatalogId()))
                .max(Comparator
                        .comparingDouble(density)
                        .thenComparing(Comparator.comparingLong(DietRecommendationCandidate::stableKey).reversed()));
    }

    private void addFill(
            List<List<DietRecommendationCandidate>> fills,
            Set<List<Long>> seen,
            List<DietRecommendationCandidate> fill) {
        if (fill.isEmpty()) {
            return;
        }
        List<Long> key = fill.stream()
                .map(DietRecommendationCandidate::foodCatalogId).sorted().toList();
        if (seen.add(key)) {
            fills.add(fill);
        }
    }

    /**
     * 선호 카테고리에서 1개씩, 부족하면 다른 카테고리·잔여에서 채워 maxItems개를 고른다.
     * offset만큼 각 카테고리의 시작 후보를 회전시켜 변형을 만든다(결정적).
     */
    private List<DietRecommendationCandidate> selectItems(
            List<DietRecommendationCandidate> pool,
            List<FoodCategory> preferred,
            int maxItems,
            int offset
    ) {
        List<DietRecommendationCandidate> selected = new ArrayList<>();
        Set<FoodCategory> usedCategories = new HashSet<>();
        Set<Long> usedIds = new HashSet<>();

        for (FoodCategory category : preferred) {
            if (selected.size() >= maxItems) {
                break;
            }
            pickFromCategory(pool, category, offset, usedIds)
                    .ifPresent(food -> {
                        selected.add(food);
                        usedCategories.add(food.category());
                        usedIds.add(food.foodCatalogId());
                    });
        }
        // 새 카테고리 우선 보충
        fillRemaining(pool, selected, usedIds, usedCategories, maxItems, true);
        // 그래도 부족하면 카테고리 무관 보충
        fillRemaining(pool, selected, usedIds, usedCategories, maxItems, false);
        return selected;
    }

    private java.util.Optional<DietRecommendationCandidate> pickFromCategory(
            List<DietRecommendationCandidate> pool, FoodCategory category, int offset, Set<Long> usedIds) {
        List<DietRecommendationCandidate> inCategory = pool.stream()
                .filter(food -> food.category() == category && !usedIds.contains(food.foodCatalogId()))
                .toList();
        if (inCategory.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(inCategory.get(offset % inCategory.size()));
    }

    private void fillRemaining(
            List<DietRecommendationCandidate> pool,
            List<DietRecommendationCandidate> selected,
            Set<Long> usedIds,
            Set<FoodCategory> usedCategories,
            int maxItems,
            boolean newCategoryOnly
    ) {
        for (DietRecommendationCandidate food : pool) {
            if (selected.size() >= maxItems) {
                return;
            }
            if (usedIds.contains(food.foodCatalogId())) {
                continue;
            }
            if (newCategoryOnly && usedCategories.contains(food.category())) {
                continue;
            }
            selected.add(food);
            usedIds.add(food.foodCatalogId());
            usedCategories.add(food.category());
        }
    }

    private RecommendedMeal buildMeal(
            MealType mealType, List<DietRecommendationCandidate> foods,
            double mealCalorieTarget, MealMacroAim mealMacroAim) {
        // 제공량은 각 식품의 검증된 1회 제공량 옵션(0.5/1/1.5/2회 등)에서만 고른다(§7.1).
        // 옵션 조합을 전수 탐색해 칼로리·제약 매크로 목표에 가장 가까운 조합을 선택한다.
        List<DietRecommendationCandidate> ordered = foods.stream()
                .sorted(Comparator
                        .comparingDouble(DietRecommendationCandidate::caloriesPer100g)
                        .thenComparingLong(DietRecommendationCandidate::stableKey))
                .toList();
        double[] grams = chooseServingCombination(ordered, mealCalorieTarget, mealMacroAim);

        List<RecommendedFoodEntry> items = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            items.add(RecommendedFoodEntry.from(ordered.get(i), grams[i]));
        }
        double cal = items.stream().mapToDouble(RecommendedFoodEntry::calories).sum();
        double protein = items.stream().mapToDouble(RecommendedFoodEntry::proteinG).sum();
        double carbs = items.stream().mapToDouble(RecommendedFoodEntry::carbsG).sum();
        double fat = items.stream().mapToDouble(RecommendedFoodEntry::fatG).sum();
        return new RecommendedMeal(mealType, round(mealCalorieTarget), round(cal),
                round(protein), round(carbs), round(fat), items);
    }

    /**
     * 식품별 제공량 옵션의 조합을 탐색해 끼니 목표(칼로리 + 제약 매크로)에 가장 가까운 제공량을 고른다.
     * 제공량은 검증된 1회 제공량 옵션에서만 고르므로 비현실적 분량(예: 과일 500g)이 나오지 않는다.
     */
    private double[] chooseServingCombination(
            List<DietRecommendationCandidate> foods, double calTarget, MealMacroAim aim) {
        int n = foods.size();
        List<List<Double>> optionsPerFood = new ArrayList<>(n);
        long combinations = 1;
        for (DietRecommendationCandidate food : foods) {
            List<Double> options = food.verifiedServingGramOptions();
            if (options.isEmpty()) {
                // 제공량 근거가 없는 식품(추천 풀 필터를 우회한 경우)의 안전망: 칼로리 기준 단일 추정.
                options = List.of(food.chooseServingGramsFor(calTarget / n));
            }
            optionsPerFood.add(options);
            combinations *= options.size();
        }

        double[] best = new double[n];
        double bestDistance = Double.MAX_VALUE;
        if (combinations <= MAX_SERVING_COMBINATIONS) {
            int[] index = new int[n];
            do {
                double[] grams = pick(optionsPerFood, index);
                double distance = mealDistance(foods, grams, calTarget, aim);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = grams;
                }
            } while (advance(index, optionsPerFood));
            return best;
        }
        // 조합이 너무 많으면 식품별로 독립 최적 옵션을 고른다(탐욕적 절단).
        for (int i = 0; i < n; i++) {
            best[i] = optionsPerFood.get(i).get(optionsPerFood.get(i).size() / 2);
        }
        return best;
    }

    private double[] pick(List<List<Double>> optionsPerFood, int[] index) {
        double[] grams = new double[index.length];
        for (int i = 0; i < index.length; i++) {
            grams[i] = optionsPerFood.get(i).get(index[i]);
        }
        return grams;
    }

    private boolean advance(int[] index, List<List<Double>> optionsPerFood) {
        for (int i = index.length - 1; i >= 0; i--) {
            if (index[i] + 1 < optionsPerFood.get(i).size()) {
                index[i]++;
                return true;
            }
            index[i] = 0;
        }
        return false;
    }

    /**
     * 제공량 조합의 끼니 합계가 목표(칼로리 + 제약 매크로)에서 벗어난 정규화 거리.
     */
    private double mealDistance(
            List<DietRecommendationCandidate> foods, double[] grams,
            double calTarget, MealMacroAim aim) {
        double cal = 0;
        double protein = 0;
        double carbs = 0;
        double fat = 0;
        for (int i = 0; i < foods.size(); i++) {
            double factor = grams[i] / 100.0;
            DietRecommendationCandidate food = foods.get(i);
            cal += food.caloriesPer100g() * factor;
            protein += food.proteinPer100g() * factor;
            carbs += food.carbsPer100g() * factor;
            fat += food.fatPer100g() * factor;
        }
        double distance = relativeGap(cal, calTarget);
        if (aim.proteinAim() > 0) {
            distance += relativeGap(protein, aim.proteinAim());
        }
        if (aim.carbAim() > 0) {
            distance += relativeGap(carbs, aim.carbAim());
        }
        if (aim.fatAim() > 0) {
            distance += relativeGap(fat, aim.fatAim());
        }
        return distance;
    }

    private double relativeGap(double value, double target) {
        return target > 0 ? Math.abs(value - target) / target : 0;
    }

    // ─── 점수화·선별 ───

    private List<SearchState> pruneBeam(
            List<SearchState> states,
            double aimCalories,
            double totalRatio,
            UserRepetitionPolicy repetitionPolicy,
            OnlinePreferencePolicy onlinePolicy,
            int beamWidth
    ) {
        return states.stream()
                .sorted(Comparator
                        .comparingDouble((SearchState s) ->
                                partialScore(s, aimCalories, totalRatio, repetitionPolicy, onlinePolicy))
                        .thenComparingLong(SearchState::stableSignature))
                .limit(beamWidth)
                .toList();
    }

    /**
     * 부분-하루 상태 점수. 지금까지 만든 끼니의 누적 목표 대비 진행도를 본다(myopic 과적합 방지).
     * 누적 칼로리가 끼니 비율 합에 맞을수록 좋고, 그래야 뒤 끼니에 후보·여유가 남는다.
     */
    private double partialScore(
            SearchState state,
            double aimCalories,
            double totalRatio,
            UserRepetitionPolicy repetitionPolicy,
            OnlinePreferencePolicy onlinePolicy
    ) {
        double cumulativeRatio = totalRatio > 0
                ? state.meals().stream()
                .mapToDouble(m -> BASE_RATIOS.getOrDefault(m.mealType(), 0.0)).sum() / totalRatio
                : 0.0;
        double cumulativeAim = aimCalories * cumulativeRatio;
        double scale = aimCalories > 0 ? aimCalories : 1.0;
        double calorieProgress = Math.abs(state.calories() - cumulativeAim) / scale;
        double repetition = state.foods().stream()
                .mapToDouble(item -> repetitionPolicy.penaltyScore(item.foodCatalogId())).sum();
        double online = state.foods().stream()
                .mapToDouble(item -> onlinePolicy.penaltyScore(item.foodCatalogId())).sum();
        double proteinOccupancy = proteinSourceOccupancyPenalty(state);
        return calorieProgress + 0.3 * repetition + 0.3 * online + 0.2 * proteinOccupancy;
    }

    private List<RecommendationSolution> buildSolutions(
            List<SearchState> feasible,
            RemainingNutritionBudget budget,
            UserRepetitionPolicy repetitionPolicy,
            OnlinePreferencePolicy onlinePolicy,
            int maxSolutions,
            String policyVersion
    ) {
        List<SearchState> ranked = feasible.stream()
                .sorted(Comparator
                        .comparingDouble((SearchState s) ->
                                softScore(s, budget, repetitionPolicy, onlinePolicy))
                        .thenComparingLong(SearchState::stableSignature))
                .toList();

        SearchState primary = ranked.get(0);
        List<SearchState> ordered = new ArrayList<>();
        ordered.add(primary);
        // 다양성: primary 대비 새 카테고리가 많은 해를 대안 상위로(§8.2)
        Set<FoodCategory> primaryCategories = primary.categories();
        ranked.stream()
                .skip(1)
                .sorted(Comparator
                        .comparingLong((SearchState s) -> newCategoryCount(s, primaryCategories))
                        .reversed()
                        .thenComparingLong(SearchState::stableSignature))
                .forEach(ordered::add);

        return ordered.stream()
                .limit(Math.max(1, maxSolutions))
                .map(state -> new RecommendationSolution(
                        state.meals(), rationale(state, budget, policyVersion)))
                .toList();
    }

    private double softScore(
            SearchState state,
            RemainingNutritionBudget budget,
            UserRepetitionPolicy repetitionPolicy,
            OnlinePreferencePolicy onlinePolicy
    ) {
        double centerDistance = normalizedDistance(budget.calories(), state.calories())
                + normalizedDistance(budget.protein(), state.protein())
                + normalizedDistance(budget.carbs(), state.carbs())
                + normalizedDistance(budget.fat(), state.fat());
        double mealVariance = mealCalorieVariance(state);
        double repetition = state.foods().stream()
                .mapToDouble(item -> repetitionPolicy.penaltyScore(item.foodCatalogId())).sum();
        double online = state.foods().stream()
                .mapToDouble(item -> onlinePolicy.penaltyScore(item.foodCatalogId())).sum();
        double proteinOccupancy = proteinSourceOccupancyPenalty(state);
        return centerDistance + 0.5 * mealVariance + 0.3 * repetition + 0.3 * online + 0.4 * proteinOccupancy;
    }

    /**
     * 같은 단백질 소스 카테고리가 여러 끼니를 점유할수록 penalty(soft).
     */
    private double proteinSourceOccupancyPenalty(SearchState state) {
        long proteinMeals = state.meals().stream()
                .filter(meal -> meal.items().stream()
                        .anyMatch(item -> item.category() == PROTEIN_SOURCE))
                .count();
        return Math.max(0, proteinMeals - 1);
    }

    private double mealCalorieVariance(SearchState state) {
        List<Double> calories = state.meals().stream().map(RecommendedMeal::totalCalories).toList();
        if (calories.size() <= 1) {
            return 0.0;
        }
        double mean = calories.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        return calories.stream().mapToDouble(c -> Math.abs(c - mean)).sum() / mean;
    }

    private long newCategoryCount(SearchState state, Set<FoodCategory> baseline) {
        return state.categories().stream().filter(c -> !baseline.contains(c)).count();
    }

    // ─── budget 판정·근거 ───

    private boolean satisfies(RemainingNutritionBudget budget, SearchState state) {
        return within(budget.calories(), state.calories())
                && within(budget.protein(), state.protein())
                && within(budget.carbs(), state.carbs())
                && within(budget.fat(), state.fat());
    }

    private boolean within(NutrientBudget b, double value) {
        return value >= b.minimum() && value <= b.maximum();
    }

    private double normalizedDistance(NutrientBudget b, double value) {
        double center = center(b);
        double scale = center > 0 ? center : 1.0;
        return Math.abs(value - center) / scale;
    }

    /**
     * 끼니 분배의 기준 칼로리. feasible 밴드 안쪽을 겨냥한다.
     * 상한만 있으면(atMost) 상한 근처를 채우고, 하한만 있으면(atLeast) 하한 위로 안전하게 둔다.
     */
    private double aim(NutrientBudget b) {
        if (Double.isInfinite(b.maximum())) {
            return b.minimum() * INFINITE_MAX_AIM_RATIO;
        }
        if (b.minimum() <= 0) {
            return b.maximum() * 0.9;
        }
        return center(b);
    }

    private double center(NutrientBudget b) {
        return Double.isInfinite(b.maximum()) ? b.minimum() : (b.minimum() + b.maximum()) / 2.0;
    }

    private RecommendationRationale rationale(
            SearchState state, RemainingNutritionBudget budget, String policyVersion) {
        Double headroom = Double.isInfinite(budget.calories().maximum())
                ? null : round(budget.calories().maximum() - state.calories());
        Double proteinGap = budget.protein().minimum() <= 0
                ? null : round(state.protein() - budget.protein().minimum());
        String note = String.format(
                "남은 목표 중 열량 %.0fkcal·단백질 %.0fg를 채웠습니다. 등록한 조건과 검증된 식품 정보를 기준으로 구성했습니다.",
                state.calories(), state.protein());
        return new RecommendationRationale(
                round(state.calories()), round(state.protein()), round(state.carbs()),
                headroom, proteinGap, policyVersion, note);
    }

    private Comparator<DietRecommendationCandidate> orderingFor(LocalDate date) {
        return Comparator
                .comparingLong((DietRecommendationCandidate f) -> rotationKey(date, f))
                .thenComparing(Comparator.comparingLong(DietRecommendationCandidate::usageCount).reversed())
                .thenComparingLong(DietRecommendationCandidate::stableKey);
    }

    /**
     * 끼니 매크로 목표에 대한 식품 적합도 순 정렬. 비제약(aim 0) 차원은 무시한다.
     * 적합도가 비슷하면 날짜 회전으로 tie-break해 일자별 다양성을 유지한다.
     */
    private Comparator<DietRecommendationCandidate> macroFitOrdering(LocalDate date, MealMacroAim aim) {
        if (!aim.hasAnyConstraint()) {
            // 매크로 비제약 끼니: 칼로리 밀도 높은 식품 우선(칼로리 floor 도달 보장) + 날짜 회전.
            return Comparator
                    .comparingDouble((DietRecommendationCandidate f) -> -f.caloriesPer100g())
                    .thenComparingLong(f -> rotationKey(date, f));
        }
        return Comparator
                .comparingDouble((DietRecommendationCandidate f) -> macroFitDistance(f, aim))
                .thenComparingLong(f -> rotationKey(date, f));
    }

    /**
     * 식품의 매크로 칼로리 구성비가 목표 구성비에서 벗어난 정도(제약 차원만 합산).
     */
    private double macroFitDistance(DietRecommendationCandidate food, MealMacroAim aim) {
        double foodProteinCal = food.proteinPer100g() * 4.0;
        double foodCarbCal = food.carbsPer100g() * 4.0;
        double foodFatCal = food.fatPer100g() * 9.0;
        double foodTotal = foodProteinCal + foodCarbCal + foodFatCal;
        if (foodTotal <= 0) {
            return Double.MAX_VALUE;
        }
        double aimTotal = aim.constrainedMacroCalories();
        double distance = 0.0;
        if (aim.proteinAim() > 0) {
            distance += Math.abs(foodProteinCal / foodTotal - aim.proteinAim() * 4.0 / aimTotal);
        }
        if (aim.carbAim() > 0) {
            distance += Math.abs(foodCarbCal / foodTotal - aim.carbAim() * 4.0 / aimTotal);
        }
        if (aim.fatAim() > 0) {
            distance += Math.abs(foodFatCal / foodTotal - aim.fatAim() * 9.0 / aimTotal);
        }
        return distance;
    }

    /**
     * 끼니별 매크로 목표(g). 값이 0이면 해당 매크로는 비제약.
     */
    private record MealMacroAim(double proteinAim, double carbAim, double fatAim) {
        MealMacroAim scaled(double ratio) {
            return new MealMacroAim(proteinAim * ratio, carbAim * ratio, fatAim * ratio);
        }

        boolean hasAnyConstraint() {
            return proteinAim > 0 || carbAim > 0 || fatAim > 0;
        }

        double constrainedMacroCalories() {
            double total = 0.0;
            if (proteinAim > 0) {
                total += proteinAim * 4.0;
            }
            if (carbAim > 0) {
                total += carbAim * 4.0;
            }
            if (fatAim > 0) {
                total += fatAim * 9.0;
            }
            return total > 0 ? total : 1.0;
        }
    }

    private long rotationKey(LocalDate date, DietRecommendationCandidate food) {
        long value = date.toEpochDay() ^ (food.stableKey() * 0x9E3779B97F4A7C15L);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    // ─── 내부 상태 ───

    private record SearchRun(List<SearchState> feasibleStates, boolean truncated) {
    }

    private record SearchState(
            Set<Long> usedFoodIds,
            List<RecommendedMeal> meals,
            double calories,
            double protein,
            double carbs,
            double fat
    ) {
        static SearchState empty() {
            return new SearchState(Set.of(), List.of(), 0, 0, 0, 0);
        }

        SearchState withMeal(RecommendedMeal meal) {
            Set<Long> used = new HashSet<>(usedFoodIds);
            meal.items().forEach(item -> used.add(item.foodCatalogId()));
            List<RecommendedMeal> nextMeals = new ArrayList<>(meals);
            nextMeals.add(meal);
            return new SearchState(
                    Set.copyOf(used),
                    List.copyOf(nextMeals),
                    calories + meal.totalCalories(),
                    protein + meal.totalProteinG(),
                    carbs + meal.totalCarbsG(),
                    fat + meal.totalFatG());
        }

        List<RecommendedFoodEntry> foods() {
            return meals.stream().flatMap(m -> m.items().stream()).toList();
        }

        Set<FoodCategory> categories() {
            Set<FoodCategory> categories = new HashSet<>();
            meals.forEach(m -> m.items().forEach(i -> categories.add(i.category())));
            return categories;
        }

        /**
         * 결정적 tie-break용 안정 서명(사용된 foodId 정렬 해시).
         */
        long stableSignature() {
            long signature = 1L;
            List<Long> ids = usedFoodIds.stream().sorted().toList();
            for (Long id : ids) {
                signature = signature * 1000003L + id;
            }
            return signature;
        }
    }
}
