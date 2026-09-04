package com.jobpilot.interview.controller;

import static com.jobpilot.interview.dto.InterviewDtos.*;

import com.jobpilot.common.api.ApiResponse;
import com.jobpilot.common.security.CurrentUser;
import com.jobpilot.interview.service.InterviewAgentService;
import com.jobpilot.interview.service.InterviewQuestionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
public class InterviewQuestionController {
    private final InterviewQuestionService questions;
    private final InterviewAgentService agent;
    public InterviewQuestionController(InterviewQuestionService questions, InterviewAgentService agent) { this.questions = questions; this.agent = agent; }

    @PostMapping("/api/v1/interview-rounds/{id}/questions:predict")
    public ApiResponse<List<QuestionView>> predict(@PathVariable String id, @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.success(agent.predict(CurrentUser.require().userId(), id, key));
    }
    @PostMapping("/api/v1/interview-rounds/{id}/questions") public ApiResponse<QuestionView> create(@PathVariable String id, @Valid @RequestBody QuestionCreateRequest request) { return ApiResponse.success(questions.create(CurrentUser.require().userId(), id, request)); }
    @PutMapping("/api/v1/interview-questions/{id}") public ApiResponse<QuestionView> update(@PathVariable String id, @Valid @RequestBody QuestionUpdateRequest request) { return ApiResponse.success(questions.update(CurrentUser.require().userId(), id, request)); }
    @PostMapping("/api/v1/interview-questions/{id}/answer-notes") public ApiResponse<AnswerNoteView> note(@PathVariable String id, @Valid @RequestBody AnswerNoteRequest request) { return ApiResponse.success(questions.addAnswerNote(CurrentUser.require().userId(), id, request)); }
}
