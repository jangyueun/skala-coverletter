package com.team.careerlab.integration.storage.client;

import com.team.careerlab.integration.storage.config.SupabaseStorageProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Supabase Storage REST API에 바이너리를 그대로 올린다.
 *
 * <p>서버는 파일 자체를 DB 에 두지 않는다 — Storage 에 올리고 URL 만 {@code ai_tasks.request_payload}
 * 에 기록해 AI 서버에 넘긴다(v6 회의 결정).
 *
 * <p>인테이크 첨부는 이력서·포트폴리오 같은 개인 문서라 버킷을 private 으로 둔다. 그래서 공개 URL 대신
 * <b>기간제 서명 URL</b>을 만들어 돌려준다. 만료(현재 {@link #SIGNED_URL_TTL_SECONDS})는 넉넉히 잡아뒀다 —
 * 아직 이 URL 을 실제로 읽어가는 AI 워커가 없어서, 워커가 붙으면 그때 처리 직전에 다시 서명하는 방식으로
 * 바꾸는 게 더 안전하다.
 */
@Component
public class SupabaseStorageClient {

    private static final long SIGNED_URL_TTL_SECONDS = 7 * 24 * 60 * 60L;

    private final SupabaseStorageProperties properties;
    private final RestClient restClient;

    public SupabaseStorageClient(SupabaseStorageProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.create();
    }

    /**
     * @param path 버킷 안 경로. 예: {@code intake/7/790/portfolio.pdf}
     * @return 기간제 서명 URL
     */
    public String upload(String path, byte[] content, String contentType) {
        String uploadUrl = "%s/storage/v1/object/%s/%s".formatted(properties.url(), properties.bucket(), path);

        restClient.put()
                .uri(uploadUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.serviceRoleKey())
                .header("apikey", properties.serviceRoleKey())
                .contentType(contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM)
                .body(content)
                .retrieve()
                .toBodilessEntity();

        return sign(path);
    }

    private String sign(String path) {
        String signUrl = "%s/storage/v1/object/sign/%s/%s".formatted(properties.url(), properties.bucket(), path);

        SignResponse response = restClient.post()
                .uri(signUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.serviceRoleKey())
                .header("apikey", properties.serviceRoleKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SignRequest(SIGNED_URL_TTL_SECONDS))
                .retrieve()
                .body(SignResponse.class);

        // signedURL은 "/object/sign/{bucket}/{path}?token=..." 형태의 상대 경로로 온다.
        return properties.url() + "/storage/v1" + response.signedURL();
    }

    private record SignRequest(long expiresIn) {
    }

    private record SignResponse(String signedURL) {
    }
}
