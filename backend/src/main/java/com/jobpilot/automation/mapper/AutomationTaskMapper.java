package com.jobpilot.automation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobpilot.automation.domain.AutomationTaskEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AutomationTaskMapper extends BaseMapper<AutomationTaskEntity> { }
