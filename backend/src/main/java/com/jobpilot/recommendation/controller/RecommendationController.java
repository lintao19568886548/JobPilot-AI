package com.jobpilot.recommendation.controller;

import static com.jobpilot.recommendation.dto.RecommendationDtos.*;

import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import com.jobpilot.recommendation.service.RecommendationService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecommendationController {
    private final RecommendationService service;

    public RecommendationController(RecommendationService service) {
        this.service = service;
    }

    @GetMapping({"/api/recommendations/capabilities", "/api/v1/recommendations/capabilities"})
    public ApiResponse<RecommendationCapabilities> capabilities() {
        return ApiResponse.success(service.capabilities());
    }

    @GetMapping({"/api/recommendations", "/api/v1/recommendations"})
    public ApiResponse<RecommendationPage> list(
            @RequestParam(required = false) String view,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String hardFilter,
            @RequestParam(required = false) String recommendation,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String companyId,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) BigDecimal salaryMin,
            @RequestParam(required = false) BigDecimal salaryMax,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime publishFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime publishTo,
            @RequestParam(required = false) String skill,
            @RequestParam(required = false) String sourcePlatform,
            @RequestParam(required = false) Boolean favorite,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String sort) {
        RecommendationQuery query = new RecommendationQuery(view, level, hardFilter, recommendation,
                city, companyId, companyName, salaryMin, salaryMax, publishFrom, publishTo,
                skill, sourcePlatform, favorite, keyword, cursor, limit, sort);
        return ApiResponse.success(service.list(CurrentUser.require().userId(), query));
    }

    @GetMapping({"/api/recommendations/{id}", "/api/v1/recommendations/{id}"})
    public ApiResponse<RecommendationDetail> get(@PathVariable String id) {
        return ApiResponse.success(service.get(CurrentUser.require().userId(), id));
    }

    @GetMapping({"/api/recommendations/{id}/events", "/api/v1/recommendations/{id}/events"})
    public ApiResponse<List<RecommendationEventView>> events(@PathVariable String id) {
        return ApiResponse.success(service.events(CurrentUser.require().userId(), id));
    }

    @PostMapping({"/api/recommendations/{id}:viewed", "/api/v1/recommendations/{id}:viewed"})
    public ApiResponse<RecommendationEventView> viewed(@PathVariable String id) {
        return ApiResponse.success(service.viewed(CurrentUser.require().userId(), id));
    }

    @PostMapping({"/api/recommendations/{id}:favorite", "/api/v1/recommendations/{id}:favorite"})
    public ApiResponse<RecommendationListItem> favorite(@PathVariable String id,
                                                         @Valid @RequestBody RecommendationActionRequest request) {
        return ApiResponse.success(service.favorite(CurrentUser.require().userId(), id, request.version()));
    }

    @PostMapping({"/api/recommendations/{id}:unfavorite", "/api/v1/recommendations/{id}:unfavorite"})
    public ApiResponse<RecommendationListItem> unfavorite(@PathVariable String id,
                                                           @Valid @RequestBody RecommendationActionRequest request) {
        return ApiResponse.success(service.unfavorite(CurrentUser.require().userId(), id, request.version()));
    }

    @PostMapping({"/api/recommendations/{id}:ignore", "/api/v1/recommendations/{id}:ignore"})
    public ApiResponse<RecommendationListItem> ignore(@PathVariable String id,
                                                       @Valid @RequestBody RecommendationIgnoreRequest request) {
        return ApiResponse.success(service.ignore(CurrentUser.require().userId(), id,
                request.version(), request.reason()));
    }

    @PostMapping({"/api/recommendations/{id}:restore", "/api/v1/recommendations/{id}:restore"})
    public ApiResponse<RecommendationListItem> restore(@PathVariable String id,
                                                        @Valid @RequestBody RecommendationActionRequest request) {
        return ApiResponse.success(service.restore(CurrentUser.require().userId(), id, request.version()));
    }
}
