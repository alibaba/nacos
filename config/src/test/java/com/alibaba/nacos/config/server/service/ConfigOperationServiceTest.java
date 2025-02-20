/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigOperateResult;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoGrayPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.StandardEnvironment;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ConfigServiceTest.
 *
 * @author dongyafei
 * @date 2022/8/11
 */

@ExtendWith(MockitoExtension.class)
class ConfigOperationServiceTest {
    
    private ConfigOperationService configOperationService;
    
    @Mock
    private ConfigInfoPersistService configInfoPersistService;
    
    @Mock
    private ConfigInfoGrayPersistService configInfoGrayPersistService;
    
    @Mock
    ConfigGrayModelMigrateService configGrayModelMigrateService;
    
    @BeforeEach
    void setUp() throws Exception {
        EnvUtil.setEnvironment(new StandardEnvironment());
        this.configOperationService = new ConfigOperationService(configInfoPersistService, configInfoGrayPersistService,
                configGrayModelMigrateService);
    }
    
    @Test
    void testPublishConfigBeta() throws NacosException {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId("test");
        configForm.setGroup("test");
        configForm.setContent("test content");
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        
        configRequestInfo.setCasMd5("");
        configForm.setTag("");
        
        // if betaIps is not blank and casMd5 is blank
        configRequestInfo.setBetaIps("test-betaIps");
        
        when(configInfoGrayPersistService.insertOrUpdateGray(any(ConfigInfo.class), eq("beta"), anyString(),
                eq(configRequestInfo.getSrcIp()), eq(configForm.getSrcUser()))).thenReturn(new ConfigOperateResult());
        Boolean eResult = configOperationService.publishConfig(configForm, configRequestInfo, "");
        assertTrue(eResult);
        
    }
    
    @Test
    void testPublishConfigBetaCas() throws NacosException {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId("test");
        configForm.setGroup("test");
        configForm.setContent("test content");
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        
        configRequestInfo.setCasMd5("casMd5");
        configForm.setTag("");
        
        // if betaIps is not blank and casMd5 is not blank
        configRequestInfo.setBetaIps("test-betaIps");
        configRequestInfo.setCasMd5("test casMd5");
        
        when(configInfoGrayPersistService.insertOrUpdateGrayCas(any(ConfigInfo.class), eq("beta"), anyString(),
                eq(configRequestInfo.getSrcIp()), eq(configForm.getSrcUser()))).thenReturn(new ConfigOperateResult());
        Boolean fResult = configOperationService.publishConfig(configForm, configRequestInfo, "");
        assertTrue(fResult);
    }
    
    @Test
    void testPublishConfigTag() throws NacosException {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId("test");
        configForm.setGroup("test");
        configForm.setContent("test content");
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        
        configRequestInfo.setCasMd5("");
        String tag = "testTag";
        configForm.setTag(tag);
        
        when(configInfoGrayPersistService.insertOrUpdateGray(any(ConfigInfo.class), eq("tag_" + tag), anyString(),
                eq(configRequestInfo.getSrcIp()), eq(configForm.getSrcUser()))).thenReturn(new ConfigOperateResult());
        Boolean cResult = configOperationService.publishConfig(configForm, configRequestInfo, "");
        assertTrue(cResult);
        
    }
    
    @Test
    void testPublishConfigTagCas() throws NacosException {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId("test");
        configForm.setGroup("test");
        configForm.setContent("test content");
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        
        configRequestInfo.setCasMd5("casMd5");
        String tag = "testTag";
        configForm.setTag(tag);
        
        when(configInfoGrayPersistService.insertOrUpdateGrayCas(any(ConfigInfo.class), eq("tag_" + tag), anyString(),
                eq(configRequestInfo.getSrcIp()), eq(configForm.getSrcUser()))).thenReturn(new ConfigOperateResult());
        Boolean dResult = configOperationService.publishConfig(configForm, configRequestInfo, "");
        assertTrue(dResult);
    }
    
    @Test
    void testPublishConfig() throws NacosException {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId("test");
        configForm.setGroup("test");
        configForm.setContent("test content");
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        
        // if betaIps is blank, tag is blank and casMd5 is blank
        when(configInfoPersistService.insertOrUpdate(any(), any(), any(ConfigInfo.class), any())).thenReturn(
                new ConfigOperateResult());
        Boolean aResult = configOperationService.publishConfig(configForm, configRequestInfo, "");
        verify(configInfoPersistService).insertOrUpdate(any(), any(), any(ConfigInfo.class), any());
        assertTrue(aResult);
        
        // if betaIps is blank, tag is blank and casMd5 is not blank
        configRequestInfo.setCasMd5("test casMd5");
        when(configInfoPersistService.insertOrUpdateCas(any(), any(), any(ConfigInfo.class), any())).thenReturn(
                new ConfigOperateResult());
        Boolean bResult = configOperationService.publishConfig(configForm, configRequestInfo, "");
        verify(configInfoPersistService).insertOrUpdateCas(any(), any(), any(ConfigInfo.class), any());
        assertTrue(bResult);
        configRequestInfo.setCasMd5("");
    }
    
    @Test
    void testDeleteConfig() {
        
        // if tag is blank
        Boolean aResult = configOperationService.deleteConfig("test", "test", "", "", "1.1.1.1", "test");
        verify(configInfoPersistService).removeConfigInfo(eq("test"), eq("test"), eq(""), any(), any());
        assertTrue(aResult);
        // if tag is not blank
        Boolean bResult = configOperationService.deleteConfig("test", "test", "", "test", "1.1.1.1", "test");
        assertTrue(bResult);
    }
}
