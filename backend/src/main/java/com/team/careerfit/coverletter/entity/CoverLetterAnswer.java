package com.team.careerfit.coverletter.entity;

import com.team.careerfit.job.entity.JobPostingQuestion;
import com.team.careerfit.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 문항에 대한 사용자의 답변. <b>(user, question) 당 1행</b>이고 저장할 때마다 덮어쓴다.
 *
 * <p>근거 경험은 {@code usedExperienceIds} 배열 컬럼에 본문과 함께 저장한다. 별도 조인 테이블을 두지 않은 건
 * 저장 단위가 "본문 + 근거 경험" 하나이고, 경험 목록의 "N개 문항에 사용" 배지는 unnest GROUP BY 한 번으로 세면 되기 때문이다.
 * <b>배열 안의 경험이 이 사용자의 것인지는 DB 가 검사하지 않는다.</b> 서비스에서 반드시 확인한다.
 *
 * <p>{@code aiTaskId} 가 있으면 AI 초안을 반영한 답변이다. 초안 자체는 저장되지 않고 사용자가 저장한 본문만 남는다.
 */
@Entity
@Table(
        name = "cover_letter_answers",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cover_letter_answers_user_question",
                columnNames = {"user_id", "question_id"}),
        indexes = @Index(name = "ix_cover_letter_answers_question", columnList = "question_id"))
public class CoverLetterAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private JobPostingQuestion question;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    /** 서버가 센 글자 수. 화면의 "210 / 700" 왼쪽 값이다. 코드포인트 기준이라 이모지도 한 글자다. */
    @Column(name = "char_count", nullable = false)
    private int charCount;

    @Column(name = "used_experience_ids", nullable = false, columnDefinition = "bigint[]")
    private List<Long> usedExperienceIds = new ArrayList<>();

    /** 연관관계가 아니라 값으로 둔다 — 화면은 숫자만 내보내고, 작업 내용을 여기서 탐색하지 않는다. FK 는 SET NULL. */
    @Column(name = "ai_task_id")
    private Long aiTaskId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 화면의 "HH:MM 저장됨". */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CoverLetterAnswer() {
        // JPA 용
    }

    private CoverLetterAnswer(User user, JobPostingQuestion question, String content, List<Long> usedExperienceIds,
            Long aiTaskId) {
        this.user = user;
        this.question = question;
        Instant now = Instant.now();
        this.createdAt = now;
        apply(content, usedExperienceIds, aiTaskId, now);
    }

    public static CoverLetterAnswer write(User user, JobPostingQuestion question, String content,
            List<Long> usedExperienceIds, Long aiTaskId) {
        return new CoverLetterAnswer(user, question, content, usedExperienceIds, aiTaskId);
    }

    /**
     * PUT 은 본문·근거 경험·draftTaskId 를 전부 다시 보낸다. 한 번 AI 초안을 썼어도 다음 저장에 draftTaskId 가
     * 없으면 null 로 돌아간다 — 지금 저장된 본문이 어디서 왔는지를 뜻하기 때문이다.
     */
    public void rewrite(String content, List<Long> usedExperienceIds, Long aiTaskId) {
        apply(content, usedExperienceIds, aiTaskId, Instant.now());
    }

    private void apply(String content, List<Long> usedExperienceIds, Long aiTaskId, Instant at) {
        this.content = content;
        this.charCount = content.codePointCount(0, content.length());
        this.usedExperienceIds = new ArrayList<>(usedExperienceIds);
        this.aiTaskId = aiTaskId;
        this.updatedAt = at;
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public JobPostingQuestion getQuestion() {
        return question;
    }

    public String getContent() {
        return content;
    }

    public int getCharCount() {
        return charCount;
    }

    public List<Long> getUsedExperienceIds() {
        return Collections.unmodifiableList(usedExperienceIds);
    }

    public Long getAiTaskId() {
        return aiTaskId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
