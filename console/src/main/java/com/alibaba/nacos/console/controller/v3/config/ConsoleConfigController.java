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

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.enums.ApiType;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.controller.parameters.SameNamespaceCloneConfigBean;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.GroupkeyListenserStatus;
import com.alibaba.nacos.config.server.model.SameConfigPolicy;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.paramcheck.ConfigBlurSearchHttpParamExtractor;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.console.paramcheck.ConsoleDefaultHttpParamExtractor;
import com.alibaba.nacos.console.proxy.config.ConfigProxy;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for handling HTTP requests related to configuration operations.
 *
 * @author zhangyukun
 */
@RestController
@RequestMapping("/v3/console/cs/config")
@ExtractorManager.Extractor(httpExtractor = ConsoleDefaultHttpParamExtractor.class)
public class ConsoleConfigController {
    
    private final ConfigProxy configProxy;
    
    public ConsoleConfigController(ConfigProxy configProxy) {
        this.configProxy = configProxy;
        
    }
    
    /**
     * Get the specific configuration information.
     *
     * @param dataId      Data ID string value.
     * @param group       Group string value.
     * @param namespaceId Namespace string value.
     * @return Result containing detailed configuration information.
     * @throws NacosException If a Nacos-specific error occurs.
     */
    @GetMapping
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG, apiType = ApiType.CONSOLE_API)
    public Result<ConfigAllInfo> getConfigDetail(@RequestParam("dataId") String dataId,
            @RequestParam("group") String group,
            @RequestParam(value = "namespaceId", required = false, defaultValue = StringUtils.EMPTY) String namespaceId)
            throws NacosException {
        // check namespaceId
        ParamUtils.checkTenantV2(namespaceId);
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        // check params
        ParamUtils.checkParam(dataId, group, "datumId", "content");
        
        return Result.success(configProxy.getConfigDetail(dataId, group, namespaceId));
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
    public Result<Boolean> publishConfig(HttpServletRequest request, ConfigForm configForm) throws NacosException {
        // check required field
        configForm.validate();
        //fix issue #9783
        configForm.setNamespaceId(NamespaceUtil.processNamespaceParameter(configForm.getNamespaceId()));
        // check param
        ParamUtils.checkTenantV2(configForm.getNamespaceId());
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
        
        return Result.success(configProxy.publishConfig(configForm, configRequestInfo));
    }
    
    
    /**
     * Delete configuration.
     *
     * @param request     HTTP servlet request.
     * @param dataId      Data ID string value.
     * @param group       Group string value.
     * @param namespaceId Namespace string value.
     * @param tag         Tag string value.
     * @return Result containing success status.
     * @throws NacosException If a Nacos-specific error occurs.
     */
    @DeleteMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG, apiType = ApiType.CONSOLE_API)
    public Result<Boolean> deleteConfig(HttpServletRequest request, @RequestParam("dataId") String dataId,
            @RequestParam("group") String group,
            @RequestParam(value = "namespaceId", required = false, defaultValue = StringUtils.EMPTY) String namespaceId,
            @RequestParam(value = "tag", required = false) String tag) throws NacosException {
        //fix issue #9783
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        // check namespaceId
        ParamUtils.checkTenantV2(namespaceId);
        ParamUtils.checkParam(dataId, group, "datumId", "rm");
        ParamUtils.checkParamV2(tag);
        
        String clientIp = RequestUtil.getRemoteIp(request);
        String srcUser = RequestUtil.getSrcUserName(request);
        
        return Result.success(configProxy.deleteConfig(dataId, group, namespaceId, tag, clientIp, srcUser));
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
     * @param dataId      Data ID string value.
     * @param group       Group string value.
     * @param namespaceId Namespace string value.
     * @param configTags  Configuration tags.
     * @param appName     Application name string value.
     * @param pageNo      Page number.
     * @param pageSize    Page size.
     * @return Result containing the configuration information.
     * @throws ServletException If a servlet-specific error occurs.
     * @throws IOException      If an I/O error occurs.
     * @throws NacosException   If a Nacos-specific error occurs.
     */
    @GetMapping("/list")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG, apiType = ApiType.CONSOLE_API)
    public Result<Page<ConfigInfo>> getConfigList(@RequestParam("dataId") String dataId,
            @RequestParam("group") String group,
            @RequestParam(value = "namespaceId", required = false, defaultValue = StringUtils.EMPTY) String namespaceId,
            @RequestParam(value = "config_tags", required = false) String configTags,
            @RequestParam(value = "appName", required = false) String appName, @RequestParam("pageNo") int pageNo,
            @RequestParam("pageSize") int pageSize) throws IOException, ServletException, NacosException {
        // check tenant
        ParamUtils.checkTenant(namespaceId);
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        Map<String, Object> configAdvanceInfo = new HashMap<>(100);
        if (StringUtils.isNotBlank(appName)) {
            configAdvanceInfo.put("appName", appName);
        }
        if (StringUtils.isNotBlank(configTags)) {
            configAdvanceInfo.put("config_tags", configTags);
        }
        
        return Result.success(
                configProxy.getConfigList(pageNo, pageSize, dataId, group, namespaceId, configAdvanceInfo));
    }
    
    /**
     * Search config list by config detail.
     *
     * @param dataId       Data ID string value.
     * @param group        Group string value.
     * @param appName      Application name string value.
     * @param namespaceId  Namespace string value.
     * @param configTags   Configuration tags.
     * @param configDetail Configuration detail string value.
     * @param search       Search type.
     * @param pageNo       Page number.
     * @param pageSize     Page size.
     * @return Result containing the configuration list by content.
     * @throws NacosException If a Nacos-specific error occurs.
     */
    @GetMapping("/searchDetail")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG, apiType = ApiType.CONSOLE_API)
    @ExtractorManager.Extractor(httpExtractor = ConfigBlurSearchHttpParamExtractor.class)
    public Result<Page<ConfigInfo>> getConfigListByContent(@RequestParam("dataId") String dataId,
            @RequestParam("group") String group, @RequestParam(value = "appName", required = false) String appName,
            @RequestParam(value = "namespaceId", required = false, defaultValue = StringUtils.EMPTY) String namespaceId,
            @RequestParam(value = "config_tags", required = false) String configTags,
            @RequestParam(value = "config_detail") String configDetail,
            @RequestParam(value = "search", defaultValue = "blur", required = false) String search,
            @RequestParam("pageNo") int pageNo, @RequestParam("pageSize") int pageSize) throws NacosException {
        Map<String, Object> configAdvanceInfo = new HashMap<>(100);
        if (StringUtils.isNotBlank(appName)) {
            configAdvanceInfo.put("appName", appName);
        }
        if (StringUtils.isNotBlank(configTags)) {
            configAdvanceInfo.put("config_tags", configTags);
        }
        if (StringUtils.isNotBlank(configDetail)) {
            configAdvanceInfo.put("content", configDetail);
        }
        
        return Result.success(configProxy.getConfigListByContent(search, pageNo, pageSize, dataId, group, namespaceId,
                configAdvanceInfo));
    }
    
    /**
     * Subscribe to configured client information.
     *
     * @param dataId      Data ID string value.
     * @param group       Group string value.
     * @param namespaceId Namespace string value.
     * @param sampleTime  Sample time value.
     * @return Result containing listener status.
     * @throws Exception If an error occurs during the operation.
     */
    @GetMapping("/listener")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG, apiType = ApiType.CONSOLE_API)
    public Result<GroupkeyListenserStatus> getListeners(@RequestParam("dataId") String dataId,
            @RequestParam("group") String group,
            @RequestParam(value = "namespaceId", required = false) String namespaceId,
            @RequestParam(value = "sampleTime", required = false, defaultValue = "1") int sampleTime) throws Exception {
        
        // check namespaceId
        ParamUtils.checkTenantV2(namespaceId);
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        
        // check params
        group = StringUtils.isBlank(group) ? Constants.DEFAULT_GROUP : group;
        ParamUtils.checkParam(dataId, group, "datumId", "content");
        
        return Result.success(configProxy.getListeners(dataId, group, namespaceId, sampleTime));
    }
    
    /**
     * Get subscribe information from client side.
     */
    @GetMapping("/listener/ip")
    @Secured(resource = Constants.LISTENER_CONTROLLER_PATH, action = ActionTypes.READ, signType = SignType.CONFIG, apiType = ApiType.CONSOLE_API)
    public Result<GroupkeyListenserStatus> getAllSubClientConfigByIp(@RequestParam("ip") String ip,
            @RequestParam(value = "all", required = false) boolean all,
            @RequestParam(value = "namespaceId", required = false) String namespaceId,
            @RequestParam(value = "sampleTime", required = false, defaultValue = "1") int sampleTime, ModelMap modelMap)
            throws NacosException {
        GroupkeyListenserStatus result = configProxy.getAllSubClientConfigByIp(ip, all, namespaceId, sampleTime);
        return Result.success(result);
    }
    
    /**
     * Export configuration.
     *
     * @param dataId      Data ID string value.
     * @param group       Group string value.
     * @param appName     Application name string value.
     * @param namespaceId Namespace string value.
     * @param ids         List of config IDs.
     * @return ResponseEntity containing the exported configuration.
     * @throws Exception If an error occurs during the export.
     */
    @GetMapping("/export")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG, apiType = ApiType.CONSOLE_API)
    public ResponseEntity<byte[]> exportConfig(@RequestParam(value = "dataId", required = false) String dataId,
            @RequestParam(value = "group", required = false) String group,
            @RequestParam(value = "appName", required = false) String appName,
            @RequestParam(value = "namespaceId", required = false, defaultValue = StringUtils.EMPTY) String namespaceId,
            @RequestParam(value = "ids", required = false) List<Long> ids) throws Exception {
        ids.removeAll(Collections.singleton(null));
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        
        return configProxy.exportConfig(dataId, group, namespaceId, appName, ids);
    }
    
    /**
     * New version export config adds metadata.yml file to record config metadata.
     *
     * @param dataId      Data ID string value.
     * @param group       Group string value.
     * @param appName     Application name string value.
     * @param namespaceId Namespace string value.
     * @param ids         List of config IDs.
     * @return ResponseEntity containing the exported configuration.
     * @throws Exception If an error occurs during the export.
     */
    @GetMapping("/export2")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG, apiType = ApiType.CONSOLE_API)
    public ResponseEntity<byte[]> exportConfigV2(@RequestParam(value = "dataId", required = false) String dataId,
            @RequestParam(value = "group", required = false) String group,
            @RequestParam(value = "appName", required = false) String appName,
            @RequestParam(value = "namespaceId", required = false, defaultValue = StringUtils.EMPTY) String namespaceId,
            @RequestParam(value = "ids", required = false) List<Long> ids) throws Exception {
        ids.removeAll(Collections.singleton(null));
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        
        return configProxy.exportConfigV2(dataId, group, namespaceId, appName, ids);
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
            @RequestParam(value = "src_user", required = false) String srcUser,
            @RequestParam(value = "namespaceId", required = false) String namespaceId,
            @RequestParam(value = "policy", defaultValue = "ABORT") SameConfigPolicy policy, MultipartFile file)
            throws NacosException {
        // check namespaceId
        ParamUtils.checkTenantV2(namespaceId);
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
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG, apiType = ApiType.CONSOLE_API)
    public Result<Map<String, Object>> cloneConfig(HttpServletRequest request,
            @RequestParam(value = "src_user", required = false) String srcUser,
            @RequestParam(value = "namespaceId") String namespaceId,
            @RequestBody List<SameNamespaceCloneConfigBean> configBeansList,
            @RequestParam(value = "policy", defaultValue = "ABORT") SameConfigPolicy policy) throws NacosException {
        
        configBeansList.removeAll(Collections.singleton(null));
        // check namespaceId
        ParamUtils.checkTenantV2(namespaceId);
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        
        if (StringUtils.isBlank(srcUser)) {
            srcUser = RequestUtil.getSrcUserName(request);
        }
        final String srcIp = RequestUtil.getRemoteIp(request);
        String requestIpApp = RequestUtil.getAppName(request);
        
        return configProxy.cloneConfig(srcUser, namespaceId, configBeansList, policy, srcIp, requestIpApp);
    }
    
}


