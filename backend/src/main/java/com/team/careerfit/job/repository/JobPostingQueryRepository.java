package com.team.careerfit.job.repository;

import com.team.careerfit.job.entity.PostingStatus;
import com.team.careerfit.matching.entity.MatchVerdict;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JobPostingQueryRepository {

    private static final String SELECT = """
            select
                posting.id,
                company.name as company_name,
                posting.position,
                posting.deadline,
                posting.status,
                exists (
                    select 1
                    from bookmarks bookmark
                    where bookmark.user_id = :userId
                      and bookmark.job_posting_id = posting.id
                ) as bookmarked,
                job_match.match_score,
                job_match.verdict,
                coalesce((
                    select count(*)
                    from posting_competencies required
                    where required.job_posting_id = posting.id
                ), 0) as required_count,
                array(
                    select competency.name
                    from jsonb_array_elements(coalesce(job_match.coverage, '[]'::jsonb)) as coverage(value)
                    join competencies competency
                      on competency.id = (coverage.value ->> 'competencyId')::bigint
                    where not coalesce((coverage.value ->> 'isGap')::boolean, false)
                    order by competency.name
                ) as covered_competency_names,
                coalesce((
                    select count(*)
                    from job_posting_questions question
                    where question.job_posting_id = posting.id
                ), 0) as question_count,
                coalesce((
                    select count(*)
                    from job_posting_questions question
                    join cover_letter_answers answer on answer.question_id = question.id
                    where question.job_posting_id = posting.id
                      and answer.user_id = :userId
                ), 0) as answer_count
            """;

    private static final String FROM = """
            from job_postings posting
            join companies company on company.id = posting.company_id
            left join job_matches job_match
              on job_match.job_posting_id = posting.id
             and job_match.user_id = :userId
            where 1 = 1
            """;

    private final JdbcClient jdbcClient;

    public JobPostingQueryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Page findAll(SearchCondition condition) {
        String where = createWhere(condition);
        Map<String, Object> parameters = parameters(condition);

        String orderBy = switch (condition.sort()) {
            case MATCH -> "order by job_match.match_score desc nulls last, posting.deadline, posting.id";
            case DEADLINE -> "order by posting.deadline, posting.id";
        };

        List<Row> rows = jdbcClient.sql(SELECT + FROM + where + orderBy + " limit :size offset :offset")
                .params(parameters)
                .param("size", condition.size())
                .param("offset", Math.multiplyExact((long) condition.page(), condition.size()))
                .query(this::mapRow)
                .list();

        Long totalCount = jdbcClient.sql("select count(*) " + FROM + where)
                .params(parameters)
                .query(Long.class)
                .single();

        return new Page(rows, totalCount);
    }

    private String createWhere(SearchCondition condition) {
        StringBuilder where = new StringBuilder();
        if (!condition.includeClosed()) {
            where.append(" and posting.status = 'ACTIVE' and posting.deadline > now()");
        }
        if (condition.keyword() != null) {
            where.append("""
                     and (
                         position(:keyword in lower(company.name)) > 0
                         or position(:keyword in lower(posting.position)) > 0
                         or exists (
                             select 1
                             from posting_competencies required
                             join competencies competency on competency.id = required.competency_id
                             where required.job_posting_id = posting.id
                               and position(:keyword in lower(competency.name)) > 0
                         )
                     )
                    """);
        }
        if (!condition.competencyIds().isEmpty()) {
            where.append("""
                     and exists (
                         select 1
                         from posting_competencies required
                         where required.job_posting_id = posting.id
                           and required.competency_id in (:competencyIds)
                     )
                    """);
        }
        if (condition.bookmarked() != null) {
            where.append("""
                     and exists (
                         select 1
                         from bookmarks bookmark_filter
                         where bookmark_filter.user_id = :userId
                           and bookmark_filter.job_posting_id = posting.id
                     ) = :bookmarked
                    """);
        }
        return where.toString();
    }

    private Map<String, Object> parameters(SearchCondition condition) {
        HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("userId", condition.userId());
        if (condition.keyword() != null) {
            parameters.put("keyword", condition.keyword());
        }
        if (!condition.competencyIds().isEmpty()) {
            parameters.put("competencyIds", condition.competencyIds());
        }
        if (condition.bookmarked() != null) {
            parameters.put("bookmarked", condition.bookmarked());
        }
        return parameters;
    }

    private Row mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        BigDecimal matchScore = resultSet.getBigDecimal("match_score");
        String verdict = resultSet.getString("verdict");
        return new Row(
                resultSet.getLong("id"),
                resultSet.getString("company_name"),
                resultSet.getString("position"),
                resultSet.getTimestamp("deadline").toInstant(),
                PostingStatus.valueOf(resultSet.getString("status")),
                resultSet.getBoolean("bookmarked"),
                matchScore,
                verdict == null ? null : MatchVerdict.valueOf(verdict),
                stringList(resultSet.getArray("covered_competency_names")),
                resultSet.getInt("required_count"),
                resultSet.getInt("answer_count"),
                resultSet.getInt("question_count"));
    }

    private List<String> stringList(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        return Arrays.asList((String[]) array.getArray());
    }

    public enum Sort {
        MATCH,
        DEADLINE
    }

    public record SearchCondition(
            Long userId,
            String keyword,
            List<Long> competencyIds,
            Boolean bookmarked,
            Sort sort,
            boolean includeClosed,
            int page,
            int size) {
    }

    public record Page(List<Row> rows, long totalCount) {
    }

    public record Row(
            Long id,
            String company,
            String position,
            Instant deadline,
            PostingStatus status,
            boolean bookmarked,
            BigDecimal matchScore,
            MatchVerdict verdict,
            List<String> coveredCompetencyNames,
            int requiredCount,
            int answeredCount,
            int questionCount) {
    }
}
