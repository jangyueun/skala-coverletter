package com.team.careerfit.job.service;

import com.team.careerfit.job.repository.PostingMatchQueryRepository.ExperienceInput;
import com.team.careerfit.job.repository.PostingMatchQueryRepository.Requirement;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * 매칭 입력 해시 — {@code job_matches.input_hash}. 공고 요구 역량(id, weight)과 사용자 경험 태그(experience, competency,
 * strength)의 sha256 이다. 모델·프롬프트 버전은 넣지 않는다(JobMatch 주석).
 *
 * <p>읽는 쪽({@code PostingMatchService}: 저장된 결과가 stale 인지)과 쓰는 쪽({@code MatchTaskHandler}: 결과를 어떤 입력으로
 * 계산했는지)이 <b>같은 식</b>을 써야 한다. 한쪽만 고치면 결과가 영원히 stale 로 보여 매칭 작업이 계속 만들어진다.
 * 그래서 한 곳에 둔다. 리포지토리가 정렬해서 주므로(요구 역량은 competency id 순, 경험은 experience·competency 순)
 * 여기서 다시 정렬하지 않는다.
 */
public final class MatchInputHash {

    private MatchInputHash() {
    }

    public static String of(List<Requirement> requirements, List<ExperienceInput> experiences) {
        StringBuilder input = new StringBuilder();
        requirements.forEach(value -> input.append("R:")
                .append(value.competencyId()).append(':')
                .append(value.weight().stripTrailingZeros().toPlainString()).append(';'));
        experiences.forEach(value -> input.append("E:")
                .append(value.experienceId()).append(':')
                .append(value.competencyId()).append(':')
                .append(value.strength().stripTrailingZeros().toPlainString()).append(';'));
        return sha256(input.toString());
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
