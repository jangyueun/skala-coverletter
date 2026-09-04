package com.team.careerlab.competency.repository;

import com.team.careerlab.competency.dto.CompetencyResponse;
import com.team.careerlab.competency.entity.CompetencyCategory;
import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CompetencyQueryRepository {

    private final JdbcClient jdbcClient;

    public CompetencyQueryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<CompetencyResponse> findAll() {
        return jdbcClient.sql("""
                        select competency.id, competency.name, competency.category,
                               array_agg(alias.alias order by alias.alias)
                                   filter (where alias.id is not null) as aliases
                        from competencies competency
                        left join competency_aliases alias on alias.competency_id = competency.id
                        group by competency.id, competency.name, competency.category
                        order by competency.category, competency.name, competency.id
                        """)
                .query((resultSet, rowNumber) -> new CompetencyResponse(
                        resultSet.getLong("id"),
                        resultSet.getString("name"),
                        CompetencyCategory.valueOf(resultSet.getString("category")),
                        stringList(resultSet.getArray("aliases"))))
                .list();
    }

    private List<String> stringList(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        return Arrays.asList((String[]) array.getArray());
    }
}
