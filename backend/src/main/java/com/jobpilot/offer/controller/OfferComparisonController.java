package com.jobpilot.offer.controller;

import static com.jobpilot.offer.dto.OfferDtos.*;

import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import com.jobpilot.offer.service.OfferComparisonService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/offer-comparisons","/api/v1/offers/comparisons"})
public class OfferComparisonController {
    private final OfferComparisonService service;
    public OfferComparisonController(OfferComparisonService service) { this.service = service; }
    @GetMapping public ApiResponse<List<ComparisonView>> list() { return ApiResponse.success(service.list(CurrentUser.require().userId())); }
    @GetMapping("/{id}") public ApiResponse<ComparisonView> get(@PathVariable String id) { return ApiResponse.success(service.get(CurrentUser.require().userId(), id)); }
    @PostMapping public ApiResponse<ComparisonView> create(@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ComparisonRequest request) { return ApiResponse.success(service.create(CurrentUser.require().userId(), key, request)); }
}
