package com.jobpilot.job.importer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.config.JobImportProperties;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.job.domain.JobImportErrorEntity;
import com.jobpilot.job.domain.JobImportTaskEntity;
import com.jobpilot.job.dto.JobDtos.CreateResult;
import com.jobpilot.job.dto.JobDtos.ExtensionCaptureRequest;
import com.jobpilot.job.dto.JobDtos.ImportErrorView;
import com.jobpilot.job.dto.JobDtos.ImportTaskView;
import com.jobpilot.job.dto.JobDtos.JobCreateRequest;
import com.jobpilot.job.dto.JobDtos.UrlImportRequest;
import com.jobpilot.job.mapper.JobImportErrorMapper;
import com.jobpilot.job.mapper.JobImportTaskMapper;
import com.jobpilot.job.normalization.JobNormalizationService;
import com.jobpilot.job.service.JobService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class JobImportService {
    private static final Set<String> ALLOWED_COLUMNS = Set.of("title","companyName","city","salaryText","education","experienceMinYears","experienceMaxYears","graduateYear","jobType","description","platform","platformJobId","jobUrl","publishAt","industry","companySize");
    private static final Set<String> VISIBLE_FIELDS = Set.of("jobTitle","companyName","salaryText","city","descriptionText","platformJobId","publishAt");
    private final JobImportTaskMapper taskMapper;
    private final JobImportErrorMapper errorMapper;
    private final JobService jobService;
    private final JobImportProperties properties;
    private final ObjectMapper objectMapper;
    private final JsonCodec json;
    private final JobNormalizationService normalization;
    private final AuditService audit;
    private final HttpClient httpClient;

    public JobImportService(JobImportTaskMapper taskMapper, JobImportErrorMapper errorMapper, JobService jobService,
                            JobImportProperties properties, ObjectMapper objectMapper, JsonCodec json,
                            JobNormalizationService normalization, AuditService audit) {
        this.taskMapper=taskMapper; this.errorMapper=errorMapper; this.jobService=jobService; this.properties=properties;
        this.objectMapper=objectMapper; this.json=json; this.normalization=normalization; this.audit=audit;
        this.httpClient=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public ImportTaskView importFile(Long userId, MultipartFile file, String idempotencyKey) {
        if (file == null || file.isEmpty()) throw new BusinessException(4002201,"Import file is empty",HttpStatus.BAD_REQUEST);
        if (file.getSize() > properties.getMaxFileBytes()) throw new BusinessException(4132202,"Import file is too large",HttpStatus.PAYLOAD_TOO_LARGE);
        String name = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        String extension = name.toLowerCase(Locale.ROOT).endsWith(".csv") ? "CSV" : name.toLowerCase(Locale.ROOT).endsWith(".xlsx") ? "EXCEL" : null;
        if (extension == null) throw new BusinessException(4002203,"Only CSV and XLSX files are supported",HttpStatus.BAD_REQUEST);
        String key = requiredKey(idempotencyKey);
        JobImportTaskEntity existing = byIdempotency(userId,key);
        if(existing!=null)return view(existing);
        try {
            byte[] bytes=file.getBytes(); String hash=normalization.sha256(new String(bytes,StandardCharsets.ISO_8859_1));
            JobImportTaskEntity task=createTask(userId,extension,null,name,hash,key);
            List<Map<String,String>> rows="CSV".equals(extension)?readCsv(bytes):readXlsx(bytes);
            processRows(userId,task,rows,extension); return view(taskMapper.selectById(task.getId()));
        } catch(IOException exception){throw new BusinessException(4002204,"Import file could not be read",HttpStatus.BAD_REQUEST);}
    }

    public ImportTaskView importUrl(Long userId, UrlImportRequest request) {
        if(!Boolean.TRUE.equals(request.userInitiated()))throw new BusinessException(4002210,"URL import must be user initiated",HttpStatus.BAD_REQUEST);
        JobImportTaskEntity existing=byIdempotency(userId,request.idempotencyKey()); if(existing!=null)return view(existing);
        JobImportTaskEntity task=createTask(userId,"URL",request.url(),null,null,request.idempotencyKey());
        try {
            FetchedPage page=fetch(request.url()); Document document=Jsoup.parse(page.body(),page.url());
            String title=textOr(document.selectFirst("h1"),document.title());
            if(title==null||title.isBlank())title="Imported Job";
            String company=document.select("meta[property=og:site_name]").attr("content");
            if(company.isBlank())company=URI.create(page.url()).getHost();
            String description=document.body()==null?document.text():document.body().text();
            if(description.length()>100_000)description=description.substring(0,100_000);
            Map<String,Object> values=new LinkedHashMap<>(); values.put("title",title);values.put("companyName",company);values.put("description",description);
            values.put("platform",blankDefault(request.platform(),"URL"));values.put("platformJobId",request.platformJobId());values.put("sourceType","URL");
            values.put("jobUrl",page.url());values.put("userInitiated",true);values.put("parseAfterCreate",true);
            CreateResult result=jobService.create(userId,objectMapper.convertValue(values,JobCreateRequest.class));
            finish(task,1,1,0,List.of()); audit.record(userId,"JOB_IMPORT_COMPLETE","JOB_IMPORT",task.getPublicId());
            return view(taskMapper.selectById(task.getId()));
        } catch(Exception exception){
            saveError(task,1,"JOB_URL_FETCH_FAILED",safeMessage(exception),"url",request.url()); finish(task,1,0,1,List.of("JOB_URL_FETCH_FAILED"));
            audit.record(userId,"JOB_IMPORT_FAILED","JOB_IMPORT",task.getPublicId());
            if(exception instanceof BusinessException business)throw business;
            throw new BusinessException(4002211,"Job URL could not be imported",HttpStatus.BAD_REQUEST);
        }
    }

    public CreateResult importExtension(Long userId, ExtensionCaptureRequest request) {
        if(!Boolean.TRUE.equals(request.userInitiated()))throw new BusinessException(4002220,"Extension capture must be user initiated",HttpStatus.BAD_REQUEST);
        Map<String,Object> visible=request.visibleFields()==null?Map.of():request.visibleFields();
        for(String field:visible.keySet())if(!VISIBLE_FIELDS.contains(field))throw new BusinessException(4002221,"Extension capture contains a forbidden field",HttpStatus.BAD_REQUEST);
        String title=string(visible.get("jobTitle"));String company=string(visible.get("companyName"));String description=string(visible.get("descriptionText"));
        if(blank(title)||blank(company)||blank(description))throw new ValidationException("visibleFields must contain jobTitle, companyName, and descriptionText");
        Map<String,Object> values=new LinkedHashMap<>();values.put("title",title);values.put("companyName",company);values.put("city",string(visible.get("city")));
        values.put("salaryText",string(visible.get("salaryText")));values.put("description",description);values.put("platform",request.platform());
        values.put("platformJobId",string(visible.get("platformJobId")));values.put("sourceType","EXTENSION");values.put("jobUrl",request.pageUrl());
        values.put("publishAt",visible.get("publishAt"));values.put("userInitiated",true);values.put("parseAfterCreate",true);
        CreateResult result=jobService.create(userId,objectMapper.convertValue(values,JobCreateRequest.class));
        audit.record(userId,"JOB_IMPORT_COMPLETE","JOB",result.job().job().id());return result;
    }

    public ImportTaskView getTask(Long userId,String publicId){return view(ownedTask(userId,publicId));}
    public List<ImportErrorView> errors(Long userId,String publicId){JobImportTaskEntity task=ownedTask(userId,publicId);return errorMapper.selectList(new LambdaQueryWrapper<JobImportErrorEntity>().eq(JobImportErrorEntity::getImportTaskId,task.getId()).orderByAsc(JobImportErrorEntity::getRowNumber)).stream().map(e->new ImportErrorView(e.getPublicId(),e.getRowNumber(),e.getErrorCode(),e.getErrorMessage(),e.getFieldName(),e.getRawPreview(),e.getCreatedAt())).toList();}

    private void processRows(Long userId,JobImportTaskEntity task,List<Map<String,String>> rows,String type){int success=0;int failure=0;List<String> codes=new ArrayList<>();int row=1;for(Map<String,String> values:rows){row++;try{validateRow(values);Map<String,Object> payload=new LinkedHashMap<>(values);payload.put("sourceType",type);payload.put("userInitiated",true);payload.put("parseAfterCreate",true);jobService.create(userId,objectMapper.convertValue(payload,JobCreateRequest.class));success++;}catch(Exception exception){failure++;String code="JOB_IMPORT_ROW_INVALID";codes.add(code);saveError(task,row,code,safeMessage(exception),null,preview(values));}}finish(task,rows.size(),success,failure,codes);audit.record(userId,failure==0?"JOB_IMPORT_COMPLETE":"JOB_IMPORT_COMPLETE","JOB_IMPORT",task.getPublicId());}

    private List<Map<String,String>> readCsv(byte[] bytes)throws IOException{CSVFormat format=CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).get();List<Map<String,String>> rows=new ArrayList<>();try(InputStreamReader reader=new InputStreamReader(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8)){Iterable<CSVRecord> records=format.parse(reader);for(CSVRecord record:records){if(rows.size()>=properties.getMaxRows())throw new ValidationException("Import exceeds maximum row count");Map<String,String> row=new LinkedHashMap<>();for(String header:record.toMap().keySet()){if(ALLOWED_COLUMNS.contains(header))row.put(header,limited(record.get(header)));}rows.add(row);}}return rows;}
    private List<Map<String,String>> readXlsx(byte[] bytes)throws IOException{ZipSecureFile.setMinInflateRatio(0.01);List<Map<String,String>> result=new ArrayList<>();try(XSSFWorkbook workbook=new XSSFWorkbook(new ByteArrayInputStream(bytes))){var sheet=workbook.getSheetAt(0);Row header=sheet.getRow(sheet.getFirstRowNum());if(header==null)throw new ValidationException("XLSX header is missing");DataFormatter formatter=new DataFormatter();List<String> headers=new ArrayList<>();for(Cell cell:header)headers.add(formatter.formatCellValue(cell).trim());for(int i=header.getRowNum()+1;i<=sheet.getLastRowNum();i++){if(result.size()>=properties.getMaxRows())throw new ValidationException("Import exceeds maximum row count");Row source=sheet.getRow(i);if(source==null)continue;Map<String,String> row=new LinkedHashMap<>();for(int c=0;c<headers.size();c++){String h=headers.get(c);if(ALLOWED_COLUMNS.contains(h))row.put(h,limited(formatter.formatCellValue(source.getCell(c))));}result.add(row);}}return result;}
    private String limited(String value){String result=value==null?"":value.trim();if(result.length()>properties.getMaxCellLength())throw new ValidationException("Cell exceeds maximum length");return result;}
    private void validateRow(Map<String,String> row){if(blank(row.get("title")))throw new ValidationException("title is required");if(blank(row.get("companyName")))throw new ValidationException("companyName is required");if(blank(row.get("description")))throw new ValidationException("description is required");}

    private FetchedPage fetch(String initial)throws Exception{URI current=URI.create(initial);for(int redirect=0;redirect<=properties.getUrlMaxRedirects();redirect++){validatePublicUri(current);HttpRequest request=HttpRequest.newBuilder(current).timeout(Duration.ofSeconds(12)).header("User-Agent","JobPilotAI/0.2 user-initiated-import").header("Accept","text/html,text/plain;q=0.9").GET().build();HttpResponse<java.io.InputStream> response=httpClient.send(request,HttpResponse.BodyHandlers.ofInputStream());int status=response.statusCode();if(status>=300&&status<400){String location=response.headers().firstValue("Location").orElseThrow(()->new ValidationException("Redirect location is missing"));current=current.resolve(location);continue;}if(status<200||status>=300)throw new ValidationException("Remote server returned HTTP "+status);String type=response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);if(!type.contains("text/html")&&!type.contains("text/plain"))throw new ValidationException("URL content type is not supported");byte[] body=response.body().readNBytes(properties.getUrlMaxBytes()+1);if(body.length>properties.getUrlMaxBytes())throw new ValidationException("URL response is too large");return new FetchedPage(current.toString(),new String(body,StandardCharsets.UTF_8));}throw new ValidationException("URL has too many redirects");}
    private void validatePublicUri(URI uri)throws Exception{if(!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null)throw new BusinessException(4002212,"Job URL is invalid",HttpStatus.BAD_REQUEST);String host=uri.getHost().toLowerCase(Locale.ROOT);if(host.equals("localhost")||host.endsWith(".localhost"))throw new BusinessException(4002213,"Job URL is blocked",HttpStatus.BAD_REQUEST);for(InetAddress address:InetAddress.getAllByName(host)){byte[] b=address.getAddress();boolean carrier=b.length==4&&(b[0]&255)==100&&((b[1]&255)>=64&&(b[1]&255)<=127);boolean metadata=b.length==4&&(b[0]&255)==169&&(b[1]&255)==254;boolean ula=b.length==16&&((b[0]&0xfe)==0xfc);if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isSiteLocalAddress()||address.isLinkLocalAddress()||address.isMulticastAddress()||carrier||metadata||ula)throw new BusinessException(4002213,"Job URL is blocked",HttpStatus.BAD_REQUEST);}}

    private JobImportTaskEntity createTask(Long userId,String type,String url,String name,String hash,String key){JobImportTaskEntity t=new JobImportTaskEntity();t.setUserId(userId);t.setImportType(type);t.setSourceUrl(url);t.setFileName(name);t.setFileHash(hash);t.setIdempotencyKey(key);t.setStatus("PROCESSING");t.setTotalCount(0);t.setSuccessCount(0);t.setFailureCount(0);t.setErrorSummaryJson("[]");taskMapper.insert(t);audit.record(userId,"JOB_IMPORT_START","JOB_IMPORT",t.getPublicId());return t;}
    private void finish(JobImportTaskEntity t,int total,int success,int failure,List<String> codes){t.setTotalCount(total);t.setSuccessCount(success);t.setFailureCount(failure);t.setStatus(failure==0?"COMPLETED":success==0?"FAILED":"PARTIAL_SUCCESS");t.setErrorSummaryJson(json.write(codes.stream().distinct().toList()));taskMapper.updateById(t);}
    private void saveError(JobImportTaskEntity t,int row,String code,String message,String field,String preview){JobImportErrorEntity e=new JobImportErrorEntity();e.setImportTaskId(t.getId());e.setRowNumber(row);e.setErrorCode(code);e.setErrorMessage(message.length()>500?message.substring(0,500):message);e.setFieldName(field);e.setRawPreview(preview==null?null:preview.substring(0,Math.min(preview.length(),1000)));errorMapper.insert(e);}
    private JobImportTaskEntity byIdempotency(Long userId,String key){return taskMapper.selectOne(new LambdaQueryWrapper<JobImportTaskEntity>().eq(JobImportTaskEntity::getUserId,userId).eq(JobImportTaskEntity::getIdempotencyKey,key).last("LIMIT 1"));}
    private JobImportTaskEntity ownedTask(Long userId,String id){JobImportTaskEntity t=taskMapper.selectOne(new LambdaQueryWrapper<JobImportTaskEntity>().eq(JobImportTaskEntity::getUserId,userId).eq(JobImportTaskEntity::getPublicId,id).last("LIMIT 1"));if(t==null)throw new ResourceNotFoundException("Job import");return t;}
    private ImportTaskView view(JobImportTaskEntity t){return new ImportTaskView(t.getPublicId(),t.getImportType(),t.getSourceUrl(),t.getFileName(),t.getFileHash(),t.getIdempotencyKey(),t.getStatus(),t.getTotalCount(),t.getSuccessCount(),t.getFailureCount(),json.readNode(t.getErrorSummaryJson()),t.getCreatedAt(),t.getUpdatedAt());}
    private String requiredKey(String key){if(blank(key))throw new ValidationException("Idempotency-Key is required");return key.trim();}
    private String preview(Map<String,String> row){return row.toString().replaceAll("(?i)(password|token|cookie|authorization)=[^,}]+","$1=[REDACTED]");}
    private String safeMessage(Exception e){return e instanceof BusinessException||e instanceof ValidationException?e.getMessage():"Import row could not be processed";}
    private String textOr(org.jsoup.nodes.Element element,String fallback){String value=element==null?fallback:element.text();return value==null?null:value.trim();}
    private String blankDefault(String value,String fallback){return blank(value)?fallback:value.trim();}
    private String string(Object value){return value==null?null:String.valueOf(value).trim();}
    private boolean blank(String value){return value==null||value.isBlank();}
    private record FetchedPage(String url,String body){}
}
