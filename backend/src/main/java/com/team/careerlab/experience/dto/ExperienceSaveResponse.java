package com.team.careerlab.experience.dto;

import java.util.List;

/** POST·PUT /api/experiences 공통 응답. 저장 뒤 활성 공고마다 만든 MATCH 작업 목록을 같이 준다. */
public record ExperienceSaveResponse(ExperienceResponse experience, Reassess reassess) {

    public record Reassess(int postingCount, List<Long> taskIds) {
    }
}
