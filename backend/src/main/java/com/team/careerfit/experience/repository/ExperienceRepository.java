package com.team.careerfit.experience.repository;

import com.team.careerfit.experience.entity.Experience;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExperienceRepository extends JpaRepository<Experience, Long> {

    List<Experience> findByUserIdOrderByStartDateDesc(Long userId);

    @Query("""
            SELECT DISTINCT e FROM Experience e
            JOIN e.competencies ec
            WHERE e.user.id = :userId AND ec.competency.id = :competencyId
            ORDER BY e.startDate DESC
            """)
    List<Experience> findByUserIdAndCompetencyId(@Param("userId") Long userId, @Param("competencyId") Long competencyId);

    /**
     * 답변 근거로 쓰인 경험별 건수. {@code cover_letter_answers.used_experience_ids} 는 coverletter
     * 도메인 소유지만, 그 도메인의 서비스가 아직 없어 엔티티로는 건널 수 없다. 그래서 경험 엔티티를
     * 거치지 않고 원시 SQL로 직접 센다 — N+1 방지 표(문서 "경험 목록" 행)가 정한 3번째 쿼리다.
     */
    @Query(value = """
            SELECT unnest(used_experience_ids) AS experience_id, COUNT(*) AS used_count
            FROM cover_letter_answers
            WHERE user_id = :userId
            GROUP BY experience_id
            """, nativeQuery = true)
    List<UsedCount> countUsedInQuestions(@Param("userId") Long userId);

    interface UsedCount {

        Long getExperienceId();

        Long getUsedCount();
    }

    long countByIdInAndUserId(Collection<Long> ids, Long userId);
}
