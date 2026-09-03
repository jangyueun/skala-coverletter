package com.team.careerfit.job.entity;

import com.team.careerfit.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 즐겨찾기. 행이 있으면 켜진 것, 없으면 꺼진 것이다 — 켜짐/꺼짐 컬럼을 두지 않는다.
 *
 * <p>{@code PUT /api/postings/{id}/bookmark} 의 {@code bookmarked:false} 는 이 행을 지운다.
 */
@Entity
@Table(name = "bookmarks")
public class Bookmark {

    @EmbeddedId
    private BookmarkId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @MapsId("jobPostingId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Bookmark() {
        // JPA 용
    }

    private Bookmark(User user, JobPosting jobPosting) {
        this.id = new BookmarkId(user.getId(), jobPosting.getId());
        this.user = user;
        this.jobPosting = jobPosting;
        this.createdAt = Instant.now();
    }

    public static Bookmark of(User user, JobPosting jobPosting) {
        return new Bookmark(user, jobPosting);
    }

    public BookmarkId getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public JobPosting getJobPosting() {
        return jobPosting;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
