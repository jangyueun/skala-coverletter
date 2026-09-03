package com.team.careerfit.job.service;

import com.team.careerfit.job.dto.PostingDetailResponse;
import com.team.careerfit.job.dto.PostingListResponse;
import com.team.careerfit.job.dto.PostingListResponse.Essay;
import com.team.careerfit.job.dto.PostingListResponse.EssayState;
import com.team.careerfit.job.dto.PostingListResponse.Item;
import com.team.careerfit.job.dto.PostingListResponse.Match;
import com.team.careerfit.job.dto.PostingQuestionResponse;
import com.team.careerfit.job.exception.JobException;
import com.team.careerfit.job.repository.JobPostingDetailQueryRepository;
import com.team.careerfit.job.repository.JobPostingQueryRepository;
import com.team.careerfit.job.repository.JobPostingQueryRepository.Page;
import com.team.careerfit.job.repository.JobPostingQueryRepository.Row;
import com.team.careerfit.job.repository.JobPostingQueryRepository.SearchCondition;
import com.team.careerfit.job.repository.JobPostingQueryRepository.Sort;
import com.team.careerfit.job.repository.PostingQuestionQueryRepository;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobPostingService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final JobPostingQueryRepository jobPostings;
    private final JobPostingDetailQueryRepository postingDetails;
    private final PostingQuestionQueryRepository postingQuestions;

    public JobPostingService(
            JobPostingQueryRepository jobPostings,
            JobPostingDetailQueryRepository postingDetails,
            PostingQuestionQueryRepository postingQuestions) {
        this.jobPostings = jobPostings;
        this.postingDetails = postingDetails;
        this.postingQuestions = postingQuestions;
    }

    @Transactional(readOnly = true)
    public PostingDetailResponse findDetail(Long userId, Long postingId) {
        return postingDetails.findById(postingId, userId).orElseThrow(JobException::postingNotFound);
    }

    @Transactional(readOnly = true)
    public List<PostingQuestionResponse> findQuestions(Long userId, Long postingId) {
        return postingQuestions.findByPostingId(postingId, userId).orElseThrow(JobException::postingNotFound);
    }

    @Transactional(readOnly = true)
    public PostingListResponse findAll(
            Long userId,
            String query,
            List<Long> competencyIds,
            Boolean bookmarked,
            String sortValue,
            boolean includeClosed,
            int page,
            int size) {
        validate(competencyIds, page, size);
        Sort sort = parseSort(sortValue);
        String keyword = normalizeKeyword(query);

        Page result = jobPostings.findAll(new SearchCondition(
                userId,
                keyword,
                competencyIds,
                bookmarked,
                sort,
                includeClosed,
                page,
                size));

        return new PostingListResponse(
                result.rows().stream().map(this::toItem).toList(),
                page,
                size,
                result.totalCount());
    }

    private void validate(List<Long> competencyIds, int page, int size) {
        boolean hasInvalidCompetencyId = competencyIds.stream().anyMatch(id -> id == null || id < 1);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE || hasInvalidCompetencyId) {
            throw JobException.invalidListQuery();
        }
    }

    private Sort parseSort(String value) {
        try {
            return Sort.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw JobException.invalidListQuery();
        }
    }

    private String normalizeKeyword(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return query.trim().toLowerCase(Locale.ROOT);
    }

    private Item toItem(Row row) {
        Match match = row.matchScore() == null
                ? null
                : new Match(
                        row.matchScore().movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValue(),
                        row.verdict(),
                        row.coveredCompetencyNames(),
                        row.requiredCount());

        return new Item(
                row.id(),
                row.company(),
                row.position(),
                row.deadline().atZone(KOREA).toOffsetDateTime(),
                row.status(),
                row.bookmarked(),
                match,
                essay(row));
    }

    private Essay essay(Row row) {
        EssayState state;
        if (row.questionCount() == 0) {
            state = EssayState.NO_QUESTIONS;
        } else if (row.answeredCount() == 0) {
            state = EssayState.EMPTY;
        } else if (row.answeredCount() < row.questionCount()) {
            state = EssayState.WRITING;
        } else {
            state = EssayState.DONE;
        }
        return new Essay(state, row.answeredCount(), row.questionCount());
    }
}
