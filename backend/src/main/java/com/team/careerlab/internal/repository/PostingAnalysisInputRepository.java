package com.team.careerlab.internal.repository;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PostingAnalysisInputRepository {

    private final JdbcClient jdbcClient;

    public PostingAnalysisInputRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<PostingSnapshot> findPosting(Long postingId) {
        return jdbcClient.sql("select id, content from job_postings where id = :postingId for update")
                .param("postingId", postingId)
                .query((resultSet, rowNumber) -> new PostingSnapshot(
                        resultSet.getLong("id"), resultSet.getString("content")))
                .optional();
    }

    public List<CompetencySnapshot> findCompetencies() {
        return jdbcClient.sql("""
                        select competency.id, competency.name, competency.category,
                               array_agg(alias.alias order by alias.alias)
                                   filter (where alias.id is not null) as aliases
                        from competencies competency
                        left join competency_aliases alias on alias.competency_id = competency.id
                        group by competency.id, competency.name, competency.category
                        order by competency.id
                        """)
                .query((resultSet, rowNumber) -> new CompetencySnapshot(
                        resultSet.getLong("id"),
                        resultSet.getString("name"),
                        resultSet.getString("category"),
                        stringList(resultSet.getArray("aliases"))))
                .list();
    }

    private List<String> stringList(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
    }

    public record PostingSnapshot(Long id, String content) {
    }

    public record CompetencySnapshot(Long id, String name, String category, List<String> aliases) {
    }
}
