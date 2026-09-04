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
        return competencies.findAll();
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
