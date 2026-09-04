package com.jobpilot.job.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.candidate.domain.SkillEntity;
import com.jobpilot.candidate.mapper.SkillMapper;
import com.jobpilot.job.domain.SkillAliasEntity;
import com.jobpilot.job.mapper.SkillAliasMapper;
import com.jobpilot.job.normalization.JobNormalizationService;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class SkillAliasService {
    private final SkillAliasMapper aliasMapper;
    private final SkillMapper skillMapper;
    private final JobNormalizationService normalization;

    public SkillAliasService(SkillAliasMapper aliasMapper, SkillMapper skillMapper, JobNormalizationService normalization) {
        this.aliasMapper = aliasMapper; this.skillMapper = skillMapper; this.normalization = normalization;
    }

    public Optional<SkillEntity> resolve(String name) {
        String normalized = normalization.alias(name);
        SkillAliasEntity alias = aliasMapper.selectOne(new LambdaQueryWrapper<SkillAliasEntity>()
                .eq(SkillAliasEntity::getNormalizedAlias, normalized).eq(SkillAliasEntity::getActive, true).last("LIMIT 1"));
        if (alias != null) return Optional.ofNullable(skillMapper.selectById(alias.getSkillId()));
        SkillEntity exact = skillMapper.selectOne(new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getCanonicalName, name == null ? "" : name.trim().toLowerCase()).last("LIMIT 1"));
        if (exact != null) return Optional.of(exact);
        return skillMapper.selectList(new LambdaQueryWrapper<SkillEntity>().eq(SkillEntity::getStatus, "ACTIVE"))
                .stream()
                .filter(skill -> normalized.equals(normalization.alias(skill.getCanonicalName()))
                        || normalized.equals(normalization.alias(skill.getDisplayName())))
                .findFirst();
    }
}
