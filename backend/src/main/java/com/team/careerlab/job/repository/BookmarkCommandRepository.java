package com.team.careerlab.job.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class BookmarkCommandRepository {

    private final JdbcClient jdbcClient;

    public BookmarkCommandRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public boolean postingExists(Long postingId) {
        return jdbcClient.sql("select exists(select 1 from job_postings where id = :postingId)")
                .param("postingId", postingId)
                .query(Boolean.class)
                .single();
    }

    public void save(Long userId, Long postingId) {
        jdbcClient.sql("""
                        insert into bookmarks (user_id, job_posting_id, created_at)
                        values (:userId, :postingId, now())
                        on conflict (user_id, job_posting_id) do nothing
                        """)
                .param("userId", userId)
                .param("postingId", postingId)
                .update();
    }

    public void delete(Long userId, Long postingId) {
        jdbcClient.sql("""
                        delete from bookmarks
                        where user_id = :userId and job_posting_id = :postingId
                        """)
                .param("userId", userId)
                .param("postingId", postingId)
                .update();
    }
}
