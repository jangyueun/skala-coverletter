package com.team.careerlab.job.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * 공고의 자기소개서 문항. <b>관리자가 일괄 관리하고 사용자는 등록·삭제하지 않는다</b>(v6 회의 결정).
 *
 * <p>(공고, 순번) 이 유일하다. 순번은 1부터고 화면에 "문항 1 · 문항 2" 로 나간다.
 * {@code lengthLimit} 은 글자 수 상한이고 null 이면 제한이 없다.
 */
@Entity
@Table(
        name = "job_posting_questions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_job_posting_questions_sequence",
                columnNames = {"job_posting_id", "sequence"}))
public class JobPostingQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @Column(name = "sequence", nullable = false)
    private int sequence;

    @Column(name = "prompt_text", nullable = false, columnDefinition = "text")
    private String promptText;

    @Column(name = "length_limit")
    private Integer lengthLimit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JobPostingQuestion() {
        // JPA 용
    }

    private JobPostingQuestion(JobPosting jobPosting, int sequence, String promptText, Integer lengthLimit) {
        this.jobPosting = jobPosting;
        this.sequence = sequence;
        this.promptText = promptText;
        this.lengthLimit = lengthLimit;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static JobPostingQuestion of(JobPosting jobPosting, int sequence, String promptText, Integer lengthLimit) {
        return new JobPostingQuestion(jobPosting, sequence, promptText, lengthLimit);
    }

    public Long getId() {
        return id;
    }

    public JobPosting getJobPosting() {
        return jobPosting;
    }

    public int getSequence() {
        return sequence;
    }

    public String getPromptText() {
        return promptText;
    }

    public Integer getLengthLimit() {
        return lengthLimit;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
