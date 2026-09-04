package com.jobpilot.resume.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobpilot.resume.domain.ResumeVersionEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ResumeVersionMapper extends BaseMapper<ResumeVersionEntity> {

    @Select("SELECT COALESCE(MAX(version_number), 0) FROM resume_versions WHERE resume_id = #{resumeId} AND deleted_at IS NULL")
    int selectMaxVersionNumber(@Param("resumeId") Long resumeId);
}

