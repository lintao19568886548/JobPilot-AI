package com.jobpilot.resume.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobpilot.resume.domain.ResumeEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ResumeMapper extends BaseMapper<ResumeEntity> {

    @Select("SELECT * FROM resumes WHERE id = #{id} AND deleted_at IS NULL FOR UPDATE")
    ResumeEntity selectByIdForUpdate(@Param("id") Long id);
}

