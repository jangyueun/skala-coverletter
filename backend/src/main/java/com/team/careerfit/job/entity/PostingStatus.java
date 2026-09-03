package com.team.careerfit.job.entity;

/**
 * 공고 상태. CLOSED 는 deadline 이 지난 뒤 배치가 바꾼다.
 *
 * <p>배치가 늦을 수 있으므로 <b>마감 판정은 status 만 믿지 말고 deadline 도 같이 본다</b>
 * ({@link JobPosting#isClosed(java.time.Instant)}).
 */
public enum PostingStatus {
    ACTIVE,
    CLOSED
}
