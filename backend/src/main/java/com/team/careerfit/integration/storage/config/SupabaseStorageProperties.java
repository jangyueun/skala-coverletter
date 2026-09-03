package com.team.careerfit.integration.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Supabase Storage 설정. 실제 값(URL·서비스 키)은 절대 여기 쓰지 않는다 — `.env` 또는 배포 환경변수로 준다.
 *
 * @param url 프로젝트 REST URL. 예: {@code https://xxxx.supabase.co}. DB 접속에 쓰는
 *     {@code SUPABASE_DB_URL}(풀러 JDBC 주소)과는 다른 값이다
 * @param serviceRoleKey Storage 업로드용 서비스 롤 키. anon 키가 아니다 — RLS 를 우회해야 서버가 대신 올릴 수 있다
 * @param bucket 인테이크 첨부파일을 올릴 버킷 이름
 */
@ConfigurationProperties("careerfit.storage")
public record SupabaseStorageProperties(String url, String serviceRoleKey, String bucket) {
}
