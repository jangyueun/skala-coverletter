package com.team.careerfit.integration.storage.client;

import com.team.careerfit.integration.storage.config.SupabaseStorageProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Supabase Storage REST API에 바이너리를 그대로 올린다.
 *
 * <p>서버는 파일 자체를 DB 에 두지 않는다 — Storage 에 올리고 URL 만 {@code ai_tasks.request_payload}
 * 에 기록해 AI 서버에 넘긴다(v6 회의 결정). 버킷이 public 이어야 반환한 URL 로 AI 서버가 바로 읽을 수 있다.
 */
@Component
public class SupabaseStorageClient {

    private final SupabaseStorageProperties properties;
    private final RestClient restClient;

    public SupabaseStorageClient(SupabaseStorageProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.create();
    }

    /**
     * @param path 버킷 안 경로. 예: {@code intake/7/790/portfolio.pdf}
     * @return 공개 URL
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

        return "%s/storage/v1/object/public/%s/%s".formatted(properties.url(), properties.bucket(), path);
    }
}
