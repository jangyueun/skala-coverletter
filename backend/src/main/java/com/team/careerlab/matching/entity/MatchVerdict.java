package com.team.careerlab.matching.entity;

import java.math.BigDecimal;

/**
 * 매칭 판정 3단계. 화면 라벨은 "지원 권장" · "조건부 지원" · "보강 필요".
 *
 * <p>경계값은 목업 화면과 맞춘 팀 결정이다. 바꾸면 이미 저장된 job_matches 의 verdict 와 어긋나므로
 * <b>경계를 바꿀 때는 전체 재계산(MATCH 작업)을 같이 돌린다.</b>
 */
public enum MatchVerdict {
    /** match_score >= 0.85 */
    RECOMMEND,
    /** match_score >= 0.62 */
    CONDITIONAL,
    /** 그 외 */
    HOLD;

    private static final BigDecimal RECOMMEND_FROM = new BigDecimal("0.85");
    private static final BigDecimal CONDITIONAL_FROM = new BigDecimal("0.62");

    public static MatchVerdict from(BigDecimal matchScore) {
        if (matchScore.compareTo(RECOMMEND_FROM) >= 0) {
            return RECOMMEND;
        }
        if (matchScore.compareTo(CONDITIONAL_FROM) >= 0) {
            return CONDITIONAL;
        }
        return HOLD;
    }
}
