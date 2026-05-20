/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.config.server.configuration.ConfigCompatibleConfig;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoBetaWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoGrayWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoStateWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoTagWrapper;
import com.alibaba.nacos.config.server.model.ConfigOperateResult;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoBetaPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoGrayPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoTagPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigMigratePersistService;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.core.namespace.repository.NamespacePersistService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigMigrateServiceTest {
    
    @Mock
    private ConfigInfoBetaPersistService configInfoBetaPersistService;
    
    @Mock
    private ConfigInfoTagPersistService configInfoTagPersistService;
    
    @Mock
    private ConfigInfoGrayPersistService configInfoGrayPersistService;
    
    @Mock
    private ConfigMigratePersistService configMigratePersistService;
    
    @Mock
    private NamespacePersistService namespacePersistService;
    
    @Mock
    private ConfigInfoPersistService configInfoPersistService;
    
    private MockedStatic<PropertyUtil> propertyUtilMockedStatic;
    
    private MockedStatic<ConfigCompatibleConfig> configCompatibleConfigMockedStatic;
    
    @Mock
    private ConfigCompatibleConfig configCompatibleConfig;
    
    private ConfigMigrateService service;
    
    @BeforeEach
    void setUp() {
        propertyUtilMockedStatic = Mockito.mockStatic(PropertyUtil.class);
        configCompatibleConfigMockedStatic =
            Mockito.mockStatic(ConfigCompatibleConfig.class);
        configCompatibleConfigMockedStatic
            .when(ConfigCompatibleConfig::getInstance)
            .thenReturn(configCompatibleConfig);
        service = new ConfigMigrateService(
            configInfoBetaPersistService, configInfoTagPersistService,
            configInfoGrayPersistService, configMigratePersistService,
            namespacePersistService, configInfoPersistService);
    }
    
    @AfterEach
    void tearDown() {
        propertyUtilMockedStatic.close();
        configCompatibleConfigMockedStatic.close();
    }
    
    @Test
    void testPersistTagv1NotCompatible() throws NacosApiException {
        propertyUtilMockedStatic.when(PropertyUtil::isGrayCompatibleModel)
            .thenReturn(false);
        service.persistTagv1(new ConfigForm(), new ConfigInfo(),
            new ConfigRequestInfo());
        verify(configInfoTagPersistService, never())
            .insertOrUpdateTag(any(), any(), any(), any());
    }
    
    @Test
    void testPersistTagv1Success() throws NacosApiException {
        propertyUtilMockedStatic.when(PropertyUtil::isGrayCompatibleModel)
            .thenReturn(true);
        service.oldTableVersion = true;
        ConfigForm form = new ConfigForm();
        form.setTag("t1");
        ConfigRequestInfo reqInfo = new ConfigRequestInfo();
        service.persistTagv1(form, new ConfigInfo(), reqInfo);
        verify(configInfoTagPersistService)
            .insertOrUpdateTag(any(), eq("t1"), any(), any());
    }
    
    @Test
    void testPersistTagv1CasSuccess() throws NacosApiException {
        propertyUtilMockedStatic.when(PropertyUtil::isGrayCompatibleModel)
            .thenReturn(true);
        service.oldTableVersion = true;
        ConfigForm form = new ConfigForm();
        form.setTag("t1");
        ConfigRequestInfo reqInfo = new ConfigRequestInfo();
        reqInfo.setCasMd5("md5");
        when(configInfoTagPersistService.insertOrUpdateTagCas(
            any(), eq("t1"), any(), any()))
            .thenReturn(new ConfigOperateResult(true));
        service.persistTagv1(form, new ConfigInfo(), reqInfo);
        verify(configInfoTagPersistService)
            .insertOrUpdateTagCas(any(), eq("t1"), any(), any());
    }
    
    @Test
    void testPersistTagv1CasFailure() {
        propertyUtilMockedStatic.when(PropertyUtil::isGrayCompatibleModel)
            .thenReturn(true);
        service.oldTableVersion = true;
        ConfigForm form = new ConfigForm();
        form.setTag("t1");
        ConfigRequestInfo reqInfo = new ConfigRequestInfo();
        reqInfo.setCasMd5("md5");
        when(configInfoTagPersistService.insertOrUpdateTagCas(
            any(), eq("t1"), any(), any()))
            .thenReturn(new ConfigOperateResult(false));
        assertThrows(NacosApiException.class,
            () -> service.persistTagv1(form, new ConfigInfo(), reqInfo));
    }
    
    @Test
    void testPersistBetaNotCompatible() throws NacosApiException {
        propertyUtilMockedStatic.when(PropertyUtil::isGrayCompatibleModel)
            .thenReturn(false);
        service.persistBeta(new ConfigForm(), new ConfigInfo(),
            new ConfigRequestInfo());
        verify(configInfoBetaPersistService, never())
            .insertOrUpdateBeta(any(), any(), any(), any());
    }
    
    @Test
    void testPersistBetaSuccess() throws NacosApiException {
        propertyUtilMockedStatic.when(PropertyUtil::isGrayCompatibleModel)
            .thenReturn(true);
        service.oldTableVersion = true;
        ConfigRequestInfo reqInfo = new ConfigRequestInfo();
        reqInfo.setBetaIps("1.2.3.4");
        service.persistBeta(new ConfigForm(), new ConfigInfo(), reqInfo);
        verify(configInfoBetaPersistService)
            .insertOrUpdateBeta(any(), eq("1.2.3.4"), any(), any());
    }
    
    @Test
    void testPersistBetaCasFailure() {
        propertyUtilMockedStatic.when(PropertyUtil::isGrayCompatibleModel)
            .thenReturn(true);
        service.oldTableVersion = true;
        ConfigRequestInfo reqInfo = new ConfigRequestInfo();
        reqInfo.setCasMd5("md5");
        reqInfo.setBetaIps("1.2.3.4");
        when(configInfoBetaPersistService.insertOrUpdateBetaCas(
            any(), eq("1.2.3.4"), any(), any()))
            .thenReturn(new ConfigOperateResult(false));
        assertThrows(NacosApiException.class,
            () -> service.persistBeta(new ConfigForm(), new ConfigInfo(),
                reqInfo));
    }
    
    @Test
    void testDeleteConfigGrayV1NotCompatible() {
        propertyUtilMockedStatic.when(PropertyUtil::isGrayCompatibleModel)
            .thenReturn(false);
        service.deleteConfigGrayV1("d", "g", "ns", "beta", "ip", "user");
        verify(configInfoBetaPersistService, never())
            .removeConfigInfo4Beta(any(), any(), any());
    }
    
    @Test
    void testDeleteConfigGrayV1Beta() {
        propertyUtilMockedStatic.when(PropertyUtil::isGrayCompatibleModel)
            .thenReturn(true);
        service.oldTableVersion = true;
        service.deleteConfigGrayV1("d", "g", "ns", "beta", "ip", "user");
        verify(configInfoBetaPersistService)
            .removeConfigInfo4Beta("d", "g", "ns");
    }
    
    @Test
    void testDeleteConfigGrayV1Tag() {
        propertyUtilMockedStatic.when(PropertyUtil::isGrayCompatibleModel)
            .thenReturn(true);
        service.oldTableVersion = true;
        service.deleteConfigGrayV1("d", "g", "ns", "tag_t1", "ip", "user");
        verify(configInfoTagPersistService)
            .removeConfigInfoTag("d", "g", "ns", "t1", "ip", "user");
    }
    
    @Test
    void testCheckMigrateBetaWhenBetaNull() {
        when(configInfoBetaPersistService.findConfigInfo4Beta("d", "g", "ns"))
            .thenReturn(null);
        when(configInfoGrayPersistService.findConfigInfo4Gray(
            "d", "g", "ns", "beta")).thenReturn(null);
        service.checkMigrateBeta("d", "g", "ns");
        verify(configInfoGrayPersistService, never())
            .insertOrUpdateGray(any(), any(), any(), any(), any());
    }
    
    @Test
    void testCheckMigrateBetaWhenBetaNullButGrayExists() {
        when(configInfoBetaPersistService.findConfigInfo4Beta("d", "g", "ns"))
            .thenReturn(null);
        ConfigInfoGrayWrapper gray = new ConfigInfoGrayWrapper();
        when(configInfoGrayPersistService.findConfigInfo4Gray(
            "d", "g", "ns", "beta")).thenReturn(gray);
        service.checkMigrateBeta("d", "g", "ns");
        verify(configInfoGrayPersistService)
            .removeConfigInfoGray(eq("d"), eq("g"), eq("ns"), eq("beta"),
                any(), any());
    }
    
    @Test
    void testCheckMigrateBetaMigrates() {
        ConfigInfoBetaWrapper beta = new ConfigInfoBetaWrapper();
        beta.setDataId("d");
        beta.setGroup("g");
        beta.setTenant("ns");
        beta.setBetaIps("1.2.3.4");
        beta.setLastModified(200L);
        when(configInfoBetaPersistService.findConfigInfo4Beta("d", "g", "ns"))
            .thenReturn(beta);
        when(configInfoGrayPersistService.findConfigInfo4Gray(
            "d", "g", "ns", "beta")).thenReturn(null);
        service.checkMigrateBeta("d", "g", "ns");
        verify(configInfoGrayPersistService)
            .insertOrUpdateGray(eq(beta), eq("beta"), anyString(), any(),
                any());
    }
    
    @Test
    void testCheckMigrateTagWhenTagNull() {
        when(configInfoTagPersistService.findConfigInfo4Tag(
            "d", "g", "ns", "t1")).thenReturn(null);
        when(configInfoGrayPersistService.findConfigInfo4Gray(
            "d", "g", "ns", "tag_t1")).thenReturn(null);
        service.checkMigrateTag("d", "g", "ns", "t1");
        verify(configInfoGrayPersistService, never())
            .insertOrUpdateGray(any(), any(), any(), any(), any());
    }
    
    @Test
    void testCheckMigrateTagMigrates() {
        ConfigInfoTagWrapper tag = new ConfigInfoTagWrapper();
        tag.setDataId("d");
        tag.setGroup("g");
        tag.setTenant("ns");
        tag.setTag("t1");
        tag.setLastModified(200L);
        when(configInfoTagPersistService.findConfigInfo4Tag(
            "d", "g", "ns", "t1")).thenReturn(tag);
        when(configInfoGrayPersistService.findConfigInfo4Gray(
            "d", "g", "ns", "tag_t1")).thenReturn(null);
        service.checkMigrateTag("d", "g", "ns", "t1");
        verify(configInfoGrayPersistService)
            .insertOrUpdateGray(eq(tag), eq("tag"), anyString(), any(),
                any());
    }
    
    @Test
    void testNamespaceMigrateBlankTenant() {
        service.namespaceMigrate("d", "g", "");
        verify(configMigratePersistService)
            .syncConfig(eq("d"), eq("g"), eq(""), eq("public"), anyString());
    }
    
    @Test
    void testNamespaceMigratePublicTenant() {
        service.namespaceMigrate("d", "g", "public");
        verify(configMigratePersistService)
            .syncConfig(eq("d"), eq("g"), eq("public"), eq(""), anyString());
    }
    
    @Test
    void testNamespaceMigrateOtherTenant() {
        service.namespaceMigrate("d", "g", "custom");
        verify(configMigratePersistService, never())
            .syncConfig(any(), any(), any(), any(), any());
    }
    
    @Test
    void testNamespaceMigrateGrayBlankTenant() {
        service.namespaceMigrateGray("d", "g", "", "gray1");
        verify(configMigratePersistService).syncConfigGray(
            eq("d"), eq("g"), eq(""), eq("gray1"), eq("public"),
            anyString());
    }
    
    @Test
    void testNamespaceMigrateGrayPublicTenant() {
        service.namespaceMigrateGray("d", "g", "public", "gray1");
        verify(configMigratePersistService).syncConfigGray(
            eq("d"), eq("g"), eq("public"), eq("gray1"), eq(""),
            anyString());
    }
    
    @Test
    void testRemoveConfigInfoMigrateNotPublic() {
        service.removeConfigInfoMigrate("d", "g", "custom", "ip", "user");
        verify(configInfoPersistService, never())
            .removeConfigInfo(any(), any(), eq(""), any(), any());
    }
    
    @Test
    void testRemoveConfigInfoMigratePublic() {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        service.removeConfigInfoMigrate("d", "g", "public", "ip", "user");
        verify(configInfoPersistService)
            .removeConfigInfo(eq("d"), eq("g"), eq(""), eq("ip"),
                anyString());
    }
    
    @Test
    void testRemoveConfigInfoGrayMigratePublic() {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        propertyUtilMockedStatic.when(PropertyUtil::isGrayCompatibleModel)
            .thenReturn(false);
        service.removeConfigInfoGrayMigrate("d", "g", "public", "beta",
            "ip", "user");
        verify(configInfoGrayPersistService).removeConfigInfoGray(
            eq("d"), eq("g"), eq(""), eq("beta"), eq("ip"), anyString());
    }
    
    @Test
    void testCheckDeletedConfigGrayMigrateStateNull() {
        service.checkDeletedConfigGrayMigrateState(null);
        verify(configInfoGrayPersistService, never())
            .removeConfigInfoGray(any(), any(), any(), any(), any(), any());
    }
    
    @Test
    void testCheckDeletedConfigGrayMigrateStateNotCompatible() {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(false);
        ConfigInfoStateWrapper wrapper = new ConfigInfoStateWrapper();
        wrapper.setTenant("");
        service.checkDeletedConfigGrayMigrateState(wrapper);
        verify(configInfoGrayPersistService, never())
            .findConfigInfo4GrayState(any(), any(), any(), any());
    }
    
    @Test
    void testCheckDeletedConfigGrayMigrateStateNonPublicTenant() {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        ConfigInfoStateWrapper wrapper = new ConfigInfoStateWrapper();
        wrapper.setTenant("custom-ns");
        service.checkDeletedConfigGrayMigrateState(wrapper);
        verify(configInfoGrayPersistService, never())
            .findConfigInfo4GrayState(any(), any(), any(), any());
    }
    
    @Test
    void testCheckDeletedConfigMigrateStateNotCompatible() {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(false);
        ConfigInfoStateWrapper wrapper = new ConfigInfoStateWrapper();
        wrapper.setTenant("");
        service.checkDeletedConfigMigrateState(wrapper);
        verify(configInfoPersistService, never())
            .findConfigInfoState(any(), any(), any());
    }
    
    @Test
    void testCheckChangedConfigMigrateStateNotCompatible() {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(false);
        ConfigInfoStateWrapper wrapper = new ConfigInfoStateWrapper();
        wrapper.setTenant("");
        service.checkChangedConfigMigrateState(wrapper);
        verify(configInfoPersistService, never())
            .findConfigAllInfo(any(), any(), any());
    }
    
    @Test
    void testCheckChangedConfigGrayMigrateStateNotCompatible() {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(false);
        ConfigInfoGrayWrapper wrapper = new ConfigInfoGrayWrapper();
        wrapper.setTenant("");
        service.checkChangedConfigGrayMigrateState(wrapper);
        verify(configInfoGrayPersistService, never())
            .findConfigInfo4Gray(any(), any(), any(), any());
    }
    
    @Test
    void testUpdateConfigMetadataMigrateNotPublic() throws NacosException {
        service.updateConfigMetadataMigrate("d", "g", "custom", "t", "desc");
        verify(configInfoPersistService, never())
            .updateConfigInfoMetadata(any(), any(), any(), any(), any());
    }
    
    @Test
    void testUpdateConfigMetadataMigrateSuccess() throws NacosException {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        when(configInfoPersistService.updateConfigInfoMetadata(
            "d", "g", "", "t", "desc"))
            .thenReturn(new ConfigOperateResult(true));
        service.updateConfigMetadataMigrate("d", "g", "public", "t", "desc");
        verify(configInfoPersistService)
            .updateConfigInfoMetadata("d", "g", "", "t", "desc");
    }
    
    @Test
    void testUpdateConfigMetadataMigrateFailure() throws NacosException {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        when(configInfoPersistService.updateConfigInfoMetadata(
            "d", "g", "", "t", "desc"))
            .thenReturn(new ConfigOperateResult(false));
        assertThrows(NacosApiException.class,
            () -> service.updateConfigMetadataMigrate("d", "g", "public",
                "t", "desc"));
    }
    
    @Test
    void testPublishConfigMigrateNotPublic() throws NacosException {
        ConfigForm form = new ConfigForm();
        form.setNamespaceId("custom");
        service.publishConfigMigrate(form, new ConfigRequestInfo(), "");
        verify(configInfoPersistService, never())
            .insertOrUpdate(any(), any(), any(), any());
    }
    
    @Test
    void testPublishConfigGrayMigrateNotPublic() throws NacosException {
        ConfigForm form = new ConfigForm();
        form.setNamespaceId("custom");
        service.publishConfigGrayMigrate("beta", form,
            new ConfigRequestInfo());
        verify(configInfoGrayPersistService, never())
            .insertOrUpdateGray(any(), any(), any(), any(), any());
    }
    
    @Test
    void testGetConfigAdvanceInfo() {
        ConfigForm form = new ConfigForm();
        form.setConfigTags("tags");
        form.setDesc("desc");
        form.setType("json");
        Map<String, Object> info = service.getConfigAdvanceInfo(form);
        assertEquals("tags", info.get("config_tags"));
        assertEquals("desc", info.get("desc"));
        assertEquals("json", info.get("type"));
    }
    
    @Test
    void testCheckDeletedConfigGrayMigrateStateBlankTenantRemoves() {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        ConfigInfoStateWrapper deleted = new ConfigInfoStateWrapper();
        deleted.setDataId("d");
        deleted.setGroup("g");
        deleted.setTenant("");
        deleted.setGrayName("beta");
        deleted.setLastModified(200L);
        
        ConfigInfoStateWrapper target = new ConfigInfoStateWrapper();
        target.setLastModified(100L);
        when(configInfoGrayPersistService.findConfigInfo4GrayState(
            "d", "g", "public", "beta")).thenReturn(target);
        
        service.checkDeletedConfigGrayMigrateState(deleted);
        verify(configInfoGrayPersistService)
            .removeConfigInfoGray(eq("d"), eq("g"), eq("public"), eq("beta"),
                isNull(), any());
    }
    
    @Test
    void testCheckDeletedConfigMigrateStateBlankTenantRemoves() {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        ConfigInfoStateWrapper deleted = new ConfigInfoStateWrapper();
        deleted.setDataId("d");
        deleted.setGroup("g");
        deleted.setTenant("");
        deleted.setLastModified(200L);
        
        ConfigInfoStateWrapper target = new ConfigInfoStateWrapper();
        target.setLastModified(100L);
        when(configInfoPersistService.findConfigInfoState(
            "d", "g", "public")).thenReturn(target);
        
        service.checkDeletedConfigMigrateState(deleted);
        verify(configInfoPersistService)
            .removeConfigInfo(eq("d"), eq("g"), eq("public"), isNull(),
                any());
    }
    
    @Test
    void testCheckDeletedConfigMigrateStateTargetNull() {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        ConfigInfoStateWrapper deleted = new ConfigInfoStateWrapper();
        deleted.setDataId("d");
        deleted.setGroup("g");
        deleted.setTenant("");
        deleted.setLastModified(200L);
        
        when(configInfoPersistService.findConfigInfoState(
            "d", "g", "public")).thenReturn(null);
        
        service.checkDeletedConfigMigrateState(deleted);
        verify(configInfoPersistService, never())
            .removeConfigInfo(any(), any(), eq("public"), any(), any());
    }
    
    @Test
    void testCheckChangedConfigMigrateStateAddsNew() {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        ConfigInfoStateWrapper changed = new ConfigInfoStateWrapper();
        changed.setDataId("d");
        changed.setGroup("g");
        changed.setTenant("");
        
        ConfigAllInfo changedAll = new ConfigAllInfo();
        changedAll.setDataId("d");
        changedAll.setGroup("g");
        changedAll.setMd5("md5-1");
        changedAll.setCreateUser("someuser");
        when(configInfoPersistService.findConfigAllInfo("d", "g", ""))
            .thenReturn(changedAll);
        when(configInfoPersistService.findConfigAllInfo("d", "g", "public"))
            .thenReturn(null);
        
        service.checkChangedConfigMigrateState(changed);
        verify(configInfoPersistService)
            .addConfigInfo(any(), anyString(), any(), any());
    }
    
    @Test
    void testCheckChangedConfigGrayMigrateStateAddsNew() {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        ConfigInfoGrayWrapper changed = new ConfigInfoGrayWrapper();
        changed.setDataId("d");
        changed.setGroup("g");
        changed.setTenant("");
        changed.setGrayName("beta");
        changed.setMd5("md5-1");
        changed.setGrayRule("rule1");
        changed.setSrcUser("someuser");
        changed.setLastModified(200L);
        
        when(configInfoGrayPersistService.findConfigInfo4Gray(
            "d", "g", "public", "beta")).thenReturn(null);
        
        service.checkChangedConfigGrayMigrateState(changed);
        verify(configInfoGrayPersistService)
            .addConfigInfo4Gray(any(), eq("beta"), eq("rule1"), any(),
                anyString());
    }
    
    @Test
    void testPublishConfigMigrateNotCompatibleMode() throws NacosException {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(false);
        ConfigForm form = new ConfigForm();
        form.setNamespaceId("public");
        service.publishConfigMigrate(form, new ConfigRequestInfo(), "");
        verify(configInfoPersistService, never())
            .insertOrUpdate(any(), any(), any(), any());
    }
    
    @Test
    void testPublishConfigMigrateInsertOrUpdate()
        throws NacosException {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        ConfigForm form = new ConfigForm();
        form.setNamespaceId("public");
        form.setDataId("d");
        form.setGroup("g");
        form.setContent("content");
        ConfigRequestInfo reqInfo = new ConfigRequestInfo();
        service.publishConfigMigrate(form, reqInfo, "");
        verify(configInfoPersistService)
            .insertOrUpdate(any(), anyString(), any(ConfigInfo.class),
                any());
    }
    
    @Test
    void testPublishConfigMigrateCasSuccess() throws NacosException {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        ConfigForm form = new ConfigForm();
        form.setNamespaceId("public");
        form.setDataId("d");
        form.setGroup("g");
        form.setContent("content");
        ConfigRequestInfo reqInfo = new ConfigRequestInfo();
        reqInfo.setCasMd5("casMd5");
        when(configInfoPersistService.insertOrUpdateCas(any(), anyString(),
            any(ConfigInfo.class), any()))
            .thenReturn(new ConfigOperateResult(true));
        service.publishConfigMigrate(form, reqInfo, "");
        verify(configInfoPersistService)
            .insertOrUpdateCas(any(), anyString(), any(ConfigInfo.class),
                any());
    }
    
    @Test
    void testPublishConfigMigrateCasFailure() {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        ConfigForm form = new ConfigForm();
        form.setNamespaceId("public");
        form.setDataId("d");
        form.setGroup("g");
        form.setContent("content");
        ConfigRequestInfo reqInfo = new ConfigRequestInfo();
        reqInfo.setCasMd5("casMd5");
        when(configInfoPersistService.insertOrUpdateCas(any(), anyString(),
            any(ConfigInfo.class), any()))
            .thenReturn(new ConfigOperateResult(false));
        assertThrows(NacosApiException.class,
            () -> service.publishConfigMigrate(form, reqInfo, ""));
    }
    
    @Test
    void testPublishConfigMigrateAddConfigInfo()
        throws NacosException {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        ConfigForm form = new ConfigForm();
        form.setNamespaceId("public");
        form.setDataId("d");
        form.setGroup("g");
        form.setContent("content");
        ConfigRequestInfo reqInfo = new ConfigRequestInfo();
        reqInfo.setUpdateForExist(false);
        when(configInfoPersistService.addConfigInfo(any(), anyString(),
            any(ConfigInfo.class), any()))
            .thenReturn(new ConfigOperateResult(true));
        service.publishConfigMigrate(form, reqInfo, "");
        verify(configInfoPersistService)
            .addConfigInfo(any(), anyString(), any(ConfigInfo.class),
                any());
    }
    
    @Test
    void testRemoveConfigInfoMigrateBlankTenant() {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        service.removeConfigInfoMigrate("d", "g", "public", "ip",
            "user");
        verify(configInfoPersistService)
            .removeConfigInfo(eq("d"), eq("g"), eq(""), eq("ip"),
                anyString());
    }
    
    @Test
    void testRemoveConfigInfoGrayMigrateNotPublic() {
        service.removeConfigInfoGrayMigrate("d", "g", "custom", "beta",
            "ip", "user");
        verify(configInfoGrayPersistService, never())
            .removeConfigInfoGray(any(), any(), any(), any(), any(),
                any());
    }
    
    @Test
    void testCheckChangedConfigMigrateStateUpdatesTarget()
        throws NacosException {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        ConfigInfoStateWrapper changed = new ConfigInfoStateWrapper();
        changed.setDataId("d");
        changed.setGroup("g");
        changed.setTenant("");
        
        ConfigAllInfo srcAll = new ConfigAllInfo();
        srcAll.setDataId("d");
        srcAll.setGroup("g");
        srcAll.setMd5("md5-1");
        srcAll.setModifyTime(100L);
        srcAll.setCreateUser("someuser");
        when(configInfoPersistService.findConfigAllInfo("d", "g", ""))
            .thenReturn(srcAll);
        
        ConfigAllInfo targetAll = new ConfigAllInfo();
        targetAll.setDataId("d");
        targetAll.setGroup("g");
        targetAll.setMd5("md5-other");
        targetAll.setModifyTime(50L);
        targetAll.setCreateUser("other");
        when(configInfoPersistService.findConfigAllInfo(
            "d", "g", "public")).thenReturn(targetAll);
        
        service.checkChangedConfigMigrateState(changed);
        verify(configInfoPersistService)
            .updateConfigInfo(any(), any(), anyString(), any());
    }
    
    @Test
    void testCheckChangedConfigGrayMigrateStateUpdates() {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        ConfigInfoGrayWrapper changed = new ConfigInfoGrayWrapper();
        changed.setDataId("d");
        changed.setGroup("g");
        changed.setTenant("");
        changed.setGrayName("beta");
        changed.setMd5("md5-1");
        changed.setGrayRule("rule1");
        changed.setSrcUser("someuser");
        changed.setLastModified(200L);
        
        ConfigInfoGrayWrapper existing = new ConfigInfoGrayWrapper();
        existing.setMd5("md5-other");
        existing.setGrayRule("rule-other");
        existing.setGrayName("beta");
        existing.setLastModified(100L);
        existing.setSrcUser("other");
        when(configInfoGrayPersistService.findConfigInfo4Gray(
            "d", "g", "public", "beta")).thenReturn(existing);
        
        service.checkChangedConfigGrayMigrateState(changed);
        verify(configInfoGrayPersistService)
            .updateConfigInfo4Gray(any(), eq("beta"), anyString(), any(),
                anyString());
    }
    
    @Test
    void testPublishConfigGrayMigrateNullGrayRule()
        throws NacosException {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        ConfigForm form = new ConfigForm();
        form.setNamespaceId("public");
        form.setDataId("d");
        form.setGroup("g");
        form.setContent("content");
        form.setGrayName("beta");
        form.setGrayVersion("invalid-version");
        form.setGrayRuleExp("1.2.3.4");
        form.setGrayPriority(1);
        assertThrows(NacosApiException.class,
            () -> service.publishConfigGrayMigrate("invalid_type",
                form, new ConfigRequestInfo()));
    }
    
    @Test
    void testPublishConfigGrayMigrateInsertOrUpdateGray()
        throws NacosException {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        propertyUtilMockedStatic.when(PropertyUtil::isGrayCompatibleModel)
            .thenReturn(false);
        ConfigForm form = new ConfigForm();
        form.setNamespaceId("public");
        form.setDataId("d");
        form.setGroup("g");
        form.setContent("content");
        form.setGrayName("beta");
        form.setGrayVersion("1.0.0");
        form.setGrayRuleExp("1.2.3.4");
        form.setGrayPriority(Integer.MAX_VALUE);
        service.publishConfigGrayMigrate("beta", form,
            new ConfigRequestInfo());
        verify(configInfoGrayPersistService)
            .insertOrUpdateGray(any(), eq("beta"), anyString(), any(),
                anyString());
    }
    
    @Test
    void testPublishConfigGrayMigrateCasSuccess()
        throws NacosException {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        propertyUtilMockedStatic.when(PropertyUtil::isGrayCompatibleModel)
            .thenReturn(false);
        ConfigForm form = new ConfigForm();
        form.setNamespaceId("public");
        form.setDataId("d");
        form.setGroup("g");
        form.setContent("content");
        form.setGrayName("beta");
        form.setGrayVersion("1.0.0");
        form.setGrayRuleExp("1.2.3.4");
        form.setGrayPriority(Integer.MAX_VALUE);
        ConfigRequestInfo reqInfo = new ConfigRequestInfo();
        reqInfo.setCasMd5("casMd5");
        when(configInfoGrayPersistService.insertOrUpdateGrayCas(
            any(), eq("beta"), anyString(), any(), anyString()))
            .thenReturn(new ConfigOperateResult(true));
        service.publishConfigGrayMigrate("beta", form, reqInfo);
        verify(configInfoGrayPersistService)
            .insertOrUpdateGrayCas(any(), eq("beta"), anyString(),
                any(), anyString());
    }
    
    @Test
    void testPublishConfigGrayMigrateCasFailure() {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        propertyUtilMockedStatic.when(PropertyUtil::isGrayCompatibleModel)
            .thenReturn(false);
        ConfigForm form = new ConfigForm();
        form.setNamespaceId("public");
        form.setDataId("d");
        form.setGroup("g");
        form.setContent("content");
        form.setGrayName("beta");
        form.setGrayVersion("1.0.0");
        form.setGrayRuleExp("1.2.3.4");
        form.setGrayPriority(Integer.MAX_VALUE);
        ConfigRequestInfo reqInfo = new ConfigRequestInfo();
        reqInfo.setCasMd5("casMd5");
        when(configInfoGrayPersistService.insertOrUpdateGrayCas(
            any(), eq("beta"), anyString(), any(), anyString()))
            .thenReturn(new ConfigOperateResult(false));
        assertThrows(NacosApiException.class,
            () -> service.publishConfigGrayMigrate("beta",
                form, reqInfo));
    }
    
    @Test
    void testPublishConfigGrayMigrateWithTagType()
        throws NacosException {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        propertyUtilMockedStatic.when(PropertyUtil::isGrayCompatibleModel)
            .thenReturn(true);
        service.oldTableVersion = true;
        ConfigForm form = new ConfigForm();
        form.setNamespaceId("public");
        form.setDataId("d");
        form.setGroup("g");
        form.setContent("content");
        form.setTag("t1");
        form.setGrayName("tag_t1");
        form.setGrayVersion("1.0.0");
        form.setGrayRuleExp("tagValue");
        form.setGrayPriority(Integer.MAX_VALUE - 1);
        service.publishConfigGrayMigrate("tag", form,
            new ConfigRequestInfo());
        verify(configInfoTagPersistService)
            .insertOrUpdateTag(any(), eq("t1"), any(), any());
        verify(configInfoGrayPersistService)
            .insertOrUpdateGray(any(), eq("tag_t1"), anyString(),
                any(), anyString());
    }
    
    @Test
    void testPublishConfigGrayMigrateWithBetaType()
        throws NacosException {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        propertyUtilMockedStatic.when(PropertyUtil::isGrayCompatibleModel)
            .thenReturn(true);
        service.oldTableVersion = true;
        ConfigForm form = new ConfigForm();
        form.setNamespaceId("public");
        form.setDataId("d");
        form.setGroup("g");
        form.setContent("content");
        form.setGrayName("beta");
        form.setGrayVersion("1.0.0");
        form.setGrayRuleExp("1.2.3.4");
        form.setGrayPriority(Integer.MAX_VALUE);
        ConfigRequestInfo reqInfo = new ConfigRequestInfo();
        reqInfo.setBetaIps("1.2.3.4");
        service.publishConfigGrayMigrate("beta", form, reqInfo);
        verify(configInfoBetaPersistService)
            .insertOrUpdateBeta(any(), eq("1.2.3.4"), any(), any());
    }
    
    @Test
    void testCheckChangedConfigGrayMigrateStateMigrateSrcTargetNull() {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        ConfigInfoGrayWrapper changed = new ConfigInfoGrayWrapper();
        changed.setDataId("d");
        changed.setGroup("g");
        changed.setTenant("");
        changed.setGrayName("beta");
        changed.setMd5("md5-1");
        changed.setGrayRule("rule1");
        changed.setSrcUser("nacos_namespace_migrate");
        changed.setLastModified(200L);
        
        when(configInfoGrayPersistService.findConfigInfo4Gray(
            "d", "g", "public", "beta")).thenReturn(null);
        
        service.checkChangedConfigGrayMigrateState(changed);
        verify(configInfoGrayPersistService)
            .removeConfigInfoGray(eq("d"), eq("g"), eq(""),
                eq("beta"), isNull(), anyString());
    }
    
    @Test
    void testCheckChangedConfigGrayMigrateSrcMigrateMd5DiffTargetNewer() {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        ConfigInfoGrayWrapper changed = new ConfigInfoGrayWrapper();
        changed.setDataId("d");
        changed.setGroup("g");
        changed.setTenant("");
        changed.setGrayName("beta");
        changed.setMd5("md5-1");
        changed.setGrayRule("rule1");
        changed.setSrcUser("nacos_namespace_migrate");
        changed.setLastModified(100L);
        
        ConfigInfoGrayWrapper target = new ConfigInfoGrayWrapper();
        target.setMd5("md5-other");
        target.setGrayRule("rule-other");
        target.setGrayName("beta");
        target.setLastModified(200L);
        target.setSrcUser("other");
        when(configInfoGrayPersistService.findConfigInfo4Gray(
            "d", "g", "public", "beta")).thenReturn(target);
        
        service.checkChangedConfigGrayMigrateState(changed);
        verify(configInfoGrayPersistService)
            .updateConfigInfo4Gray(eq(target), eq("beta"),
                anyString(), isNull(), anyString());
    }
    
    @Test
    void testCheckChangedConfigGrayNonMigrateMd5DiffSourceNewer() {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        ConfigInfoGrayWrapper changed = new ConfigInfoGrayWrapper();
        changed.setDataId("d");
        changed.setGroup("g");
        changed.setTenant("");
        changed.setGrayName("beta");
        changed.setMd5("md5-1");
        changed.setGrayRule("rule1");
        changed.setSrcUser("someuser");
        changed.setLastModified(200L);
        
        ConfigInfoGrayWrapper target = new ConfigInfoGrayWrapper();
        target.setMd5("md5-other");
        target.setGrayRule("rule-other");
        target.setGrayName("beta");
        target.setLastModified(100L);
        target.setSrcUser("nacos_namespace_migrate");
        when(configInfoGrayPersistService.findConfigInfo4Gray(
            "d", "g", "public", "beta")).thenReturn(target);
        
        service.checkChangedConfigGrayMigrateState(changed);
        verify(configInfoGrayPersistService)
            .updateConfigInfo4Gray(eq(changed), eq("beta"),
                eq("rule1"), isNull(), anyString());
    }
    
    @Test
    void testCheckChangedConfigMigrateSrcMigrateTargetNullRemoves() {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        ConfigInfoStateWrapper changed = new ConfigInfoStateWrapper();
        changed.setDataId("d");
        changed.setGroup("g");
        changed.setTenant("");
        
        ConfigAllInfo srcAll = new ConfigAllInfo();
        srcAll.setDataId("d");
        srcAll.setGroup("g");
        srcAll.setMd5("md5-1");
        srcAll.setCreateUser("nacos_namespace_migrate");
        when(configInfoPersistService.findConfigAllInfo("d", "g", ""))
            .thenReturn(srcAll);
        when(configInfoPersistService.findConfigAllInfo(
            "d", "g", "public")).thenReturn(null);
        
        service.checkChangedConfigMigrateState(changed);
        verify(configInfoPersistService)
            .removeConfigInfo(eq("d"), eq("g"), eq(""), isNull(),
                anyString());
    }
    
    @Test
    void testCheckChangedConfigMigrateSrcMigrateMd5DiffTargetNewer() {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        ConfigInfoStateWrapper changed = new ConfigInfoStateWrapper();
        changed.setDataId("d");
        changed.setGroup("g");
        changed.setTenant("");
        
        ConfigAllInfo srcAll = new ConfigAllInfo();
        srcAll.setDataId("d");
        srcAll.setGroup("g");
        srcAll.setMd5("md5-1");
        srcAll.setModifyTime(100L);
        srcAll.setCreateUser("nacos_namespace_migrate");
        when(configInfoPersistService.findConfigAllInfo("d", "g", ""))
            .thenReturn(srcAll);
        
        ConfigAllInfo targetAll = new ConfigAllInfo();
        targetAll.setMd5("md5-other");
        targetAll.setModifyTime(200L);
        targetAll.setCreateUser("other");
        when(configInfoPersistService.findConfigAllInfo(
            "d", "g", "public")).thenReturn(targetAll);
        
        service.checkChangedConfigMigrateState(changed);
        verify(configInfoPersistService)
            .updateConfigInfo(eq(targetAll), isNull(), anyString(),
                isNull());
    }
    
    @Test
    void testCheckChangedConfigMigrateNonMigrateSourceNewerUpdates() {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        ConfigInfoStateWrapper changed = new ConfigInfoStateWrapper();
        changed.setDataId("d");
        changed.setGroup("g");
        changed.setTenant("");
        
        ConfigAllInfo srcAll = new ConfigAllInfo();
        srcAll.setDataId("d");
        srcAll.setGroup("g");
        srcAll.setMd5("md5-1");
        srcAll.setModifyTime(200L);
        srcAll.setCreateUser("someuser");
        when(configInfoPersistService.findConfigAllInfo("d", "g", ""))
            .thenReturn(srcAll);
        
        ConfigAllInfo targetAll = new ConfigAllInfo();
        targetAll.setMd5("md5-other");
        targetAll.setModifyTime(100L);
        targetAll.setCreateUser("nacos_namespace_migrate");
        when(configInfoPersistService.findConfigAllInfo(
            "d", "g", "public")).thenReturn(targetAll);
        
        service.checkChangedConfigMigrateState(changed);
        verify(configInfoPersistService)
            .updateConfigInfo(eq(srcAll), isNull(), anyString(),
                isNull());
    }
    
    @Test
    void testCheckMigrateTagWhenTagNullButGrayExists() {
        when(configInfoTagPersistService.findConfigInfo4Tag(
            "d", "g", "ns", "t1")).thenReturn(null);
        ConfigInfoGrayWrapper gray = new ConfigInfoGrayWrapper();
        when(configInfoGrayPersistService.findConfigInfo4Gray(
            "d", "g", "ns", "tag_t1")).thenReturn(gray);
        service.checkMigrateTag("d", "g", "ns", "t1");
        verify(configInfoGrayPersistService)
            .removeConfigInfoGray(eq("d"), eq("g"), eq("ns"),
                eq("tag_t1"), any(), any());
    }
    
    @Test
    void testCheckMigrateBetaGrayNewerThanBeta() {
        ConfigInfoBetaWrapper beta = new ConfigInfoBetaWrapper();
        beta.setDataId("d");
        beta.setGroup("g");
        beta.setTenant("ns");
        beta.setBetaIps("1.2.3.4");
        beta.setLastModified(100L);
        when(configInfoBetaPersistService.findConfigInfo4Beta(
            "d", "g", "ns")).thenReturn(beta);
        ConfigInfoGrayWrapper gray = new ConfigInfoGrayWrapper();
        gray.setLastModified(200L);
        when(configInfoGrayPersistService.findConfigInfo4Gray(
            "d", "g", "ns", "beta")).thenReturn(gray);
        service.checkMigrateBeta("d", "g", "ns");
        verify(configInfoGrayPersistService, never())
            .insertOrUpdateGray(any(), any(), any(), any(), any());
    }
    
    @Test
    void testCheckDeletedConfigGrayMigrateTargetNewer() {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        ConfigInfoStateWrapper deleted = new ConfigInfoStateWrapper();
        deleted.setDataId("d");
        deleted.setGroup("g");
        deleted.setTenant("");
        deleted.setGrayName("beta");
        deleted.setLastModified(100L);
        
        ConfigInfoStateWrapper target = new ConfigInfoStateWrapper();
        target.setLastModified(200L);
        when(configInfoGrayPersistService.findConfigInfo4GrayState(
            "d", "g", "public", "beta")).thenReturn(target);
        
        service.checkDeletedConfigGrayMigrateState(deleted);
        verify(configInfoGrayPersistService, never())
            .removeConfigInfoGray(any(), any(), any(), any(), any(),
                any());
    }
    
    @Test
    void testCheckDeletedConfigMigrateTargetNewer() {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        ConfigInfoStateWrapper deleted = new ConfigInfoStateWrapper();
        deleted.setDataId("d");
        deleted.setGroup("g");
        deleted.setTenant("");
        deleted.setLastModified(100L);
        
        ConfigInfoStateWrapper target = new ConfigInfoStateWrapper();
        target.setLastModified(200L);
        when(configInfoPersistService.findConfigInfoState(
            "d", "g", "public")).thenReturn(target);
        
        service.checkDeletedConfigMigrateState(deleted);
        verify(configInfoPersistService, never())
            .removeConfigInfo(any(), any(), any(), any(), any());
    }
    
    @Test
    void testCheckDeletedConfigGrayPublicTenantMapsToEmpty() {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        ConfigInfoStateWrapper deleted = new ConfigInfoStateWrapper();
        deleted.setDataId("d");
        deleted.setGroup("g");
        deleted.setTenant("public");
        deleted.setGrayName("beta");
        deleted.setLastModified(200L);
        
        ConfigInfoStateWrapper target = new ConfigInfoStateWrapper();
        target.setLastModified(100L);
        when(configInfoGrayPersistService.findConfigInfo4GrayState(
            "d", "g", "", "beta")).thenReturn(target);
        
        service.checkDeletedConfigGrayMigrateState(deleted);
        verify(configInfoGrayPersistService)
            .removeConfigInfoGray(eq("d"), eq("g"), eq(""), eq("beta"),
                isNull(), anyString());
    }
    
    @Test
    void testCheckDeletedConfigMigratePublicTenantMapsToEmpty() {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        ConfigInfoStateWrapper deleted = new ConfigInfoStateWrapper();
        deleted.setDataId("d");
        deleted.setGroup("g");
        deleted.setTenant("public");
        deleted.setLastModified(200L);
        
        ConfigInfoStateWrapper target = new ConfigInfoStateWrapper();
        target.setLastModified(100L);
        when(configInfoPersistService.findConfigInfoState(
            "d", "g", "")).thenReturn(target);
        
        service.checkDeletedConfigMigrateState(deleted);
        verify(configInfoPersistService)
            .removeConfigInfo(eq("d"), eq("g"), eq(""), isNull(),
                anyString());
    }
    
    @Test
    void testCheckChangedConfigGrayNonMigrateTargetNewerUpdatesTarget() {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        ConfigInfoGrayWrapper changed = new ConfigInfoGrayWrapper();
        changed.setDataId("d");
        changed.setGroup("g");
        changed.setTenant("");
        changed.setGrayName("beta");
        changed.setMd5("md5-1");
        changed.setGrayRule("rule1");
        changed.setSrcUser("someuser");
        changed.setLastModified(100L);
        
        ConfigInfoGrayWrapper target = new ConfigInfoGrayWrapper();
        target.setMd5("md5-other");
        target.setGrayRule("rule-other");
        target.setGrayName("beta");
        target.setLastModified(200L);
        target.setSrcUser("other-user");
        when(configInfoGrayPersistService.findConfigInfo4Gray(
            "d", "g", "public", "beta")).thenReturn(target);
        
        service.checkChangedConfigGrayMigrateState(changed);
        verify(configInfoGrayPersistService)
            .updateConfigInfo4Gray(eq(target), eq("beta"),
                anyString(), isNull(), anyString());
    }
    
    @Test
    void testCheckChangedConfigMigrateNonMigrateTargetNewerUpdates() {
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(true);
        ConfigInfoStateWrapper changed = new ConfigInfoStateWrapper();
        changed.setDataId("d");
        changed.setGroup("g");
        changed.setTenant("");
        
        ConfigAllInfo srcAll = new ConfigAllInfo();
        srcAll.setDataId("d");
        srcAll.setGroup("g");
        srcAll.setMd5("md5-1");
        srcAll.setModifyTime(100L);
        srcAll.setCreateUser("someuser");
        when(configInfoPersistService.findConfigAllInfo("d", "g", ""))
            .thenReturn(srcAll);
        
        ConfigAllInfo targetAll = new ConfigAllInfo();
        targetAll.setMd5("md5-other");
        targetAll.setModifyTime(200L);
        targetAll.setCreateUser("other-user");
        when(configInfoPersistService.findConfigAllInfo(
            "d", "g", "public")).thenReturn(targetAll);
        
        service.checkChangedConfigMigrateState(changed);
        verify(configInfoPersistService)
            .updateConfigInfo(eq(targetAll), isNull(), anyString(),
                isNull());
    }
    
    @Test
    void testMigrateGrayCompatibleAndOldTableVersion() throws Exception {
        when(namespacePersistService.isExistTable("config_info_beta"))
            .thenReturn(false);
        when(configCompatibleConfig.isNamespaceCompatibleMode())
            .thenReturn(false);
        service.migrate();
        verify(configInfoBetaPersistService, never())
            .configInfoBetaCount();
    }
    
    @Test
    void testCheckMigrateTagGrayNewerThanTag() {
        ConfigInfoTagWrapper tag = new ConfigInfoTagWrapper();
        tag.setDataId("d");
        tag.setGroup("g");
        tag.setTenant("ns");
        tag.setTag("t1");
        tag.setLastModified(100L);
        when(configInfoTagPersistService.findConfigInfo4Tag(
            "d", "g", "ns", "t1")).thenReturn(tag);
        ConfigInfoGrayWrapper gray = new ConfigInfoGrayWrapper();
        gray.setLastModified(200L);
        when(configInfoGrayPersistService.findConfigInfo4Gray(
            "d", "g", "ns", "tag_t1")).thenReturn(gray);
        service.checkMigrateTag("d", "g", "ns", "t1");
        verify(configInfoGrayPersistService, never())
            .insertOrUpdateGray(any(), any(), any(), any(), any());
    }
}
