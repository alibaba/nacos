package com.alibaba.nacos.config.server.service.repository.external;

import com.alibaba.nacos.config.server.configuration.EmbeddedPostgresConfiguration;
import com.alibaba.nacos.config.server.model.*;
import com.alibaba.nacos.config.server.service.repository.extrnal.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.sql.Timestamp;
import java.util.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {MockServletContext.class, EmbeddedPostgresConfiguration.class})
@TestPropertySource(locations = "classpath:application-postgres.properties")
public class ExternalCommonPersistServiceTest {

    @InjectMocks
    private ExternalCommonPersistServiceImpl commonPersistService;
    @InjectMocks
    private ExternalConfigInfoAggrPersistServiceImpl configInfoAggrPersistService;
    @InjectMocks
    private ExternalConfigInfoBetaPersistServiceImpl configInfoBetaPersistService;
    @InjectMocks
    private ExternalConfigInfoPersistServiceImpl configInfoPersistService;
    @InjectMocks
    private ExternalConfigInfoTagPersistServiceImpl configInfoTagPersistService;
    @InjectMocks
    private ExternalHistoryConfigInfoPersistServiceImpl historyConfigInfoPersistService;

    @Test
    public void testCommonPersistService() {
        final String kp = "kp";
        final String tenantId = "tenant-0";
        final String tenantName = "tenant-0.name";
        final String tenantNameNew = "tenant-0.name-new";
        final String tenantDesc = "tenant-0.description";
        final String createResource = "create-resource";
        commonPersistService.insertTenantInfoAtomic(kp, tenantId, tenantName, tenantDesc, createResource,
            System.currentTimeMillis());
        List<TenantInfo> tenants = commonPersistService.findTenantByKp("kp");
        assert null != tenants && tenants.size() == 1;
        TenantInfo tenant = tenants.get(0);
        assert tenantId.equals(tenant.getTenantId());
        assert tenantName.equals(tenant.getTenantName());
        assert tenantDesc.equals(tenant.getTenantDesc());
        tenant = commonPersistService.findTenantByKp(kp, tenantId);
        assert tenantId.equals(tenant.getTenantId());
        assert tenantName.equals(tenant.getTenantName());
        assert tenantDesc.equals(tenant.getTenantDesc());
        commonPersistService.updateTenantNameAtomic(kp, tenantId, tenantNameNew, tenantDesc);
        tenant = commonPersistService.findTenantByKp(kp, tenantId);
        assert tenantId.equals(tenant.getTenantId());
        assert tenantNameNew.equals(tenant.getTenantName());
        assert tenantDesc.equals(tenant.getTenantDesc());
        assert 1 == commonPersistService.tenantInfoCountByTenantId(tenantId);
        commonPersistService.removeTenantInfoAtomic(kp, tenantId);
        assert 0 == commonPersistService.tenantInfoCountByTenantId(tenantId);
    }

    @Test
    public void testConfigInfoPersistService() {
        final String srcIp = "0.0.0.0";
        final String srcUser = "unit-test";
        final String dataId = "data-id";
        final String tenant = "tenant";
        final String group = "group";
        final String content = "test:\n  enabled: true";
        final String contentNew = "test:\n  enabled: false";
        final String appName = "app@unit-test";
        final ConfigInfo ci = new ConfigInfo(dataId, group, tenant, appName, content);
        final ConfigInfo ciNew = new ConfigInfo(dataId, group, tenant, appName, contentNew);
        final Map<String, Object> config = new HashMap<>(Collections.singletonMap("key", "value"));
        ConfigInfo info;
        assert 0 == configInfoPersistService.configInfoCount();
        configInfoPersistService.addConfigInfo(srcIp, srcUser, ci, new Timestamp(System.currentTimeMillis()), new HashMap<>(), false);
        configInfoPersistService.insertOrUpdate(srcIp, srcUser,ci,new Timestamp(System.currentTimeMillis()), new HashMap<>());
        configInfoPersistService.insertOrUpdate(srcIp, srcUser,ci,new Timestamp(System.currentTimeMillis()), new HashMap<>(), false);
        assert configInfoPersistService.insertOrUpdateCas(srcIp, srcUser,ci,new Timestamp(System.currentTimeMillis()), new HashMap<>());
        assert configInfoPersistService.insertOrUpdateCas(srcIp, srcUser,ciNew,new Timestamp(System.currentTimeMillis()), config, false);
        info = configInfoPersistService.findConfigInfo(dataId, group, tenant);
        assert null != info;
        assert ciNew.toString().equals(info.toString());
        info = configInfoPersistService.findConfigInfo(info.getId());
        assert null != info;
        assert ciNew.toString().equals(info.toString());
        configInfoPersistService.removeConfigInfo(dataId, group, tenant, srcIp, srcUser);
        assert 0 == configInfoPersistService.configInfoCount();
        final long configId = 1L;
        final long id = configInfoPersistService.addConfigInfoAtomic(configId, srcIp, srcUser, ciNew, new Timestamp(System.currentTimeMillis()), config);
        info = configInfoPersistService.findConfigInfo(id);
        assert null != info;
        assert ciNew.toString().equals(info.toString());
        ConfigAllInfo all = configInfoPersistService.findConfigAllInfo(dataId, group, tenant);
        assert null != all;
        assert srcIp.equals(all.getCreateIp());
        assert srcUser.equals(all.getCreateUser());
        Page<ConfigInfo> page = configInfoPersistService.findAllConfigInfo(1, Integer.MAX_VALUE, tenant);
        assert null != page;
        assert 1 == page.getTotalCount();
        assert page.getPagesAvailable() == 1;
        assert null != page.getPageItems();
        assert 1 == page.getPageItems().size();
        info = page.getPageItems().get(0);
        assert contentNew.equals(info.getContent());
        assert dataId.equals(info.getDataId());
        assert info.getAppName().equals(appName);
        assert info.getTenant().equals(tenant);
        List<ConfigAllInfo> cis = configInfoPersistService.findAllConfigInfo4Export(dataId, group, tenant, appName, Collections.singletonList(id));
        assert null != cis;
        assert 1 == cis.size();
        info = cis.get(0);
        assert info.getTenant().equals(tenant);
        assert info.getContent().equals(contentNew);
        assert info.getAppName().equals(appName);
        assert info.getGroup().equals(group);
        assert info.getDataId().equals(dataId);
        Page<ConfigInfoWrapper> pageFragments = configInfoPersistService.findAllConfigInfoFragment(0, Integer.MAX_VALUE);
        assert null != pageFragments;
        assert pageFragments.getTotalCount() == 1;
        assert pageFragments.getPagesAvailable() == 1;
        List<ConfigInfoWrapper> cws = pageFragments.getPageItems();
        assert null != cws;
        assert cws.size() == 1;
        ConfigInfoWrapper cw = cws.get(0);
        assert cw.getContent().equals(contentNew);
        assert cw.getId() == id;
        assert cw.getDataId().equals(dataId);
        assert cw.getAppName().equals(appName);
        assert cw.getGroup().equals(group);
        assert cw.getTenant().equals(tenant);
    }
}
