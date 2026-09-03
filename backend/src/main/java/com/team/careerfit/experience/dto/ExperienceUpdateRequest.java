package com.team.careerfit.experience.dto;

import com.team.careerfit.experience.entity.ExperienceCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** POST 와 같은 모양이지만 출처(aiTaskId)는 수정으로 바뀌지 않아 intakeTaskId 가 없다. */
public record ExperienceUpdateRequest(
        @NotBlank(message = "제목을 입력해 주세요.") String title,
        @NotNull(message = "분류를 선택해 주세요.") ExperienceCategory category,
        LocalDate startDate,
        LocalDate endDate,
        String situation,
        String task,
        String action,
        @NotBlank(message = "결과를 입력해 주세요.") String result,
        @NotEmpty(message = "역량을 1개 이상 선택해 주세요.") @Valid List<CompetencyStrength> competencies) {

    public record CompetencyStrength(
            @NotNull(message = "역량을 선택해 주세요.") Long competencyId,
            @NotNull(message = "강도를 입력해 주세요.")
            @DecimalMin(value = "0", message = "강도는 0 이상이어야 합니다.")
            @DecimalMax(value = "1", message = "강도는 1 이하여야 합니다.") BigDecimal strength) {
    }
}
