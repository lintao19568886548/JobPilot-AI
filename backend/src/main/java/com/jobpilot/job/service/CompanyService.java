package com.jobpilot.job.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.job.domain.CompanyEntity;
import com.jobpilot.job.domain.JobEntity;
import com.jobpilot.job.dto.JobDtos.CompanyRequest;
import com.jobpilot.job.dto.JobDtos.CompanyUpdateRequest;
import com.jobpilot.job.dto.JobDtos.CompanyView;
import com.jobpilot.job.mapper.CompanyMapper;
import com.jobpilot.job.mapper.JobMapper;
import com.jobpilot.job.normalization.JobNormalizationService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;

@Service
public class CompanyService {
    private final CompanyMapper mapper;
    private final JobNormalizationService normalization;
    private final JsonCodec json;
    private final JobMapper jobMapper;
    private final AuditService audit;

    public CompanyService(CompanyMapper mapper, JobNormalizationService normalization, JsonCodec json,
                          JobMapper jobMapper, AuditService audit) {
        this.mapper = mapper; this.normalization = normalization; this.json = json;
        this.jobMapper = jobMapper; this.audit = audit;
    }

    @Transactional
    public CompanyEntity findOrCreate(String displayName, String website, String industry, String companySize,
                                      String financingStage, String headquartersCity, String verifiedSource) {
        String name = normalization.companyName(displayName);
        CompanyEntity existing = mapper.selectOne(new LambdaQueryWrapper<CompanyEntity>()
                .eq(CompanyEntity::getNormalizedName, name).last("LIMIT 1"));
        if (existing != null) return existing;
        CompanyEntity entity = new CompanyEntity();
        entity.setNormalizedName(name); entity.setDisplayName(displayName.trim()); entity.setWebsite(blankToNull(website));
        entity.setIndustry(blankToNull(industry)); entity.setCompanySize(blankToNull(companySize));
        entity.setFinancingStage(blankToNull(financingStage)); entity.setHeadquartersCity(blankToNull(headquartersCity));
        entity.setVerifiedSource(blankToNull(verifiedSource)); entity.setRiskFlagsJson("[]");
        mapper.insert(entity);
        return entity;
    }

    public List<CompanyView> list(String search) {
        LambdaQueryWrapper<CompanyEntity> query = new LambdaQueryWrapper<CompanyEntity>().orderByAsc(CompanyEntity::getDisplayName);
        if (search != null && !search.isBlank()) query.like(CompanyEntity::getDisplayName, search.trim());
        return mapper.selectList(query).stream().map(this::view).toList();
    }

    @Transactional
    public CompanyView create(Long userId, CompanyRequest request) {
        String normalized = normalization.companyName(request.displayName());
        CompanyEntity existing = mapper.selectOne(new LambdaQueryWrapper<CompanyEntity>()
                .eq(CompanyEntity::getNormalizedName, normalized).last("LIMIT 1"));
        if (existing != null) throw new BusinessException(4092101, "Company already exists", HttpStatus.CONFLICT);
        CompanyEntity entity = findOrCreate(request.displayName(), request.website(), request.industry(), request.companySize(),
                request.financingStage(), request.headquartersCity(), request.verifiedSource());
        entity.setDescription(blankToNull(request.description())); mapper.updateById(entity);
        audit.record(userId, "COMPANY_CREATE", "COMPANY", entity.getPublicId()); return view(entity);
    }

    @Transactional
    public CompanyView update(Long userId, String publicId, CompanyUpdateRequest request) {
        CompanyEntity entity = byPublicId(publicId);
        if (entity.getVersion() != request.version()) throw new ValidationException("Company was modified concurrently; reload and retry");
        String normalized = normalization.companyName(request.displayName());
        CompanyEntity duplicate = mapper.selectOne(new LambdaQueryWrapper<CompanyEntity>()
                .eq(CompanyEntity::getNormalizedName, normalized).ne(CompanyEntity::getId, entity.getId()).last("LIMIT 1"));
        if (duplicate != null) throw new BusinessException(4092101, "Company already exists", HttpStatus.CONFLICT);
        entity.setDisplayName(request.displayName().trim()); entity.setNormalizedName(normalized); entity.setWebsite(blankToNull(request.website()));
        entity.setIndustry(blankToNull(request.industry())); entity.setCompanySize(blankToNull(request.companySize()));
        entity.setFinancingStage(blankToNull(request.financingStage())); entity.setHeadquartersCity(blankToNull(request.headquartersCity()));
        entity.setDescription(blankToNull(request.description())); entity.setVerifiedSource(blankToNull(request.verifiedSource()));
        if (mapper.updateById(entity) != 1) throw new ValidationException("Company was modified concurrently; reload and retry");
        audit.record(userId, "COMPANY_UPDATE", "COMPANY", publicId); return view(entity);
    }

    @Transactional
    public void delete(Long userId, String publicId) {
        CompanyEntity entity = byPublicId(publicId);
        if (jobMapper.selectCount(new LambdaQueryWrapper<JobEntity>().eq(JobEntity::getCompanyId, entity.getId())) > 0) {
            throw new BusinessException(4092102, "Company with active jobs cannot be deleted", HttpStatus.CONFLICT);
        }
        mapper.deleteById(entity.getId()); audit.record(userId, "COMPANY_DELETE", "COMPANY", publicId);
    }

    public CompanyView get(String publicId) { return view(byPublicId(publicId)); }

    public CompanyView view(Long id) { return view(mapper.selectById(id)); }

    public CompanyView view(CompanyEntity e) {
        return new CompanyView(e.getPublicId(), e.getNormalizedName(), e.getDisplayName(), e.getWebsite(), e.getIndustry(),
                e.getCompanySize(), e.getFinancingStage(), e.getHeadquartersCity(), e.getDescription(), e.getVerifiedSource(),
                json.readNode(e.getRiskFlagsJson()), e.getVersion());
    }

    private CompanyEntity byPublicId(String publicId) {
        CompanyEntity entity = mapper.selectOne(new LambdaQueryWrapper<CompanyEntity>()
                .eq(CompanyEntity::getPublicId, publicId).last("LIMIT 1"));
        if (entity == null) throw new ResourceNotFoundException("Company");
        return entity;
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
