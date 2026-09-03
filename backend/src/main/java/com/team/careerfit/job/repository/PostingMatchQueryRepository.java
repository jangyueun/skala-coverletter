package com.team.careerfit.job.repository;

import com.team.careerfit.aitask.entity.AiTaskStatus;
import com.team.careerfit.competency.entity.CompetencyCategory;
import com.team.careerfit.matching.entity.MatchVerdict;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PostingMatchQueryRepository {

    private final JdbcClient jdbcClient;

    public PostingMatchQueryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public boolean postingExists(Long postingId) {
        return jdbcClient.sql("select exists(select 1 from job_postings where id = :postingId)")
                .param("postingId", postingId)
                .query(Boolean.class)
                .single();
    }

    public List<Requirement> findRequirements(Long postingId) {
        return jdbcClient.sql("""
                        select competency.id, competency.name, competency.category,
                               required.weight, required.evidence_line
                        from posting_competencies required
                        join competencies competency on competency.id = required.competency_id
                        where required.job_posting_id = :postingId
                        order by competency.id
                        """)
                .param("postingId", postingId)
                .query((resultSet, rowNumber) -> new Requirement(
                        resultSet.getLong("id"),
                        resultSet.getString("name"),
                        CompetencyCategory.valueOf(resultSet.getString("category")),
                        resultSet.getBigDecimal("weight"),
                        resultSet.getString("evidence_line")))
                .list();
    }

    public List<ExperienceInput> findExperienceInputs(Long userId) {
        return jdbcClient.sql("""
                        select experience.id as experience_id,
                               competency.competency_id,
                               competency.strength
                        from experiences experience
                        join experience_competencies competency
                          on competency.experience_id = experience.id
                        where experience.user_id = :userId
                        order by experience.id, competency.competency_id
                        """)
                .param("userId", userId)
                .query((resultSet, rowNumber) -> new ExperienceInput(
                        resultSet.getLong("experience_id"),
                        resultSet.getLong("competency_id"),
                        resultSet.getBigDecimal("strength")))
                .list();
    }

    public Optional<StoredMatch> findMatch(Long postingId, Long userId) {
        return jdbcClient.sql("""
                        select match_score, verdict, covered_count, coverage, input_hash, updated_at
                        from job_matches
                        where user_id = :userId and job_posting_id = :postingId
                        """)
                .param("userId", userId)
                .param("postingId", postingId)
                .query((resultSet, rowNumber) -> new StoredMatch(
                        resultSet.getBigDecimal("match_score"),
                        MatchVerdict.valueOf(resultSet.getString("verdict")),
                        resultSet.getInt("covered_count"),
                        resultSet.getString("coverage"),
                        resultSet.getString("input_hash"),
                        resultSet.getTimestamp("updated_at").toInstant()))
                .optional();
    }

    public Optional<Task> findLatestTask(Long postingId, Long userId, String inputHash) {
        return jdbcClient.sql("""
                        select id, status
                        from ai_tasks
                        where task_type = 'MATCH'
                          and user_id = :userId
                          and job_posting_id = :postingId
                          and input_hash = :inputHash
                        order by created_at desc, id desc
                        limit 1
                        """)
                .param("userId", userId)
                .param("postingId", postingId)
                .param("inputHash", inputHash)
                .query((resultSet, rowNumber) -> new Task(
                        resultSet.getLong("id"),
                        AiTaskStatus.valueOf(resultSet.getString("status"))))
                .optional();
    }

    public Long createTask(
            Long postingId,
            Long userId,
            String idempotencyKey,
            String inputHash,
            String requestPayload) {
        Optional<Long> created = jdbcClient.sql("""
                        insert into ai_tasks
                            (task_type, status, user_id, job_posting_id, idempotency_key,
                             input_hash, request_payload, retry_count, attempts, created_at)
                        values ('MATCH', 'PENDING', :userId, :postingId, :idempotencyKey,
                                :inputHash, cast(:requestPayload as jsonb), 0, '[]', now())
                        on conflict (idempotency_key) do nothing
                        returning id
                        """)
                .param("userId", userId)
                .param("postingId", postingId)
                .param("idempotencyKey", idempotencyKey)
                .param("inputHash", inputHash)
                .param("requestPayload", requestPayload)
                .query(Long.class)
                .optional();
        return created.orElseGet(() -> findLatestTask(postingId, userId, inputHash)
                .orElseThrow()
                .id());
    }

    public List<ExperienceView> findExperiences(Long userId, Collection<Long> experienceIds) {
        if (experienceIds.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql("""
                        select id, title, result
                        from experiences
                        where user_id = :userId and id in (:experienceIds)
                        """)
                .param("userId", userId)
                .param("experienceIds", experienceIds)
                .query((resultSet, rowNumber) -> new ExperienceView(
                        resultSet.getLong("id"),
                        resultSet.getString("title"),
                        resultSet.getString("result")))
                .list();
    }

    public record Requirement(
            Long competencyId,
            String name,
            CompetencyCategory category,
            BigDecimal weight,
            String evidenceLine) {
    }

    public record ExperienceInput(Long experienceId, Long competencyId, BigDecimal strength) {
    }

    public record StoredMatch(
            BigDecimal matchScore,
            MatchVerdict verdict,
            int coveredCount,
            String coverage,
            String inputHash,
            Instant updatedAt) {
    }

    public record Task(Long id, AiTaskStatus status) {
    }

    public record ExperienceView(Long id, String title, String result) {
    }
}
