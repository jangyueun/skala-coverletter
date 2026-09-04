package com.team.careerlab.job.repository;

import com.team.careerlab.job.dto.PostingQuestionResponse;
import com.team.careerlab.job.dto.PostingQuestionResponse.Answer;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PostingQuestionQueryRepository {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final JdbcClient jdbcClient;

    public PostingQuestionQueryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<List<PostingQuestionResponse>> findByPostingId(Long postingId, Long userId) {
        List<QuestionRow> rows = jdbcClient.sql("""
                        select posting.id as posting_id,
                               question.id as question_id,
                               question.sequence,
                               question.prompt_text,
                               question.length_limit,
                               answer.id as answer_id,
                               answer.content,
                               answer.char_count,
                               answer.used_experience_ids,
                               answer.updated_at
                        from job_postings posting
                        left join job_posting_questions question
                          on question.job_posting_id = posting.id
                        left join cover_letter_answers answer
                          on answer.question_id = question.id
                         and answer.user_id = :userId
                        where posting.id = :postingId
                        order by question.sequence
                        """)
                .param("userId", userId)
                .param("postingId", postingId)
                .query(this::mapRow)
                .list();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.stream()
                .filter(row -> row.questionId() != null)
                .map(QuestionRow::response)
                .toList());
    }

    private QuestionRow mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        Long questionId = resultSet.getObject("question_id", Long.class);
        if (questionId == null) {
            return new QuestionRow(null, null);
        }
        Answer answer = resultSet.getObject("answer_id", Long.class) == null
                ? null
                : new Answer(
                        resultSet.getString("content"),
                        resultSet.getInt("char_count"),
                        longList(resultSet.getArray("used_experience_ids")),
                        resultSet.getTimestamp("updated_at").toInstant().atZone(KOREA).toOffsetDateTime());
        return new QuestionRow(
                questionId,
                new PostingQuestionResponse(
                        questionId,
                        resultSet.getInt("sequence"),
                        resultSet.getString("prompt_text"),
                        resultSet.getObject("length_limit", Integer.class),
                        answer));
    }

    private List<Long> longList(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        return Arrays.asList((Long[]) array.getArray());
    }

    private record QuestionRow(Long questionId, PostingQuestionResponse response) {
    }
}
