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

package com.alibaba.nacos.console.controller.v3.config;

import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.model.ConfigBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigDetailInfo;
import com.alibaba.nacos.api.config.model.ConfigGrayInfo;
import com.alibaba.nacos.api.config.model.ConfigListenerInfo;
import com.alibaba.nacos.api.config.model.SameConfigPolicy;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.constant.ParametersField;
import com.alibaba.nacos.config.server.controller.parameters.SameNamespaceCloneConfigBean;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigFormV3;
import com.alibaba.nacos.config.server.paramcheck.ConfigBlurSearchHttpParamExtractor;
import com.alibaba.nacos.config.server.paramcheck.ConfigDefaultHttpParamExtractor;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.console.proxy.config.ConfigProxy;
import com.alibaba.nacos.core.model.form.AggregationForm;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.nacos.config.server.utils.RequestUtil.getRemoteIp;

/**
 * Controller for handling HTTP requests related to configuration operations.
 *
 * @author zhangyukun
 */
@NacosApi
@RestController
@RequestMapping("/v3/console/cs/config")
@ExtractorManager.Extractor(httpExtractor = ConfigDefaultHttpParamExtractor.class)
public class ConsoleConfigController {
    
    private final ConfigProxy configProxy;
    
    public ConsoleConfigController(ConfigProxy configProxy) {
        this.configProxy = configProxy;
    }
    
