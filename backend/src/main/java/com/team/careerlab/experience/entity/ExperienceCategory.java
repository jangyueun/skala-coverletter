package com.team.careerlab.experience.entity;

/**
 * 경험 등록 대화상자의 분류 6종. DB 에는 이름 그대로(varchar + CHECK) 저장한다.
 */
public enum ExperienceCategory {
    /** 팀 프로젝트 */
    TEAM_PROJECT,
    /** 개인 프로젝트 */
    PERSONAL_PROJECT,
    /** 실습 프로젝트 */
    PRACTICE_PROJECT,
    /** 대외활동 */
    EXTERNAL_ACTIVITY,
    /** 인턴·근무 */
    EMPLOYMENT,
    /** 수상·자격 */
    AWARD_CERTIFICATE
}
