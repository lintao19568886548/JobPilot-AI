package com.jobpilot.tailoring.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.tailoring.domain.PromptTemplateEntity;
import com.jobpilot.tailoring.mapper.PromptTemplateMapper;
import org.springframework.stereotype.Service;

@Service
public class PromptTemplateService {
    private final PromptTemplateMapper mapper;
    public PromptTemplateService(PromptTemplateMapper mapper) { this.mapper = mapper; }

    public PromptTemplateEntity active(String key) {
        PromptTemplateEntity entity = mapper.selectOne(new LambdaQueryWrapper<PromptTemplateEntity>()
                .eq(PromptTemplateEntity::getTemplateKey, key).eq(PromptTemplateEntity::getActive, true)
                .orderByDesc(PromptTemplateEntity::getVersionNo).last("LIMIT 1"));
        if (entity == null) throw new ResourceNotFoundException("Active Prompt Template");
        return entity;
    }
}
