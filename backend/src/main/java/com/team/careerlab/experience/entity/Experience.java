package com.team.careerlab.experience.entity;

import com.team.careerlab.competency.entity.Competency;
import com.team.careerlab.user.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 사용자의 경험 한 건. STAR(situation · task · action · result) 로 적는다.
 *
 * <p>출처 컬럼이 따로 없다. <b>{@code aiTaskId} 가 null 이면 직접 입력, 값이 있으면 인테이크로 등록</b>이다.
 * 같은 사실을 두 컬럼에 적으면 언젠가 서로 어긋난다.
 *
 * <p>기간은 {@code startDate} · {@code endDate} 둘 다 비워 둘 수 있다. 월만 입력받으면 1일로 저장하고,
 * 표시 문자열("2025.03 – 2025.11")은 프론트가 만든다. 종료가 null 이면 진행 중이거나 단일 월이다.
 */
@Entity
@Table(
        name = "experiences",
        indexes = @Index(name = "ix_experiences_user_start", columnList = "user_id, start_date"))
public class Experience {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private ExperienceCategory category;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "situation", columnDefinition = "text")
    private String situation;

    @Column(name = "task", columnDefinition = "text")
    private String task;

    @Column(name = "action", columnDefinition = "text")
    private String action;

    /** 화면 필수 항목(R). STAR 중 결과만은 비워 둘 수 없다. */
    @Column(name = "result", nullable = false, columnDefinition = "text")
    private String result;

    /**
     * 인테이크 작업 ID. 연관관계가 아니라 값으로 둔다 — 화면은 숫자만 내보내고,
     * 이 값을 따라 ai_tasks 를 탐색할 일이 없다. FK 는 DB 에서 SET NULL 로 잡혀 있다.
     */
    @Column(name = "ai_task_id")
    private Long aiTaskId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * 경험이 지워지면 같이 지워지는 자식이라 cascade · orphanRemoval 을 건다.
     * 등록·수정 화면이 역량 목록을 통째로 보내므로 {@link #replaceCompetencies} 로만 바꾼다.
     */
    @OneToMany(mappedBy = "experience", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExperienceCompetency> competencies = new ArrayList<>();

    protected Experience() {
        // JPA 용
    }

    private Experience(User user, String title, ExperienceCategory category, LocalDate startDate, LocalDate endDate,
            String situation, String task, String action, String result, Long aiTaskId) {
        this.user = user;
        this.title = title;
        this.category = category;
        this.startDate = startDate;
        this.endDate = endDate;
        this.situation = situation;
        this.task = task;
        this.action = action;
        this.result = result;
        this.aiTaskId = aiTaskId;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 새 경험. 역량은 저장 뒤 {@link #replaceCompetencies} 로 붙인다 — 복합 키에 경험 ID 가 필요해서
     * 저장 전에는 붙일 수 없다.
     *
     * @param aiTaskId 인테이크 후보에서 등록할 때만 값이 있다. 직접 입력은 null
     */
    public static Experience register(User user, String title, ExperienceCategory category, LocalDate startDate,
            LocalDate endDate, String situation, String task, String action, String result, Long aiTaskId) {
        return new Experience(user, title, category, startDate, endDate, situation, task, action, result, aiTaskId);
    }

    /** 수정 화면은 모든 필드를 다시 보낸다. 출처(aiTaskId)는 수정으로 바뀌지 않는다. */
    public void update(String title, ExperienceCategory category, LocalDate startDate, LocalDate endDate,
            String situation, String task, String action, String result) {
        this.title = title;
        this.category = category;
        this.startDate = startDate;
        this.endDate = endDate;
        this.situation = situation;
        this.task = task;
        this.action = action;
        this.result = result;
        this.updatedAt = Instant.now();
    }

    /**
     * 역량 목록을 통째로 바꾼다.
     *
     * <p>비우고 다시 넣지 않는다. 같은 (경험, 역량) 키를 한 트랜잭션에서 지우고 다시 넣으면 Hibernate 가
     * INSERT 를 DELETE 보다 먼저 실행해 PK 충돌이 난다. 그래서 남는 것은 강도만 고치고,
     * 빠진 것만 지우고, 새 것만 넣는다.
     *
     * @param strengths 역량 → 강도(0~1). 비어 있으면 안 된다는 검증은 서비스에서 한다
     */
    public void replaceCompetencies(Map<Competency, BigDecimal> strengths) {
        Map<Long, BigDecimal> byId = new HashMap<>();
        strengths.forEach((competency, strength) -> byId.put(competency.getId(), strength));

        // ID 로 비교한다. 지연 로딩 프록시와 조회한 실제 엔티티를 equals 로 섞어 비교하지 않기 위해서다.
        competencies.removeIf(existing -> !byId.containsKey(existing.getCompetency().getId()));
        for (Map.Entry<Competency, BigDecimal> entry : strengths.entrySet()) {
            competencies.stream()
                    .filter(existing -> existing.getCompetency().getId().equals(entry.getKey().getId()))
                    .findFirst()
                    .ifPresentOrElse(
                            existing -> existing.changeStrength(entry.getValue()),
                            () -> competencies.add(new ExperienceCompetency(this, entry.getKey(), entry.getValue())));
        }
        this.updatedAt = Instant.now();
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

    public String getTitle() {
        return title;
    }

    public ExperienceCategory getCategory() {
        return category;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getSituation() {
        return situation;
    }

    public String getTask() {
        return task;
    }

    public String getAction() {
        return action;
    }

    public String getResult() {
        return result;
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

    public List<ExperienceCompetency> getCompetencies() {
        return Collections.unmodifiableList(competencies);
    }
}
