package com.team.careerfit.competency.entity;

/**
 * 역량 사전의 범주 5종. DB 에는 이름 그대로(varchar + CHECK) 저장한다.
 *
 * <p>화면 라벨("직무 역량" · "기술·언어" …)과 약칭("직무" · "기술" …)은 프론트 상수다.
 * 여기 두면 라벨을 바꿀 때마다 서버를 배포해야 해서 뺐다.
 */
public enum CompetencyCategory {
    /** 직무 역량 */
    ROLE,
    /** 기술·언어 */
    TECH,
    /** 일하는 방식 */
    SOFT,
    /** 산업 */
    DOMAIN,
    /** 인재상 */
    VALUE
}
