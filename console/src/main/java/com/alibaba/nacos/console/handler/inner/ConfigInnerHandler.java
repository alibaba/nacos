/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.console.handler.inner;

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.common.utils.Pair;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.controller.ConfigServletInner;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.console.handler.ConfigHandler;
import com.alibaba.nacos.plugin.encryption.handler.EncryptionHandler;
import org.springframework.stereotype.Service;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;

/**
 * Implementation of ConfigHandler for handling internal configuration operations.
 *
 * @author zhangyukun
 */
@Service
public class ConfigInnerHandler implements ConfigHandler {
    
    private final ConfigInfoPersistService configInfoPersistService;
    
    private final ConfigServletInner inner;
    
    private final ConfigOperationService configOperationService;
    
    public ConfigInnerHandler(ConfigServletInner inner, ConfigOperationService configOperationService,
            ConfigInfoPersistService configInfoPersistService) {
        this.inner = inner;
        this.configOperationService = configOperationService;
        this.configInfoPersistService = configInfoPersistService;
        
    }
    
    @Override
    public void getConfig(HttpServletRequest request, HttpServletResponse response, String dataId, String group,
            String tenant, String tag) throws IOException, ServletException, NacosException {
        ParamUtils.checkTenant(tenant);
        tenant = NamespaceUtil.processNamespaceParameter(tenant);
        
        ParamUtils.checkParam(dataId, group, "datumId", "content");
        ParamUtils.checkParam(tag);
        
        final String clientIp = RequestUtil.getRemoteIp(request);
        String isNotify = request.getHeader("notify");
        
        inner.doGetConfig(request, response, dataId, group, tenant, tag, isNotify, clientIp);
    }
    
    @Override
    public boolean publishConfig(HttpServletRequest request, HttpServletResponse response, String dataId, String group,
            String tenant, String content, String tag, String appName, String srcUser, String configTags, String desc,
            String use, String effect, String type, String schema, String encryptedDataKey) throws NacosException {
        String encryptedDataKeyFinal = null;
        if (StringUtils.isNotBlank(encryptedDataKey)) {
            encryptedDataKeyFinal = encryptedDataKey;
        } else {
            Pair<String, String> pair = EncryptionHandler.encryptHandler(dataId, content);
            content = pair.getSecond();
            encryptedDataKeyFinal = pair.getFirst();
        }
        
        ParamUtils.checkTenant(tenant);
        ParamUtils.checkParam(dataId, group, "datumId", content);
        ParamUtils.checkParam(tag);
        
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId(dataId);
        configForm.setGroup(group);
        configForm.setNamespaceId(tenant);
        configForm.setContent(content);
        configForm.setTag(tag);
        configForm.setAppName(appName);
        configForm.setSrcUser(srcUser);
        configForm.setConfigTags(configTags);
        configForm.setDesc(desc);
        configForm.setUse(use);
        configForm.setEffect(effect);
        configForm.setType(type);
        configForm.setSchema(schema);
        
        if (StringUtils.isBlank(srcUser)) {
            configForm.setSrcUser(RequestUtil.getSrcUserName(request));
        }
        if (!ConfigType.isValidType(type)) {
            configForm.setType(ConfigType.getDefaultType().getType());
        }
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        configRequestInfo.setSrcIp(RequestUtil.getRemoteIp(request));
        configRequestInfo.setRequestIpApp(RequestUtil.getAppName(request));
        configRequestInfo.setBetaIps(request.getHeader("betaIps"));
        configRequestInfo.setCasMd5(request.getHeader("casMd5"));
        
        return configOperationService.publishConfig(configForm, configRequestInfo, encryptedDataKeyFinal);
    }
    
    @Override
    public boolean deleteConfig(HttpServletRequest request, HttpServletResponse response, String dataId, String group,
            String tenant, String tag) throws NacosException {
        ParamUtils.checkTenant(tenant);
        ParamUtils.checkParam(dataId, group, "datumId", "rm");
        ParamUtils.checkParam(tag);
        
        String clientIp = RequestUtil.getRemoteIp(request);
        String srcUser = RequestUtil.getSrcUserName(request);
        
        return configOperationService.deleteConfig(dataId, group, tenant, tag, clientIp, srcUser);
    }
    
    @Override
    public ConfigAllInfo detailConfigInfo(String dataId, String group, String tenant) throws NacosException {
        ParamUtils.checkTenant(tenant);
        ParamUtils.checkParam(dataId, group, "datumId", "content");
        ConfigAllInfo configAllInfo = configInfoPersistService.findConfigAllInfo(dataId, group, tenant);
        
        if (Objects.nonNull(configAllInfo)) {
            String encryptedDataKey = configAllInfo.getEncryptedDataKey();
            Pair<String, String> pair = EncryptionHandler.decryptHandler(dataId, encryptedDataKey,
                    configAllInfo.getContent());
            configAllInfo.setContent(pair.getSecond());
        }
        return configAllInfo;
    }
}
