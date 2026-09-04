package com.jobpilot.extension.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobpilot.extension.domain.ExtensionPairingCodeEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ExtensionPairingCodeMapper extends BaseMapper<ExtensionPairingCodeEntity> {
    @Update("UPDATE extension_pairing_codes SET used_at=#{usedAt}, used_by_device_id=#{deviceId}, version=version+1 "
            + "WHERE id=#{id} AND used_at IS NULL AND expires_at>#{usedAt}")
    int consume(@Param("id") Long id, @Param("deviceId") Long deviceId, @Param("usedAt") LocalDateTime usedAt);
}
