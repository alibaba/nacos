/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.model.SameConfigPolicy;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.config.server.auth.ConfigCloneSourceReadPermissionChecker;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.service.ConfigCloneService.ConfigCloneItem;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.core.namespace.repository.NamespacePersistService;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigCloneServiceTest {
    
    @Mock
    private ConfigInfoPersistService configInfoPersistService;
    
    @Mock
    private NamespacePersistService namespacePersistService;
    
    @Mock
    private ConfigCloneSourceReadPermissionChecker configCloneSourceReadPermissionChecker;
    
    private ConfigCloneService configCloneService;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new MockEnvironment());
        configCloneService = new ConfigCloneService(configInfoPersistService,
            namespacePersistService, configCloneSourceReadPermissionChecker);
    }
    
    @Test
    void testCloneConfigWithNoSelectedConfig() throws NacosException {
        Result<Map<String, Object>> actual = configCloneService.cloneConfig("public", "public",
            Collections.emptyList(), "srcUser", SameConfigPolicy.ABORT, "srcIp",
            "requestIpApp");
        
        assertEquals(ErrorCode.NO_SELECTED_CONFIG.getCode(), actual.getCode());
        verify(configInfoPersistService, never()).findAllConfigInfo4Export(any(), any(), any(),
            any(), anyList());
    }
    
    @Test
    void testCloneConfigWithOnlyNullCloneItems() throws NacosException {
        Result<Map<String, Object>> actual = configCloneService.cloneConfig("public", "public",
            Collections.singletonList(null), "srcUser", SameConfigPolicy.ABORT, "srcIp",
            "requestIpApp");
        
        assertEquals(ErrorCode.NO_SELECTED_CONFIG.getCode(), actual.getCode());
        verify(configInfoPersistService, never()).findAllConfigInfo4Export(any(), any(), any(),
            any(), anyList());
    }
    
    @Test
    void testCloneConfigWithSourceNamespaceNotExist() throws NacosException {
        when(namespacePersistService.tenantInfoCountByTenantId("sourceNamespace")).thenReturn(0);
        
        Result<Map<String, Object>> actual = configCloneService.cloneConfig("sourceNamespace",
            "targetNamespace", Collections.singletonList(cloneItem(1L, null, null)), "srcUser",
            SameConfigPolicy.ABORT, "srcIp", "requestIpApp");
        
        assertEquals(ErrorCode.NAMESPACE_NOT_EXIST.getCode(), actual.getCode());
        verify(configInfoPersistService, never()).findAllConfigInfo4Export(any(), any(), any(),
            any(), anyList());
    }
    
    @Test
    void testCloneConfigWithTargetNamespaceNotExist() throws NacosException {
        when(namespacePersistService.tenantInfoCountByTenantId("sourceNamespace")).thenReturn(1);
        when(namespacePersistService.tenantInfoCountByTenantId("targetNamespace")).thenReturn(0);
        
        Result<Map<String, Object>> actual = configCloneService.cloneConfig("sourceNamespace",
            "targetNamespace", Collections.singletonList(cloneItem(1L, null, null)), "srcUser",
            SameConfigPolicy.ABORT, "srcIp", "requestIpApp");
        
        assertEquals(ErrorCode.NAMESPACE_NOT_EXIST.getCode(), actual.getCode());
        verify(configInfoPersistService, never()).findAllConfigInfo4Export(any(), any(), any(),
            any(), anyList());
    }
    
    @Test
    void testCloneConfigWithDataEmpty() throws NacosException {
        when(namespacePersistService.tenantInfoCountByTenantId("tenant")).thenReturn(1);
        when(configInfoPersistService.findAllConfigInfo4Export(isNull(), isNull(), eq("tenant"),
            isNull(), eq(Collections.singletonList(1L)))).thenReturn(Collections.emptyList());
        
        Result<Map<String, Object>> actual = configCloneService.cloneConfig("tenant", "tenant",
            Collections.singletonList(cloneItem(1L, null, null)), "srcUser",
            SameConfigPolicy.ABORT, "srcIp", "requestIpApp");
        
        assertEquals(ErrorCode.DATA_EMPTY.getCode(), actual.getCode());
        verify(configInfoPersistService, never()).batchInsertOrUpdate(anyList(), anyString(),
            anyString(), any(), any());
    }
    
    @Test
    void testCloneConfigDeniedBySourceNamespaceReadPermission() throws NacosException {
        when(namespacePersistService.tenantInfoCountByTenantId("sourceNamespace")).thenReturn(1);
        when(namespacePersistService.tenantInfoCountByTenantId("targetNamespace")).thenReturn(1);
        doThrow(new AccessException("authorization failed"))
            .when(configCloneSourceReadPermissionChecker)
            .checkSourceReadPermission("sourceNamespace");
        
        assertThrows(AccessException.class,
            () -> configCloneService.cloneConfig("sourceNamespace", "targetNamespace",
                Collections.singletonList(cloneItem(1L, null, null)), "srcUser",
                SameConfigPolicy.ABORT, "srcIp", "requestIpApp"));
        
        verify(configInfoPersistService, never()).findAllConfigInfo4Export(any(), any(), any(),
            any(), anyList());
        verify(configInfoPersistService, never()).batchInsertOrUpdate(anyList(), anyString(),
            anyString(), any(), any());
    }
    
    @Test
    void testCloneConfigWithSourceAndTargetNamespace() throws NacosException {
        when(namespacePersistService.tenantInfoCountByTenantId("sourceNamespace")).thenReturn(1);
        when(namespacePersistService.tenantInfoCountByTenantId("targetNamespace")).thenReturn(1);
        when(configInfoPersistService.findAllConfigInfo4Export(isNull(), isNull(),
            eq("sourceNamespace"), isNull(), eq(Collections.singletonList(1L))))
            .thenReturn(Collections.singletonList(sourceConfig(1L)));
        Map<String, Object> saveResult = new HashMap<>();
        saveResult.put("succCount", 1);
        when(configInfoPersistService.batchInsertOrUpdate(anyList(), eq("srcUser"), eq("srcIp"),
            isNull(), eq(SameConfigPolicy.OVERWRITE))).thenReturn(saveResult);
        
        Result<Map<String, Object>> actual = configCloneService.cloneConfig("sourceNamespace",
            "targetNamespace", Collections.singletonList(
                cloneItem(1L, "targetDataId", "targetGroup")),
            "srcUser",
            SameConfigPolicy.OVERWRITE, "srcIp", "requestIpApp");
        
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertSame(saveResult, actual.getData());
        ConfigAllInfo clonedConfig = captureClonedConfig();
        assertEquals("targetNamespace", clonedConfig.getTenant());
        assertEquals("targetDataId", clonedConfig.getDataId());
        assertEquals("targetGroup", clonedConfig.getGroup());
        assertEquals("sourceContent", clonedConfig.getContent());
        assertEquals(ConfigType.PROPERTIES.getType(), clonedConfig.getType());
        assertEquals("sourceApp", clonedConfig.getAppName());
        assertEquals("sourceDesc", clonedConfig.getDesc());
        assertEquals("sourceKey", clonedConfig.getEncryptedDataKey());
    }
    
    @Test
    void testCloneConfigDefaultsSourceNamespaceToTarget() throws NacosException {
        when(namespacePersistService.tenantInfoCountByTenantId("targetNamespace")).thenReturn(1);
        when(configInfoPersistService.findAllConfigInfo4Export(isNull(), isNull(),
            eq("targetNamespace"), isNull(), eq(Collections.singletonList(1L))))
            .thenReturn(Collections.singletonList(sourceConfig(1L)));
        when(configInfoPersistService.batchInsertOrUpdate(anyList(), anyString(), anyString(),
            any(), any())).thenReturn(Collections.singletonMap("succCount", 1));
        
        Result<Map<String, Object>> actual = configCloneService.cloneConfig(null,
            "targetNamespace", Collections.singletonList(cloneItem(1L, null, null)), "srcUser",
            SameConfigPolicy.ABORT, "srcIp", "requestIpApp");
        
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        ConfigAllInfo clonedConfig = captureClonedConfig();
        assertEquals("targetNamespace", clonedConfig.getTenant());
    }
    
    @Test
    void testCloneConfigBlankTargetDefaultsToPublic() throws NacosException {
        when(configInfoPersistService.findAllConfigInfo4Export(isNull(), isNull(), eq("public"),
            isNull(), eq(Collections.singletonList(1L))))
            .thenReturn(Collections.singletonList(sourceConfig(1L)));
        when(configInfoPersistService.batchInsertOrUpdate(anyList(), anyString(), anyString(),
            any(), any())).thenReturn(Collections.singletonMap("succCount", 1));
        
        Result<Map<String, Object>> actual = configCloneService.cloneConfig("", "",
            Collections.singletonList(cloneItem(1L, "targetDataId", "targetGroup")), "srcUser",
            SameConfigPolicy.ABORT, "srcIp", "requestIpApp");
        
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        ConfigAllInfo clonedConfig = captureClonedConfig();
        assertEquals("public", clonedConfig.getTenant());
    }
    
    @Test
    void testCloneConfigUsesOriginalIdentityWhenTargetBlank() throws NacosException {
        when(configInfoPersistService.findAllConfigInfo4Export(isNull(), isNull(), eq("public"),
            isNull(), eq(Collections.singletonList(1L))))
            .thenReturn(Collections.singletonList(sourceConfig(1L)));
        when(configInfoPersistService.batchInsertOrUpdate(anyList(), anyString(), anyString(),
            any(), any())).thenReturn(Collections.singletonMap("succCount", 1));
        
        Result<Map<String, Object>> actual = configCloneService.cloneConfig("public", "public",
            Collections.singletonList(cloneItem(1L, "", "")), "srcUser", SameConfigPolicy.ABORT,
            "srcIp", "requestIpApp");
        
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        ConfigAllInfo clonedConfig = captureClonedConfig();
        assertEquals("sourceDataId", clonedConfig.getDataId());
        assertEquals("sourceGroup", clonedConfig.getGroup());
    }
    
    @Test
    void testCloneConfigWithDuplicateConfigIdUsesFirstItem() throws NacosException {
        when(configInfoPersistService.findAllConfigInfo4Export(isNull(), isNull(), eq("public"),
            isNull(), eq(Arrays.asList(1L, 1L))))
            .thenReturn(Collections.singletonList(sourceConfig(1L)));
        when(configInfoPersistService.batchInsertOrUpdate(anyList(), anyString(), anyString(),
            any(), any())).thenReturn(Collections.singletonMap("succCount", 1));
        
        Result<Map<String, Object>> actual = configCloneService.cloneConfig("public", "public",
            Arrays.asList(cloneItem(1L, "firstDataId", "firstGroup"),
                cloneItem(1L, "secondDataId", "secondGroup")),
            "srcUser",
            SameConfigPolicy.ABORT, "srcIp", "requestIpApp");
        
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        ConfigAllInfo clonedConfig = captureClonedConfig();
        assertEquals("firstDataId", clonedConfig.getDataId());
        assertEquals("firstGroup", clonedConfig.getGroup());
    }
    
    @Test
    void testCloneConfigIgnoresNullCloneItem() throws NacosException {
        when(configInfoPersistService.findAllConfigInfo4Export(isNull(), isNull(), eq("public"),
            isNull(), eq(Collections.singletonList(1L))))
            .thenReturn(Collections.singletonList(sourceConfig(1L)));
        when(configInfoPersistService.batchInsertOrUpdate(anyList(), anyString(), anyString(),
            any(), any())).thenReturn(Collections.singletonMap("succCount", 1));
        
        Result<Map<String, Object>> actual = configCloneService.cloneConfig("public", "public",
            Arrays.asList(null, cloneItem(1L, "targetDataId", "targetGroup")), "srcUser",
            SameConfigPolicy.ABORT, "srcIp", "requestIpApp");
        
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        ConfigAllInfo clonedConfig = captureClonedConfig();
        assertEquals("targetDataId", clonedConfig.getDataId());
        assertEquals("targetGroup", clonedConfig.getGroup());
    }
    
    @Test
    void testCloneConfigUsesEmptyEncryptedKeyWhenSourceEncryptedKeyNull()
        throws NacosException {
        ConfigAllInfo sourceConfig = sourceConfig(1L);
        sourceConfig.setEncryptedDataKey(null);
        when(configInfoPersistService.findAllConfigInfo4Export(isNull(), isNull(), eq("public"),
            isNull(), eq(Collections.singletonList(1L))))
            .thenReturn(Collections.singletonList(sourceConfig));
        when(configInfoPersistService.batchInsertOrUpdate(anyList(), anyString(), anyString(),
            any(), any())).thenReturn(Collections.singletonMap("succCount", 1));
        
        Result<Map<String, Object>> actual = configCloneService.cloneConfig("public", "public",
            Collections.singletonList(cloneItem(1L, "targetDataId", "targetGroup")), "srcUser",
            SameConfigPolicy.ABORT, "srcIp", "requestIpApp");
        
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        ConfigAllInfo clonedConfig = captureClonedConfig();
        assertEquals("", clonedConfig.getEncryptedDataKey());
    }
    
    private ConfigAllInfo captureClonedConfig() throws NacosException {
        ArgumentCaptor<List<ConfigAllInfo>> captor = ArgumentCaptor.forClass(List.class);
        verify(configInfoPersistService).batchInsertOrUpdate(captor.capture(), anyString(),
            anyString(), any(), any());
        return captor.getValue().get(0);
    }
    
    private ConfigAllInfo sourceConfig(long id) {
        ConfigAllInfo result = new ConfigAllInfo();
        result.setId(id);
        result.setDataId("sourceDataId");
        result.setGroup("sourceGroup");
        result.setContent("sourceContent");
        result.setType(ConfigType.PROPERTIES.getType());
        result.setAppName("sourceApp");
        result.setDesc("sourceDesc");
        result.setEncryptedDataKey("sourceKey");
        return result;
    }
    
    private ConfigCloneItem cloneItem(long configId, String targetDataId,
        String targetGroupName) {
        return new ConfigCloneItem(configId, targetDataId, targetGroupName);
    }
}
