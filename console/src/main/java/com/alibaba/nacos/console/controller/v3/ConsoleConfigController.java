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

package com.alibaba.nacos.console.controller.v3;

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.common.utils.Pair;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.console.paramcheck.ConsoleDefaultHttpParamExtractor;
import com.alibaba.nacos.console.proxy.ConfigProxy;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.encryption.handler.EncryptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Controller for handling HTTP requests related to configuration operations.
 *
 * @author zhangyukun
 */
@RestController
@RequestMapping("/v3/console/cs/config")
@ExtractorManager.Extractor(httpExtractor = ConsoleDefaultHttpParamExtractor.class)
public class ConsoleConfigController {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleConfigController.class);
    
    private final ConfigProxy configProxy;
    
    @Autowired
    public ConsoleConfigController(ConfigProxy configProxy) {
        this.configProxy = configProxy;
    }
    
    /**
     * Get configure board information fail.
     *
     * @throws ServletException ServletException.
     * @throws IOException      IOException.
     * @throws NacosException   NacosException.
     */
    @GetMapping
    public void getConfig(HttpServletRequest request, HttpServletResponse response,
            @RequestParam("dataId") String dataId, @RequestParam("group") String group,
            @RequestParam(value = "tenant", required = false, defaultValue = "") String tenant,
            @RequestParam(value = "tag", required = false) String tag)
            throws IOException, ServletException, NacosException {
        configProxy.getConfig(request, response, dataId, group, tenant, tag);
    }
    
    /**
     * Publish configuration information.
     *
     * @throws NacosException NacosException.
     */
    @PostMapping
    public Result<Boolean> publishConfig(HttpServletRequest request, HttpServletResponse response,
            @RequestParam("dataId") String dataId, @RequestParam("group") String group,
            @RequestParam(value = "namespaceId", required = false, defaultValue = "") String namespaceId,
            @RequestParam("content") String content, @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "appName", required = false) String appName,
            @RequestParam(value = "src_user", required = false) String srcUser,
            @RequestParam(value = "config_tags", required = false) String configTags,
            @RequestParam(value = "desc", required = false) String desc,
            @RequestParam(value = "use", required = false) String use,
            @RequestParam(value = "effect", required = false) String effect,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "schema", required = false) String schema,
            @RequestParam(required = false) String encryptedDataKey) throws NacosException {
        // Validate parameters and convert the parameter type
        String encryptedDataKeyFinal = null;
        if (StringUtils.isNotBlank(encryptedDataKey)) {
            encryptedDataKeyFinal = encryptedDataKey;
        } else {
            Pair<String, String> pair = EncryptionHandler.encryptHandler(dataId, content);
            content = pair.getSecond();
            encryptedDataKeyFinal = pair.getFirst();
        }
        
        ParamUtils.checkTenant(namespaceId);
        ParamUtils.checkParam(dataId, group, "datumId", content);
        ParamUtils.checkParam(tag);
        
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId(dataId);
        configForm.setGroup(group);
        configForm.setNamespaceId(namespaceId);
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
        
        boolean result = configProxy.publishConfig(configForm, configRequestInfo, encryptedDataKeyFinal);
        return Result.success(result);
    }
    
    /**
     * Synchronously delete all pre-aggregation data under a dataId.
     *
     * @throws NacosException NacosException.
     */
    @DeleteMapping
    public RestResult<Boolean> deleteConfig(HttpServletRequest request, HttpServletResponse response,
            @RequestParam("dataId") String dataId, @RequestParam("group") String group,
            @RequestParam(value = "tenant", required = false, defaultValue = "") String tenant,
            @RequestParam(value = "tag", required = false) String tag) {
        boolean result = false;
        try {
            result = configProxy.deleteConfig(request, response, dataId, group, tenant, tag);
            return RestResultUtils.success(result);
        } catch (Throwable e) {
            LOGGER.error("delete configuration information fail", e);
            return RestResultUtils.failed(500, result, "delete configuration information fail");
        }
    }
    
    /**
     * Get the specific configuration information that the console USES.
     *
     * @throws NacosException NacosException.
     */
    @GetMapping("/detail")
    public RestResult<ConfigAllInfo> detailConfigInfo(@RequestParam("dataId") String dataId,
            @RequestParam("group") String group,
            @RequestParam(value = "tenant", required = false, defaultValue = "") String tenant) {
        ConfigAllInfo configAllInfo = null;
        try {
            configAllInfo = configProxy.detailConfigInfo(dataId, group, tenant);
            return RestResultUtils.success(configAllInfo);
        } catch (Throwable e) {
            LOGGER.error("get detail config info error", e);
            return RestResultUtils.failed(500, configAllInfo, "get detail config info error");
        }
    }
}


