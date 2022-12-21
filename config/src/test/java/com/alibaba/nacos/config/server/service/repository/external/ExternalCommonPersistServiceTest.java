package com.alibaba.nacos.config.server.service.repository.external;

import com.alibaba.nacos.config.server.configuration.EmbeddedPostgresConfiguration;
import com.alibaba.nacos.config.server.model.TenantInfo;
import com.alibaba.nacos.config.server.service.repository.extrnal.ExternalCommonPersistServiceImpl;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {MockServletContext.class, EmbeddedPostgresConfiguration.class})
@TestPropertySource(locations = "classpath:application-postgres.properties")
public class ExternalCommonPersistServiceTest {

    @InjectMocks
    private ExternalCommonPersistServiceImpl commonPersistService;

    @Test
    public void testCommonPersistService() {
        commonPersistService.insertTenantInfoAtomic("kp", "tenant", "租户名称", "租户描述", "createResource",
            System.currentTimeMillis());
        List<TenantInfo> tenants = commonPersistService.findTenantByKp("kp");
        assert null != tenants && tenants.size() == 1;
        TenantInfo tenant = tenants.get(0);
        assert "tenant".equals(tenant.getTenantId());
        assert "租户名称".equals(tenant.getTenantName());
        assert "租户描述".equals(tenant.getTenantDesc());
    }
}
