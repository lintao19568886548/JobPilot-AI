package com.jobpilot.candidate.controller;

import com.jobpilot.candidate.dto.CandidateDtos.CandidateSkillCreateRequest;
import com.jobpilot.candidate.dto.CandidateDtos.CandidateSkillUpdateRequest;
import com.jobpilot.candidate.dto.CandidateDtos.CandidateSkillView;
import com.jobpilot.candidate.dto.CandidateDtos.SkillView;
import com.jobpilot.candidate.service.SkillService;
import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SkillController {

    private final SkillService service;

    public SkillController(SkillService service) {
        this.service = service;
    }

    @GetMapping({"/api/skills", "/api/v1/skills"})
    public ApiResponse<List<SkillView>> skills(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category) {
        return ApiResponse.success(service.listSkills(search, category));
    }

    @GetMapping({"/api/candidate/skills", "/api/v1/candidate-profile/skills"})
    public ApiResponse<List<CandidateSkillView>> candidateSkills() {
        return ApiResponse.success(service.listCandidateSkills(CurrentUser.require().userId()));
    }

    @PostMapping({"/api/candidate/skills", "/api/v1/candidate-profile/skills"})
    public ApiResponse<CandidateSkillView> create(@Valid @RequestBody CandidateSkillCreateRequest request) {
        return ApiResponse.success(service.create(CurrentUser.require().userId(), request));
    }

    @PutMapping({"/api/candidate/skills/{id}", "/api/v1/candidate-profile/skills/{id}"})
    public ApiResponse<CandidateSkillView> update(
            @PathVariable String id,
            @Valid @RequestBody CandidateSkillUpdateRequest request) {
        return ApiResponse.success(service.update(CurrentUser.require().userId(), id, request));
    }

    @DeleteMapping({"/api/candidate/skills/{id}", "/api/v1/candidate-profile/skills/{id}"})
    public ApiResponse<Void> delete(@PathVariable String id) {
        service.delete(CurrentUser.require().userId(), id);
        return ApiResponse.success(null);
    }
}

