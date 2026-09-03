package com.team.careerfit.experience.service;

import com.team.careerfit.experience.repository.ExperienceRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExperienceService {

    private final ExperienceRepository experiences;

    public ExperienceService(ExperienceRepository experiences) {
        this.experiences = experiences;
    }

    /** 초안·자소서 답변의 근거 경험이 전부 이 사용자 소유인지 — 다른 도메인은 repository가 아니라 이 서비스로만 확인한다. */
    @Transactional(readOnly = true)
    public boolean allOwnedBy(Long userId, List<Long> experienceIds) {
        Set<Long> distinctIds = new HashSet<>(experienceIds);
        if (distinctIds.isEmpty()) {
            return true;
        }
        return experiences.countByIdInAndUserId(distinctIds, userId) == distinctIds.size();
    }
}
