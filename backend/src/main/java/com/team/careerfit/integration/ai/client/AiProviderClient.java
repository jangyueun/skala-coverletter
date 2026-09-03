package com.team.careerfit.integration.ai.client;

import com.team.careerfit.integration.ai.config.AiProviderProperties;
import com.team.careerfit.integration.ai.dto.DraftRequest;
import com.team.careerfit.integration.ai.dto.DraftResponse;
import com.team.careerfit.integration.ai.dto.ExperienceIntakeRequest;
import com.team.careerfit.integration.ai.dto.ExperienceIntakeResponse;
import com.team.careerfit.integration.ai.dto.MatchRequest;
import com.team.careerfit.integration.ai.dto.MatchResponse;
import com.team.careerfit.integration.ai.dto.PostingAnalysisRequest;
import com.team.careerfit.integration.ai.dto.PostingAnalysisResponse;
import com.team.careerfit.integration.ai.dto.PromptVersions;
import com.team.careerfit.integration.ai.exception.AiProviderException;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Python AI 서버(docs/api-spec-v6.md 8절, 현재는 Mock)를 부르는 클라이언트.
 *
 * <p>AI 서버는 상태를 갖지 않는다 — 이 클라이언트도 요청·응답을 그대로 주고받을 뿐,
 * 작업 상태·재시도·결과 저장은 호출한 쪽(각 도메인의 AiTask 처리 로직)이 맡는다.
 *
 * <p>실패(422·503·타임아웃·연결 끊김)는 전부 {@link AiProviderException} 하나로 뭉뚱그린다 —
 * 호출부가 어차피 "재시도할지 FAILED로 남길지"만 판단하면 되고, 세부 원인은 로그로 충분하다.
 */
@Component
public class AiProviderClient {

    private final AiProviderProperties properties;
    private final RestClient restClient;

    public AiProviderClient(AiProviderProperties properties) {
        this.properties = properties;
        // HTTP/2 를 기본으로 시도하면 uvicorn(h11) 이 h2c 업그레이드 요청을 못 알아듣고 body 가 깨진다.
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(Duration.ofSeconds(5))
                        .build());
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        // RestClient.builder()의 기본 컨버터 자동 감지가 Jackson 3(JsonMapper)를 못 잡는 경우가 있어 명시한다.
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .messageConverters(converters -> {
                    converters.clear();
                    converters.add(new JacksonJsonHttpMessageConverter());
                })
                .build();
    }

    public PromptVersions promptVersions() {
        return get("/ai/prompts/versions", PromptVersions.class);
    }

    public PostingAnalysisResponse postingAnalysis(PostingAnalysisRequest request) {
        return post("/ai/posting-analysis", request, PostingAnalysisResponse.class);
    }

    public ExperienceIntakeResponse experienceIntake(ExperienceIntakeRequest request) {
        return post("/ai/experience-intake", request, ExperienceIntakeResponse.class);
    }

    public MatchResponse match(MatchRequest request) {
        return post("/ai/match", request, MatchResponse.class);
    }

    public DraftResponse draft(DraftRequest request) {
        return post("/ai/draft", request, DraftResponse.class);
    }

    private <T> T get(String path, Class<T> responseType) {
        try {
            RestClient.RequestHeadersSpec<?> spec = restClient.get().uri(properties.baseUrl() + path);
            authorize(spec);
            return spec.retrieve().body(responseType);
        } catch (RestClientException e) {
            throw new AiProviderException("AI 제공자 호출에 실패했습니다: " + path, e);
        }
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        try {
            RestClient.RequestBodySpec spec = restClient.post()
                    .uri(properties.baseUrl() + path)
                    .contentType(MediaType.APPLICATION_JSON);
            authorize(spec);
            return spec.body(body).retrieve().body(responseType);
        } catch (RestClientException e) {
            throw new AiProviderException("AI 제공자 호출에 실패했습니다: " + path, e);
        }
    }

    /** 토큰이 비어 있으면(로컬 개발) 헤더를 아예 붙이지 않는다 — AI 서버도 그때는 인증을 검사하지 않는다. */
    private void authorize(RestClient.RequestHeadersSpec<?> spec) {
        if (properties.internalToken() != null && !properties.internalToken().isBlank()) {
            spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.internalToken());
        }
    }
}
