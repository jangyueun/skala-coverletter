package com.team.careerfit.job.repository;

import com.team.careerfit.competency.entity.CompetencyCategory;
import com.team.careerfit.job.dto.PostingDetailResponse;
import com.team.careerfit.job.dto.PostingDetailResponse.Related;
import com.team.careerfit.job.dto.PostingDetailResponse.RequiredCompetency;
import com.team.careerfit.job.dto.PostingDetailResponse.SameCompany;
import com.team.careerfit.job.dto.PostingDetailResponse.Similar;
import com.team.careerfit.job.entity.PostingStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JobPostingDetailQueryRepository {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final JdbcClient jdbcClient;

    public JobPostingDetailQueryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<PostingDetailResponse> findById(Long postingId, Long userId) {
        Optional<BaseRow> base = jdbcClient.sql("""
                        select posting.id, posting.company_id, company.name as company_name,
                               posting.position, posting.deadline, posting.status,
                               posting.source_url, posting.content,
                               exists (
                                   select 1 from bookmarks bookmark
                                   where bookmark.user_id = :userId
                                     and bookmark.job_posting_id = posting.id
                               ) as bookmarked
                        from job_postings posting
                        join companies company on company.id = posting.company_id
                        where posting.id = :postingId
                        """)
                .param("userId", userId)
                .param("postingId", postingId)
                .query(this::mapBase)
                .optional();
        if (base.isEmpty()) {
            return Optional.empty();
        }

        BaseRow row = base.get();
        List<RequiredCompetency> required = findRequired(postingId);
        Related related = new Related(
                findSameCompany(postingId, row.companyId(), userId),
                findSimilar(postingId, userId));
        return Optional.of(new PostingDetailResponse(
                row.id(),
                row.company(),
                row.position(),
                row.deadline().toInstant().atZone(KOREA).toOffsetDateTime(),
                PostingStatus.valueOf(row.status()),
                row.sourceUrl(),
                row.content(),
                row.bookmarked(),
                required,
                related));
    }

    private List<RequiredCompetency> findRequired(Long postingId) {
        return jdbcClient.sql("""
                        select competency.id, competency.name, competency.category,
                               required.weight, required.evidence_line
                        from posting_competencies required
                        join competencies competency on competency.id = required.competency_id
                        where required.job_posting_id = :postingId
                        order by required.weight desc, competency.name
                        """)
                .param("postingId", postingId)
                .query((resultSet, rowNumber) -> new RequiredCompetency(
                        resultSet.getLong("id"),
                        resultSet.getString("name"),
                        CompetencyCategory.valueOf(resultSet.getString("category")),
                        resultSet.getBigDecimal("weight"),
                        resultSet.getString("evidence_line")))
                .list();
    }

    private List<SameCompany> findSameCompany(Long postingId, Long companyId, Long userId) {
        return jdbcClient.sql("""
                        select posting.id, posting.position,
                               round(job_match.match_score * 100)::integer as score
                        from job_postings posting
                        left join job_matches job_match
                          on job_match.job_posting_id = posting.id
                         and job_match.user_id = :userId
                        where posting.company_id = :companyId
                          and posting.id <> :postingId
                          and posting.status = 'ACTIVE'
                          and posting.deadline > now()
                        order by job_match.match_score desc nulls last, posting.deadline, posting.id
                        """)
                .param("userId", userId)
                .param("companyId", companyId)
                .param("postingId", postingId)
                .query((resultSet, rowNumber) -> new SameCompany(
                        resultSet.getLong("id"),
                        resultSet.getString("position"),
                        nullableInteger(resultSet, "score")))
                .list();
    }

    private List<Similar> findSimilar(Long postingId, Long userId) {
        return jdbcClient.sql("""
                        select candidate.id, company.name as company_name, candidate.position,
                               count(*)::integer as shared_count,
                               round(job_match.match_score * 100)::integer as score
                        from posting_competencies target_required
                        join posting_competencies candidate_required
                          on candidate_required.competency_id = target_required.competency_id
                        join job_postings candidate on candidate.id = candidate_required.job_posting_id
                        join job_postings target on target.id = target_required.job_posting_id
                        join companies company on company.id = candidate.company_id
                        left join job_matches job_match
                          on job_match.job_posting_id = candidate.id
                         and job_match.user_id = :userId
                        where target_required.job_posting_id = :postingId
                          and candidate.id <> :postingId
                          and candidate.company_id <> target.company_id
                          and candidate.status = 'ACTIVE'
                          and candidate.deadline > now()
                        group by candidate.id, company.name, candidate.position,
                                 candidate.deadline, job_match.match_score
                        order by shared_count desc, job_match.match_score desc nulls last,
                                 candidate.deadline, candidate.id
                        limit 3
                        """)
                .param("userId", userId)
                .param("postingId", postingId)
                .query((resultSet, rowNumber) -> new Similar(
                        resultSet.getLong("id"),
                        resultSet.getString("company_name"),
                        resultSet.getString("position"),
                        resultSet.getInt("shared_count"),
                        nullableInteger(resultSet, "score")))
                .list();
    }

    private BaseRow mapBase(ResultSet resultSet, int rowNumber) throws SQLException {
        return new BaseRow(
                resultSet.getLong("id"),
                resultSet.getLong("company_id"),
                resultSet.getString("company_name"),
                resultSet.getString("position"),
                resultSet.getTimestamp("deadline"),
                resultSet.getString("status"),
                resultSet.getString("source_url"),
                resultSet.getString("content"),
                resultSet.getBoolean("bookmarked"));
    }

    private Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private record BaseRow(
            Long id,
            Long companyId,
            String company,
            String position,
            java.sql.Timestamp deadline,
            String status,
            String sourceUrl,
            String content,
            boolean bookmarked) {
    }
}
