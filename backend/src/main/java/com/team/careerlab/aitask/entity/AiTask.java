package com.team.careerlab.aitask.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * AI 작업 한 건. Python AI 서버는 상태를 갖지 않으므로 <b>작업 상태·재시도·결과 저장은 전부 이 테이블이 맡는다.</b>
 *
 * <p>대상(user · posting · question)은 연관관계가 아니라 ID 값으로 둔다. 네 도메인을 가리키는 허브라서
 * 엔티티로 잇기 시작하면 aitask 가 모든 도메인에 의존하게 된다. 소유자 검사(403)는 userId 비교로 충분하다.
 * FK 와 CASCADE 는 DB 에 있다.
 *
 * <p>{@code idempotencyKey} = sha256(type + 대상 ID + inputHash + promptVersion). 같은 입력의 재요청은
 * 새 작업을 만들지 않고 기존 taskId 를 200 으로 돌려준다. 같은 대상에 <b>다른</b> 입력으로 진행 중인 작업이 있으면
 * 409 다 — 그건 부분 UNIQUE(uq_ai_task_*_inflight)가 잡는다.
 *
 * <p>JSON 컬럼은 문자열로 둔다. 요청 스냅샷과 결과는 그대로 저장하고 그대로 돌려줄 뿐, 서버가 안에서 검색하지 않는다.
 */
@Entity
@Table(
        name = "ai_tasks",
        uniqueConstraints = @UniqueConstraint(name = "uk_ai_tasks_idempotency_key", columnNames = "idempotency_key"),
        indexes = {
                @Index(name = "ix_ai_tasks_status_created", columnList = "status, created_at"),
                @Index(name = "ix_ai_tasks_type_user_status", columnList = "task_type, user_id, status"),
                @Index(name = "ix_ai_tasks_type_posting_status", columnList = "task_type, job_posting_id, status")
        })
