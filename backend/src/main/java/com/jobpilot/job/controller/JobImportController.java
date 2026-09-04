package com.jobpilot.job.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.security.CurrentUser;
import com.jobpilot.job.dto.JobDtos.CreateResult;
import com.jobpilot.job.dto.JobDtos.ExtensionCaptureRequest;
import com.jobpilot.job.dto.JobDtos.ImportErrorView;
import com.jobpilot.job.dto.JobDtos.ImportTaskView;
import com.jobpilot.job.dto.JobDtos.UrlImportRequest;
import com.jobpilot.job.importer.JobImportService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class JobImportController {
    private static final Set<String> EXTENSION_FIELDS=Set.of("platform","pageUrl","capturedAt","userInitiated","adapterVersion","visibleFields","contentHash");
    private static final Set<String> FORBIDDEN=Set.of("cookies","cookie","authorization","localStorage","sessionStorage","hiddenFields","browserFingerprint","captchaData");
    private final JobImportService service;
    private final ObjectMapper objectMapper;
    public JobImportController(JobImportService service,ObjectMapper objectMapper){this.service=service;this.objectMapper=objectMapper;}

    @PostMapping({"/api/job-imports/url","/api/v1/job-imports/url"})
    public ApiResponse<ImportTaskView> url(@Valid @RequestBody UrlImportRequest request){return ApiResponse.success(service.importUrl(CurrentUser.require().userId(),request));}

    @PostMapping(value={"/api/job-imports/files","/api/v1/job-imports/files"},consumes="multipart/form-data")
    public ApiResponse<ImportTaskView> file(@RequestPart("file") MultipartFile file,@RequestHeader("Idempotency-Key") String key){return ApiResponse.success(service.importFile(CurrentUser.require().userId(),file,key));}

    @GetMapping({"/api/job-imports/{id}","/api/v1/job-imports/{id}"})
    public ApiResponse<ImportTaskView> task(@PathVariable String id){return ApiResponse.success(service.getTask(CurrentUser.require().userId(),id));}
    @GetMapping({"/api/job-imports/{id}/errors","/api/v1/job-imports/{id}/errors"})
    public ApiResponse<List<ImportErrorView>> errors(@PathVariable String id){return ApiResponse.success(service.errors(CurrentUser.require().userId(),id));}

    @PostMapping({"/api/extension/job-captures","/api/v1/extension/job-captures"})
    public ApiResponse<CreateResult> extension(@RequestBody JsonNode body){
        body.fieldNames().forEachRemaining(name->{if(FORBIDDEN.contains(name)||!EXTENSION_FIELDS.contains(name))throw new BusinessException(4002221,"Extension capture contains a forbidden field",HttpStatus.BAD_REQUEST);});
        ExtensionCaptureRequest request=objectMapper.convertValue(body,ExtensionCaptureRequest.class);
        return ApiResponse.success(service.importExtension(CurrentUser.require().userId(),request));
    }
}
