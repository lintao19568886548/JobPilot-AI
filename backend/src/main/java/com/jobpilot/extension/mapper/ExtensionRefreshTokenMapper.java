package com.jobpilot.extension.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobpilot.extension.domain.ExtensionRefreshTokenEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ExtensionRefreshTokenMapper extends BaseMapper<ExtensionRefreshTokenEntity> {
    @Update("UPDATE extension_refresh_tokens SET revoked_at=#{now}, replaced_by_id=#{replacementId} "
            + "WHERE id=#{id} AND revoked_at IS NULL AND expires_at>#{now}")
    int rotate(@Param("id") Long id, @Param("replacementId") Long replacementId, @Param("now") LocalDateTime now);

    @Update("UPDATE extension_refresh_tokens SET revoked_at=#{now} WHERE device_id=#{deviceId} AND revoked_at IS NULL")
    int revokeDevice(@Param("deviceId") Long deviceId, @Param("now") LocalDateTime now);
}
