package com.team.careerlab.global.security;

/** 세션 속성 키. 문자열을 여기저기 흩어 놓으면 오타 하나로 로그인이 풀린다. */
public final class SessionKeys {

    /** 값은 User 의 PK(Long). 사용자 객체 통째로 넣지 않는다 — 세션이 오래된 값을 물고 있게 된다. */
    public static final String USER_ID = "careerlab.userId";

    private SessionKeys() {}
}
