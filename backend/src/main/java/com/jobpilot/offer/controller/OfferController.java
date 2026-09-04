package com.jobpilot.offer.controller;

import static com.jobpilot.offer.dto.OfferDtos.*;

import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import com.jobpilot.offer.service.OfferDeadlineService;
import com.jobpilot.offer.service.OfferService;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
public class OfferController {
    private final OfferService offers; private final OfferDeadlineService deadlines;
    public OfferController(OfferService offers, OfferDeadlineService deadlines) { this.offers = offers; this.deadlines = deadlines; }
    @GetMapping("/api/v1/offers") public ApiResponse<OfferPage> list(@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20") int size, @RequestParam(required=false) String status,
            @RequestParam(required=false) String company, @RequestParam(required=false) String role,
            @RequestParam(required=false) String currency,
            @RequestParam(required=false) String applicationId,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return ApiResponse.success(offers.list(CurrentUser.require().userId(), page, size, status, company, role, currency, utc(from), utc(to), applicationId));
    }
    @PostMapping("/api/v1/offers") public ApiResponse<OfferView> create(@Valid @RequestBody OfferCreateRequest request) { return ApiResponse.success(offers.create(CurrentUser.require().userId(), request)); }
    @GetMapping("/api/v1/offers/{id}") public ApiResponse<OfferView> get(@PathVariable String id) { return ApiResponse.success(offers.get(CurrentUser.require().userId(), id)); }
    @PutMapping("/api/v1/offers/{id}") public ApiResponse<OfferView> update(@PathVariable String id, @Valid @RequestBody OfferUpdateRequest request) { return ApiResponse.success(offers.update(CurrentUser.require().userId(), id, request)); }
    @PostMapping({"/api/v1/offers/{id}/status","/api/v1/offers/{id}:status"}) public ApiResponse<OfferView> status(@PathVariable String id, @Valid @RequestBody OfferStatusRequest request) { return ApiResponse.success(offers.transition(CurrentUser.require().userId(), id, request)); }
    @DeleteMapping("/api/v1/offers/{id}") public ApiResponse<Void> delete(@PathVariable String id) { offers.delete(CurrentUser.require().userId(), id); return ApiResponse.success("deleted", null); }
    @GetMapping("/api/v1/offers/dashboard") public ApiResponse<OfferDashboardView> dashboard() { return ApiResponse.success(offers.dashboard(CurrentUser.require().userId())); }

    @GetMapping("/api/v1/offer-deadlines") public ApiResponse<List<DeadlineView>> deadlines(@RequestParam(required=false) String status) { return ApiResponse.success(deadlines.list(CurrentUser.require().userId(), status)); }
    @PostMapping("/api/v1/offer-deadlines") public ApiResponse<DeadlineView> createDeadline(@Valid @RequestBody DeadlineRequest request) { return ApiResponse.success(deadlines.create(CurrentUser.require().userId(), request)); }
    @PutMapping("/api/v1/offer-deadlines/{id}") public ApiResponse<DeadlineView> updateDeadline(@PathVariable String id, @Valid @RequestBody DeadlineUpdateRequest request) { return ApiResponse.success(deadlines.update(CurrentUser.require().userId(), id, request)); }
    @PostMapping({"/api/v1/offer-deadlines/{id}/done","/api/v1/offer-deadlines/{id}:done"}) public ApiResponse<DeadlineView> done(@PathVariable String id, @Valid @RequestBody VersionRequest request) { return ApiResponse.success(deadlines.change(CurrentUser.require().userId(), id, "DONE", request)); }
    @PostMapping({"/api/v1/offer-deadlines/{id}/cancel","/api/v1/offer-deadlines/{id}:cancel"}) public ApiResponse<DeadlineView> cancel(@PathVariable String id, @Valid @RequestBody VersionRequest request) { return ApiResponse.success(deadlines.change(CurrentUser.require().userId(), id, "CANCELLED", request)); }
    @DeleteMapping("/api/v1/offer-deadlines/{id}") public ApiResponse<Void> deleteDeadline(@PathVariable String id) { deadlines.delete(CurrentUser.require().userId(), id); return ApiResponse.success("deleted", null); }
    private static LocalDateTime utc(OffsetDateTime value) { return value == null ? null : LocalDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC); }
}
