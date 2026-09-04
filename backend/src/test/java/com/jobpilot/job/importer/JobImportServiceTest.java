package com.jobpilot.job.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.config.JobImportProperties;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.job.domain.JobImportTaskEntity;
import com.jobpilot.job.dto.JobDtos.ExtensionCaptureRequest;
import com.jobpilot.job.dto.JobDtos.UrlImportRequest;
import com.jobpilot.job.mapper.JobImportErrorMapper;
import com.jobpilot.job.mapper.JobImportTaskMapper;
import com.jobpilot.job.normalization.JobNormalizationService;
import com.jobpilot.job.service.JobService;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class JobImportServiceTest {
    private JobImportService service;
    private JobService jobService;

    @BeforeEach
    void setUp() {
        JobImportTaskMapper taskMapper = mock(JobImportTaskMapper.class);
        JobImportErrorMapper errorMapper = mock(JobImportErrorMapper.class);
        jobService = mock(JobService.class);
        AtomicReference<JobImportTaskEntity> saved = new AtomicReference<>();
        doAnswer(invocation -> {
            JobImportTaskEntity task = invocation.getArgument(0);
            task.setId(1L); task.setPublicId("import-1"); saved.set(task); return 1;
        }).when(taskMapper).insert(any(JobImportTaskEntity.class));
        when(taskMapper.selectById(1L)).thenAnswer(invocation -> saved.get());
        JobImportProperties properties = new JobImportProperties();
        service = new JobImportService(taskMapper, errorMapper, jobService, properties,
                new ObjectMapper().findAndRegisterModules(), new JsonCodec(new ObjectMapper()),
                new JobNormalizationService(), mock(AuditService.class));
    }

    @Test void csvContinuesAfterInvalidRow() {
        String csv = "title,companyName,description,platformJobId\nJava Engineer,Acme,要求熟悉 Java,1\n,Acme,missing title,2\n";
        var file = new MockMultipartFile("file", "jobs.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        var result = service.importFile(7L, file, "csv-key");
        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.status()).isEqualTo("PARTIAL_SUCCESS");
    }

    @Test void xlsxIsReallyParsed() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("jobs");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("title"); header.createCell(1).setCellValue("companyName"); header.createCell(2).setCellValue("description");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("Backend Engineer"); row.createCell(1).setCellValue("Acme"); row.createCell(2).setCellValue("Java and Redis");
            workbook.write(bytes);
        }
        var file = new MockMultipartFile("file", "jobs.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes.toByteArray());
        assertThat(service.importFile(7L, file, "xlsx-key").successCount()).isEqualTo(1);
    }

    @Test void unsupportedFileTypeIsRejected() {
        var file = new MockMultipartFile("file", "jobs.exe", "application/octet-stream", new byte[]{1});
        assertThatThrownBy(() -> service.importFile(7L, file, "key")).isInstanceOf(BusinessException.class);
    }

    @Test void localUrlIsBlockedBySsrfPolicy() {
        var request = new UrlImportRequest("http://127.0.0.1:8080/jobs/1", "LOCAL", "1", true, "url-key");
        assertThatThrownBy(() -> service.importUrl(7L, request)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("blocked");
    }

    @Test void extensionRejectsUnapprovedVisibleFields() {
        var request = new ExtensionCaptureRequest("DEMO", "https://example.com/jobs/1", null, true, "demo-v1",
                Map.of("jobTitle", "Java", "companyName", "Acme", "descriptionText", "Java", "cookie", "secret"), "a".repeat(64));
        assertThatThrownBy(() -> service.importExtension(7L, request)).isInstanceOf(BusinessException.class);
    }

    @Test void extensionRequiresCoreVisibleFields() {
        var request = new ExtensionCaptureRequest("DEMO", "https://example.com/jobs/1", null, true, "demo-v1",
                Map.of("jobTitle", "Java"), "a".repeat(64));
        assertThatThrownBy(() -> service.importExtension(7L, request)).isInstanceOf(ValidationException.class);
    }
}