    /**
     * Get the specific configuration information.
     *
     * @param configForm config form
     * @return Result containing detailed configuration information.
     * @throws NacosException If a Nacos-specific error occurs.
     */
    @GetMapping
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG, apiType = ApiType.CONSOLE_API)
    public Result<ConfigDetailInfo> getConfigDetail(ConfigFormV3 configForm) throws NacosException {
        configForm.validate();
        String namespaceId = NamespaceUtil.processNamespaceParameter(configForm.getNamespaceId());
        String dataId = configForm.getDataId();
        String groupName = configForm.getGroupName();
        return Result.success(configProxy.getConfigDetail(dataId, groupName, namespaceId));
    }
    
    /**
     * Add or update configuration.
     *
     * @param request    HTTP servlet request.
     * @param configForm Configuration form.
     * @return Result containing success status.
     * @throws NacosException If a Nacos-specific error occurs.
     */
    @PostMapping()
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG, apiType = ApiType.CONSOLE_API)
    public Result<Boolean> publishConfig(HttpServletRequest request, ConfigFormV3 configForm) throws NacosException {
        // check required field
        configForm.validateWithContent();
        final boolean namespaceTransferred = NamespaceUtil.isNeedTransferNamespace(configForm.getNamespaceId());
        configForm.setNamespaceId(NamespaceUtil.processNamespaceParameter(configForm.getNamespaceId()));
        
        // check param
        ParamUtils.checkParam(configForm.getDataId(), configForm.getGroup(), "datumId", configForm.getContent());
        ParamUtils.checkParamV2(configForm.getTag());
        
        if (StringUtils.isBlank(configForm.getSrcUser())) {
            configForm.setSrcUser(RequestUtil.getSrcUserName(request));
        }
        if (!ConfigType.isValidType(configForm.getType())) {
            configForm.setType(ConfigType.getDefaultType().getType());
        }
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        configRequestInfo.setSrcIp(RequestUtil.getRemoteIp(request));
        configRequestInfo.setRequestIpApp(RequestUtil.getAppName(request));
        configRequestInfo.setBetaIps(request.getHeader("betaIps"));
        configRequestInfo.setCasMd5(request.getHeader("casMd5"));
        configRequestInfo.setNamespaceTransferred(namespaceTransferred);
        
        return Result.success(configProxy.publishConfig(configForm, configRequestInfo));
    }
    
    /**
     * Delete configuration.
     *
     * @param request     HTTP servlet request.
     * @param configForm  Config form.
     * @return Result containing success status.
     * @throws NacosException If a Nacos-specific error occurs.
     */
    @DeleteMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG, apiType = ApiType.CONSOLE_API)
    public Result<Boolean> deleteConfig(HttpServletRequest request, ConfigFormV3 configForm) throws NacosException {
        configForm.validate();
        //fix issue #9783
        String namespaceId = NamespaceUtil.processNamespaceParameter(configForm.getNamespaceId());
        ParamUtils.checkParamV2(configForm.getTag());
        
        String dataId = configForm.getDataId();
        String groupName = configForm.getGroupName();
        String tag = configForm.getTag();
        String clientIp = RequestUtil.getRemoteIp(request);
        String srcUser = RequestUtil.getSrcUserName(request);
        
        return Result.success(configProxy.deleteConfig(dataId, groupName, namespaceId, tag, clientIp, srcUser));
    }
    
    /**
     * Batch delete configurations.
     *
     * @param request HTTP servlet request.
     * @param ids     List of config IDs.
     * @return Result containing success status.
     * @throws NacosException If a Nacos-specific error occurs.
     */
    @DeleteMapping("/batchDelete")
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG, apiType = ApiType.CONSOLE_API)
    public Result<Boolean> batchDeleteConfigs(HttpServletRequest request, @RequestParam(value = "ids") List<Long> ids)
            throws NacosException {
        String clientIp = RequestUtil.getRemoteIp(request);
        String srcUser = RequestUtil.getSrcUserName(request);
        
        return Result.success(configProxy.batchDeleteConfigs(ids, clientIp, srcUser));
    }
    
    /**
     * Get configure information list.
     *
     * @param configForm config form
     * @param pageForm   page form
     * @return Result containing the configuration information.
     * @throws ServletException If a servlet-specific error occurs.
     * @throws IOException      If an I/O error occurs.
     * @throws NacosException   If a Nacos-specific error occurs.
     */
    @GetMapping("/list")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG, apiType = ApiType.CONSOLE_API)
    @ExtractorManager.Extractor(httpExtractor = ConfigBlurSearchHttpParamExtractor.class)
    public Result<Page<ConfigBasicInfo>> getConfigList(ConfigFormV3 configForm, PageForm pageForm)
            throws IOException, ServletException, NacosException {
        configForm.blurSearchValidate();
        pageForm.validate();
        Map<String, Object> configAdvanceInfo = new HashMap<>(100);
        if (StringUtils.isNotBlank(configForm.getAppName())) {
            configAdvanceInfo.put("appName", configForm.getAppName());
        }
        if (StringUtils.isNotBlank(configForm.getConfigTags())) {
            configAdvanceInfo.put("config_tags", configForm.getConfigTags());
        }
        if (StringUtils.isNotBlank(configForm.getType())) {
            configAdvanceInfo.put(ParametersField.TYPES, configForm.getType());
        }
        int pageNo = pageForm.getPageNo();
        int pageSize = pageForm.getPageSize();
        String namespaceId = NamespaceUtil.processNamespaceParameter(configForm.getNamespaceId());
        String dataId = configForm.getDataId();
        String groupName = configForm.getGroupName();
        
        return Result.success(
                configProxy.getConfigList(pageNo, pageSize, dataId, groupName, namespaceId, configAdvanceInfo));
    }
    
    /**
     * Search config list by config detail.
     *
     * @param configForm   config form
     * @param pageForm     page form
     * @param configDetail Configuration detail string value.
     * @param search       Search type.
     * @return Result containing the configuration list by content.
     * @throws NacosException If a Nacos-specific error occurs.
     */
    @GetMapping("/searchDetail")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG, apiType = ApiType.CONSOLE_API)
    @ExtractorManager.Extractor(httpExtractor = ConfigBlurSearchHttpParamExtractor.class)
    public Result<Page<ConfigBasicInfo>> getConfigListByContent(ConfigFormV3 configForm, PageForm pageForm,
            String configDetail, @RequestParam(defaultValue = "blur") String search) throws NacosException {
        configForm.blurSearchValidate();
        pageForm.validate();
        Map<String, Object> configAdvanceInfo = new HashMap<>(100);
        if (StringUtils.isNotBlank(configForm.getAppName())) {
            configAdvanceInfo.put("appName", configForm.getAppName());
        }
        if (StringUtils.isNotBlank(configForm.getConfigTags())) {
            configAdvanceInfo.put("config_tags", configForm.getConfigTags());
        }
        if (StringUtils.isNotBlank(configForm.getType())) {
            configAdvanceInfo.put(ParametersField.TYPES, configForm.getType());
        }
        if (StringUtils.isNotBlank(configDetail)) {
            configAdvanceInfo.put("content", configDetail);
        }
        int pageNo = pageForm.getPageNo();
        int pageSize = pageForm.getPageSize();
        String namespaceId = NamespaceUtil.processNamespaceParameter(configForm.getNamespaceId());
        String dataId = configForm.getDataId();
        String groupName = configForm.getGroupName();
        
        return Result.success(
                configProxy.getConfigListByContent(search, pageNo, pageSize, dataId, groupName, namespaceId,
                        configAdvanceInfo));
    }
    
    /**
     * Subscribe to configured client information.
     *
     * @param configForm        config form
     * @param aggregationForm   aggregation form
     * @return Result containing listener status.
     * @throws Exception If an error occurs during the operation.
     */
    @GetMapping("/listener")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG, apiType = ApiType.CONSOLE_API)
    public Result<ConfigListenerInfo> getListeners(ConfigFormV3 configForm, AggregationForm aggregationForm)
            throws Exception {
        configForm.validate();
        aggregationForm.validate();
        String namespaceId = NamespaceUtil.processNamespaceParameter(configForm.getNamespaceId());
        String groupName = configForm.getGroupName();
        String dataId = configForm.getDataId();
        return Result.success(
                configProxy.getListeners(dataId, groupName, namespaceId, aggregationForm.isAggregation()));
    }
    
    /**
     * Get subscribe information from client side.
     */
    @GetMapping("/listener/ip")
    @Secured(resource = Constants.LISTENER_CONTROLLER_PATH, action = ActionTypes.READ, signType = SignType.CONFIG, apiType = ApiType.CONSOLE_API)
    public Result<ConfigListenerInfo> getAllSubClientConfigByIp(@RequestParam("ip") String ip,
            @RequestParam(value = "all", required = false) boolean all,
            @RequestParam(value = "namespaceId", required = false) String namespaceId, AggregationForm aggregationForm)
            throws NacosException {
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        return Result.success(
                configProxy.getAllSubClientConfigByIp(ip, all, namespaceId, aggregationForm.isAggregation()));
    }
    
    /**
     * New version export config adds metadata.yml file to record config metadata.
     *
     * @param configForm  config form
     * @param ids         List of config IDs.
     * @return ResponseEntity containing the exported configuration.
     * @throws Exception If an error occurs during the export.
     */
    @GetMapping("/export2")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG, apiType = ApiType.CONSOLE_API)
    public ResponseEntity<byte[]> exportConfigV2(ConfigFormV3 configForm,
            @RequestParam(value = "ids", required = false) List<Long> ids) throws Exception {
        configForm.blurSearchValidate();
        ids.removeAll(Collections.singleton(null));
        String namespaceId = NamespaceUtil.processNamespaceParameter(configForm.getNamespaceId());
        String dataId = configForm.getDataId();
        String groupName = configForm.getGroupName();
        String appName = configForm.getAppName();
        
        return configProxy.exportConfigV2(dataId, groupName, namespaceId, appName, ids);
    }
    
    /**
     * Import and publish configuration.
     *
     * @param request     HTTP servlet request.
     * @param srcUser     Source user string value.
     * @param namespaceId Namespace string value.
     * @param policy      Policy model.
     * @param file        Multipart file containing the configuration data.
     * @return Result containing a map of the import status.
     * @throws NacosException If a Nacos-specific error occurs.
     */
    @PostMapping("/import")
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG, apiType = ApiType.CONSOLE_API)
    public Result<Map<String, Object>> importAndPublishConfig(HttpServletRequest request,
            @RequestParam(required = false) String srcUser,
            @RequestParam(value = "namespaceId", required = false) String namespaceId,
            @RequestParam(value = "policy", defaultValue = "ABORT") SameConfigPolicy policy, MultipartFile file)
            throws NacosException {
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        
        if (StringUtils.isBlank(srcUser)) {
            srcUser = RequestUtil.getSrcUserName(request);
        }
        final String srcIp = RequestUtil.getRemoteIp(request);
        String requestIpApp = RequestUtil.getAppName(request);
        
        return configProxy.importAndPublishConfig(srcUser, namespaceId, policy, file, srcIp, requestIpApp);
    }
    
    /**
     * Clone configuration.
     *
     * @param request         HTTP servlet request.
     * @param srcUser         Source user string value.
     * @param namespaceId     Namespace string value.
     * @param configBeansList List of configuration beans.
     * @param policy          Policy model.
     * @return Result containing a map of the clone status.
     * @throws NacosException If a Nacos-specific error occurs.
     */
    @PostMapping("/clone")
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG, apiType = ApiType.CONSOLE_API, tags = {
            com.alibaba.nacos.plugin.auth.constant.Constants.Tag.SECURED_SPECIAL_TAGS})
    public Result<Map<String, Object>> cloneConfig(HttpServletRequest request,
            @RequestParam(required = false) String srcUser,
            @RequestParam(value = "targetNamespaceId") String namespaceId,
            @RequestBody List<SameNamespaceCloneConfigBean> configBeansList,
            @RequestParam(value = "policy", defaultValue = "ABORT") SameConfigPolicy policy) throws NacosException {
        configBeansList.removeAll(Collections.singleton(null));
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        if (StringUtils.isBlank(srcUser)) {
            srcUser = RequestUtil.getSrcUserName(request);
        }
        final String srcIp = RequestUtil.getRemoteIp(request);
        String requestIpApp = RequestUtil.getAppName(request);
        
        return configProxy.cloneConfig(srcUser, namespaceId, configBeansList, policy, srcIp, requestIpApp);
    }
    
    /**
     * Execute to remove beta operation.
     *
     * @param httpServletRequest HTTP request containing client details.
     * @param configForm         config form
     * @return Result indicating the outcome of the operation.
     * @throws NacosException If a Nacos-specific error occurs.
     */
    @DeleteMapping("/beta")
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG)
    public Result<Boolean> stopBeta(HttpServletRequest httpServletRequest, ConfigFormV3 configForm)
            throws NacosException {
        configForm.validate();
        String remoteIp = getRemoteIp(httpServletRequest);
        String requestIpApp = RequestUtil.getAppName(httpServletRequest);
        String dataId = configForm.getDataId();
        String groupName = configForm.getGroupName();
        String namespaceId = NamespaceUtil.processNamespaceParameter(configForm.getNamespaceId());
        String srcUser = RequestUtil.getSrcUserName(httpServletRequest);
        boolean success = configProxy.removeBetaConfig(dataId, groupName, namespaceId, remoteIp, requestIpApp, srcUser);
        if (!success) {
            return Result.failure(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), false);
        }
        return Result.success(true);
    }
    
    /**
     * Execute to query beta operation.
     *
     * @param configForm  config form
     * @return Result containing the ConfigInfo4Beta details.
     * @throws NacosException If a Nacos-specific error occurs.
     */
    @GetMapping("/beta")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG)
    public Result<ConfigGrayInfo> queryBeta(ConfigFormV3 configForm) throws NacosException {
        configForm.validate();
        String dataId = configForm.getDataId();
        String groupName = configForm.getGroupName();
        String namespaceId = NamespaceUtil.processNamespaceParameter(configForm.getNamespaceId());
        return Result.success(configProxy.queryBetaConfig(dataId, groupName, namespaceId));
    }
    
}


