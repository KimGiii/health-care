package com.healthcare.domain.exercise.service;

import com.healthcare.domain.exercise.dto.CatalogSearchParams;
import com.healthcare.domain.exercise.dto.CreateCustomExerciseRequest;
import com.healthcare.domain.exercise.dto.ExerciseCatalogResponse;
import com.healthcare.domain.exercise.entity.ExerciseCatalog;
import com.healthcare.domain.exercise.repository.ExerciseCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExerciseCatalogService {

    private final ExerciseCatalogRepository catalogRepository;

    public List<ExerciseCatalogResponse> searchCatalog(Long userId, CatalogSearchParams params) {
        return catalogRepository
                .findAccessibleToUser(userId, params.getQuery(),
                        params.getExerciseType(), params.getMuscleGroup(), params.isCustomOnly())
                .stream()
                .map(ExerciseCatalogResponse::from)
                .toList();
    }

    @Transactional
    public ExerciseCatalogResponse createCustomExercise(Long userId, CreateCustomExerciseRequest request) {
        ExerciseCatalog catalog = ExerciseCatalog.builder()
                .name(request.getName())
                .nameKo(request.getNameKo())
                .muscleGroup(request.getMuscleGroup())
                .exerciseType(request.getExerciseType())
                .metValue(request.getMetValue())
                .isCustom(true)
                .createdByUserId(userId)
                .build();

        ExerciseCatalog saved = catalogRepository.save(catalog);
        return ExerciseCatalogResponse.from(saved);
    }
}
