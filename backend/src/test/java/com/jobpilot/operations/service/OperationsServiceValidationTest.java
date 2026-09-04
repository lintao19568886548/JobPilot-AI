package com.jobpilot.operations.service;

import static com.jobpilot.operations.dto.OperationsDtos.RunRequest;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.config.AiServiceProperties;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.matching.mapper.AiCallLogMapper;
import com.jobpilot.operations.mapper.AiBudgetPolicyMapper;
import com.jobpilot.operations.mapper.OperationalRunMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;

class OperationsServiceValidationTest {
    private OperationsService service;

    @BeforeEach void setUp(){
        ObjectMapper mapper=new ObjectMapper();mapper.findAndRegisterModules();
        service=new OperationsService(mock(AiBudgetPolicyMapper.class),mock(OperationalRunMapper.class),mock(AiCallLogMapper.class),
                mock(AuditService.class),new JsonCodec(mapper),mapper,mock(JdbcTemplate.class),mock(RedisConnectionFactory.class),
                new AiServiceProperties(),new StandardEnvironment(),new SimpleMeterRegistry(),"127.0.0.1",19531);
    }

    @Test void rejectsUnknownRunTypeBeforePersistence(){
        RunRequest request=new RunRequest("REMOTE_SUBMIT","SUCCEEDED","batch-1","USER",LocalDateTime.now().minusSeconds(1),LocalDateTime.now(),Map.of(),"safe summary",null,null,0L,1L,null,"SCRIPT");
        assertThatThrownBy(()->service.createRun(1L,"key",request)).isInstanceOf(ValidationException.class).hasMessageContaining("Unsupported operational run type");
    }

    @Test void rejectsAbsoluteAndTraversingArtifactPaths(){
        RunRequest request=new RunRequest("BACKUP","SUCCEEDED","batch-1","USER",LocalDateTime.now().minusSeconds(1),LocalDateTime.now(),Map.of(),"safe summary","../.env",null,0L,1L,null,"SCRIPT");
        assertThatThrownBy(()->service.createRun(1L,"key",request)).isInstanceOf(ValidationException.class).hasMessageContaining("cannot traverse");
    }

    @Test void rejectsCredentialLikeSummary(){
        RunRequest request=new RunRequest("BACKUP","SUCCEEDED","batch-1","USER",LocalDateTime.now().minusSeconds(1),LocalDateTime.now(),Map.of(),"password=should-not-be-stored",null,null,0L,1L,null,"SCRIPT");
        assertThatThrownBy(()->service.createRun(1L,"key",request)).isInstanceOf(ValidationException.class).hasMessageContaining("credentials");
    }
}