public class AiTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 32)
    private AiTaskType taskType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AiTaskStatus status;

    /** INTAKE · MATCH · DRAFT 에 있다. POSTING_ANALYSIS 는 null. */
    @Column(name = "user_id")
    private Long userId;

    /** POSTING_ANALYSIS · MATCH 에 있다. */
    @Column(name = "job_posting_id")
    private Long jobPostingId;

    /** DRAFT 에만 있다. */
    @Column(name = "question_id")
    private Long questionId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "input_hash", nullable = false, length = 64)
    private String inputHash;

    /** AI 응답의 model. mock 이면 "mock". 완료 전에는 null. */
    @Column(name = "model", length = 100)
    private String model;

    /** AI 응답의 promptVersion. 예: posting_analysis/v2 */
    @Column(name = "prompt_version", length = 50)
    private String promptVersion;

    /** 입력 스냅샷. 인테이크는 links[] 와 Storage 에 올린 fileUrls[] — 파일 자체는 여기 없다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload", nullable = false, columnDefinition = "jsonb")
    private String requestPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_payload", columnDefinition = "jsonb")
    private String resultPayload;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    /** {@code [{"no":1,"status":"FAILED","latencyMs":812,"errorCode":"TIMEOUT","at":"..."}]}. 시도마다 서비스가 새 배열을 넣는다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attempts", nullable = false, columnDefinition = "jsonb")
    private String attempts;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected AiTask() {
        // JPA 용
    }

    private AiTask(AiTaskType taskType, Long userId, Long jobPostingId, Long questionId, String idempotencyKey,
            String inputHash, String requestPayload) {
        this.taskType = taskType;
        this.status = AiTaskStatus.PENDING;
        this.userId = userId;
        this.jobPostingId = jobPostingId;
        this.questionId = questionId;
        this.idempotencyKey = idempotencyKey;
        this.inputHash = inputHash;
        this.requestPayload = requestPayload;
        this.retryCount = 0;
        this.attempts = "[]";
        this.createdAt = Instant.now();
    }

    public static AiTask postingAnalysis(Long jobPostingId, String idempotencyKey, String inputHash,
            String requestPayload) {
        return new AiTask(AiTaskType.POSTING_ANALYSIS, null, jobPostingId, null, idempotencyKey, inputHash,
                requestPayload);
    }

    public static AiTask experienceIntake(Long userId, String idempotencyKey, String inputHash,
            String requestPayload) {
        return new AiTask(AiTaskType.EXPERIENCE_INTAKE, userId, null, null, idempotencyKey, inputHash,
                requestPayload);
    }

    public static AiTask match(Long userId, Long jobPostingId, String idempotencyKey, String inputHash,
            String requestPayload) {
        return new AiTask(AiTaskType.MATCH, userId, jobPostingId, null, idempotencyKey, inputHash, requestPayload);
    }

    public static AiTask draft(Long userId, Long questionId, String idempotencyKey, String inputHash,
            String requestPayload) {
        return new AiTask(AiTaskType.DRAFT, userId, null, questionId, idempotencyKey, inputHash, requestPayload);
    }

    /**
     * 인테이크는 Storage 경로({@code intake/{userId}/{taskId}/})에 taskId 가 필요해서 links 만으로
     * 먼저 PENDING 행을 만들고, 파일을 다 올린 뒤 fileUrls 를 채운 스냅샷으로 덮어쓴다.
     */
    public void attachRequestPayload(String requestPayload) {
        this.requestPayload = requestPayload;
    }

    /** 워커가 집어 갔다. 첫 시도든 재시도든 startedAt 은 처음 값을 유지한다 — 전체 소요 시간을 재기 위해서다. */
    public void start() {
        this.status = AiTaskStatus.RUNNING;
        if (this.startedAt == null) {
            this.startedAt = Instant.now();
        }
    }

    /** 한 번 실패했지만 재시도가 남았다. 상태는 RUNNING 그대로 두고 시도 기록과 횟수만 올린다. */
    public void recordRetry(String attempts) {
        this.attempts = attempts;
        this.retryCount++;
    }

    public void complete(String model, String promptVersion, String resultPayload, String attempts) {
        this.status = AiTaskStatus.COMPLETED;
        this.model = model;
        this.promptVersion = promptVersion;
        this.resultPayload = resultPayload;
        this.attempts = attempts;
        this.errorCode = null;
        this.errorMessage = null;
        this.completedAt = Instant.now();
    }

    /** 재시도를 소진했다. 폴링 응답의 {@code error} 가 이 값으로 채워진다. */
    public void fail(String errorCode, String errorMessage, String attempts) {
        this.status = AiTaskStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.attempts = attempts;
        this.completedAt = Instant.now();
    }

    /**
     * 같은 입력의 실패한 작업을 다시 큐에 넣는다. 멱등 키가 유일 제약이라 새 행을 만들 수 없어서(같은 입력이면 같은 키)
     * 이 행을 PENDING 으로 되돌린다 — 워커가 다시 집어간다. 인테이크가 503·타임아웃으로 실패한 뒤 같은 자료로 재시도하는 길이다.
     */
    public void reopen() {
        this.status = AiTaskStatus.PENDING;
        this.errorCode = null;
        this.errorMessage = null;
        this.resultPayload = null;
        this.model = null;
        this.promptVersion = null;
        this.attempts = "[]";
        this.retryCount = 0;
        this.startedAt = null;
        this.completedAt = null;
    }

    /** 분석 작업은 사용자가 없어 누구나 못 본다. 나머지는 만든 사용자만 본다. */
    public boolean isOwnedBy(Long userId) {
        return this.userId != null && this.userId.equals(userId);
    }

    public Long getId() {
        return id;
    }

    public AiTaskType getTaskType() {
        return taskType;
    }

    public AiTaskStatus getStatus() {
        return status;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getJobPostingId() {
        return jobPostingId;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getInputHash() {
        return inputHash;
    }

    public String getModel() {
        return model;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getRequestPayload() {
        return requestPayload;
    }

    public String getResultPayload() {
        return resultPayload;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getAttempts() {
        return attempts;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
