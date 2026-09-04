package com.team.careerlab.competency.service;

import com.team.careerlab.competency.dto.CompetencyResponse;
import com.team.careerlab.competency.entity.CompetencyCategory;
import com.team.careerlab.competency.exception.CompetencyException;
import com.team.careerlab.competency.repository.CompetencyQueryRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class CompetencyService {

    private final CompetencyQueryRepository competencies;
    private volatile List<CompetencyResponse> cached;

    public CompetencyService(CompetencyQueryRepository competencies) {
        this.competencies = competencies;
    }

    public List<CompetencyResponse> findAll(String categoryValue) {
        CompetencyCategory category = parseCategory(categoryValue);
        List<CompetencyResponse> dictionary = dictionary();
        if (category == null) {
            return dictionary;
        }
        return dictionary.stream()
                .filter(competency -> competency.category() == category)
                .toList();
    }

    private List<CompetencyResponse> dictionary() {
        List<CompetencyResponse> result = cached;
        if (result == null) {
            synchronized (this) {
                result = cached;
                if (result == null) {
                    result = List.copyOf(competencies.findAll());
                    cached = result;
                }
            }
        }
        return result;
    }

    private CompetencyCategory parseCategory(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return CompetencyCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw CompetencyException.invalidCategory();
        }
    }
}
