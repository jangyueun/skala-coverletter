package com.team.careerfit.job.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/** bookmarks 의 복합 키 (user_id, job_posting_id). 같은 공고를 두 번 즐겨찾기할 수 없다. */
@Embeddable
public class BookmarkId implements Serializable {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "job_posting_id", nullable = false)
    private Long jobPostingId;

    protected BookmarkId() {
        // JPA 용
    }

    public BookmarkId(Long userId, Long jobPostingId) {
        this.userId = userId;
        this.jobPostingId = jobPostingId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getJobPostingId() {
        return jobPostingId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BookmarkId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(jobPostingId, that.jobPostingId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, jobPostingId);
    }
}
