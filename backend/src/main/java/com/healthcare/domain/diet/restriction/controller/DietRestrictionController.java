package com.healthcare.domain.diet.restriction.controller;

import com.healthcare.security.CurrentUserId;
import com.healthcare.domain.diet.restriction.dto.CreateDietRestrictionRequest;
import com.healthcare.domain.diet.restriction.dto.DietRestrictionResponse;
import com.healthcare.domain.diet.restriction.usecase.DietRestrictionUseCases;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/diet/restrictions")
@RequiredArgsConstructor
public class DietRestrictionController {

    private final DietRestrictionUseCases dietRestrictionUseCases;

    @GetMapping
    public List<DietRestrictionResponse> listRestrictions(@CurrentUserId Long userId) {
        return dietRestrictionUseCases.listRestrictions(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DietRestrictionResponse createRestriction(
            @CurrentUserId Long userId,
            @Valid @RequestBody CreateDietRestrictionRequest request) {
        return dietRestrictionUseCases.createRestriction(userId, request);
    }

    @DeleteMapping("/{restrictionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRestriction(
            @CurrentUserId Long userId,
            @PathVariable Long restrictionId) {
        dietRestrictionUseCases.deleteRestriction(userId, restrictionId);
    }
}
