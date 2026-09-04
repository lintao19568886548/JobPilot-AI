package com.jobpilot.learning.controller;

import static com.jobpilot.learning.dto.LearningDtos.*;

import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import com.jobpilot.learning.service.FeedbackService;
import com.jobpilot.learning.service.LearningService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/learning")
public class LearningController {
    private final FeedbackService feedback; private final LearningService learning; private final int minimumSample;
    public LearningController(FeedbackService feedback,LearningService learning,@Value("${jobpilot.learning.minimum-sample:30}") int minimumSample){this.feedback=feedback;this.learning=learning;this.minimumSample=Math.max(30,minimumSample);}
    @PostMapping("/feedback:rebuild") public ApiResponse<FeedbackRebuildView> rebuild(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody FeedbackRebuildRequest request){return ApiResponse.success(feedback.rebuild(CurrentUser.require().userId(),key,request));}
    @GetMapping("/feedback/summary") public ApiResponse<FeedbackSummary> summary(){return ApiResponse.success(feedback.summary(CurrentUser.require().userId(),minimumSample));}
    @PostMapping("/models:train") public ApiResponse<TrainResult> train(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody TrainRequest request){return ApiResponse.success(learning.train(CurrentUser.require().userId(),key,request));}
    @GetMapping("/models") public ApiResponse<List<ModelView>> models(){return ApiResponse.success(learning.models(CurrentUser.require().userId()));}
    @GetMapping("/models/{id}") public ApiResponse<ModelView> model(@PathVariable String id){return ApiResponse.success(learning.model(CurrentUser.require().userId(),id));}
    @PostMapping("/models/{id}:shadow") public ApiResponse<ModelView> shadow(@PathVariable String id,@RequestHeader("Idempotency-Key") String key){return ApiResponse.success(learning.shadow(CurrentUser.require().userId(),id,key));}
    @PostMapping("/models/{id}:activate") public ApiResponse<ModelView> activate(@PathVariable String id,@Valid @RequestBody VersionRequest request){return ApiResponse.success(learning.activate(CurrentUser.require().userId(),id,request.version()));}
    @PostMapping("/models/{id}:rollback") public ApiResponse<RollbackView> rollback(@PathVariable String id,@Valid @RequestBody VersionRequest request){return ApiResponse.success(learning.rollback(CurrentUser.require().userId(),id,request.version()));}
    @GetMapping("/dashboard") public ApiResponse<LearningDashboard> dashboard(){return ApiResponse.success(learning.dashboard(CurrentUser.require().userId()));}
}
