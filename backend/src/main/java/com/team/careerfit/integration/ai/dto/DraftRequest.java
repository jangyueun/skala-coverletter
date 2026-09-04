package com.team.careerfit.integration.ai.dto;

import java.math.BigDecimal;
import java.util.List;

/** {@code POST /ai/draft} 요청(docs/api-spec-v6.md §8). */
public record DraftRequest(DraftQuestion question, DraftPosting posting, List<DraftExperience> experiences) {

    public record DraftQuestion(String promptText, Integer lengthLimit) {
    }

    /**
     * 공고. 원문(content)과 요구 역량별 근거 문장을 같이 보낸다 — 초안이 담당 업무·인재상 문장에 경험을 맞대게 하려고.
     * 이름만 보내던 v1 은 회사명과 역량 이름을 문장에 끼워 넣는 데 그쳤다.
     */
    public record DraftPosting(String company, String position, String content, List<DraftRequirement> required) {
    }

    public record DraftRequirement(String name, BigDecimal weight, String evidenceLine) {
    }

    public record DraftExperience(String title, String situation, String task, String action, String result) {
    }
}
