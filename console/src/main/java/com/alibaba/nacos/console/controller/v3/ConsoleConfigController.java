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
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.enums.ApiType;
import com.alibaba.nacos.common.model.RestResult;
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
import com.alibaba.nacos.console.proxy.ConfigProxy;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.beans.factory.annotation.Autowired;
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
import javax.servlet.http.HttpServletResponse;
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
    
    @Autowired
    public ConsoleConfigController(ConfigProxy configProxy) {
        this.configProxy = configProxy;
    }
    
    /**
     * Get configure board information fail.
     *
     * @throws ServletException  ServletException.
     * @throws IOException       IOException.
     * @throws NacosApiException NacosApiException.
     */
    @GetMapping
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG)
    public void getConfig(HttpServletRequest request, HttpServletResponse response,
            @RequestParam("dataId") String dataId, @RequestParam("group") String group,
            @RequestParam(value = "namespaceId", required = false, defaultValue = StringUtils.EMPTY) String namespaceId,
            @RequestParam(value = "tag", required = false) String tag)
            throws NacosException, IOException, ServletException {
        // check namespaceId
        ParamUtils.checkTenantV2(namespaceId);
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        // check params
        ParamUtils.checkParam(dataId, group, "datumId", "content");
        ParamUtils.checkParamV2(tag);
        final String clientIp = RequestUtil.getRemoteIp(request);
        String isNotify = request.getHeader("notify");
        
        configProxy.getConfig(request, response, dataId, group, namespaceId, tag, isNotify, clientIp, true);
        
    }
    
    /**
     * Get the specific configuration information that the console USES.
     *
     * @throws NacosException NacosException.
     */
    @GetMapping(params = "show=all")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public Result<ConfigAllInfo> detailConfigInfo(@RequestParam("dataId") String dataId,
            @RequestParam("group") String group,
            @RequestParam(value = "namespaceId", required = false, defaultValue = StringUtils.EMPTY) String namespaceId)
            throws NacosException {
        // check namespaceId
        ParamUtils.checkTenantV2(namespaceId);
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        // check params
        ParamUtils.checkParam(dataId, group, "datumId", "content");
        
        return Result.success(configProxy.detailConfigInfo(dataId, group, namespaceId));
    }
    
    /**
     * Adds or updates non-aggregated data.
     *
     * @throws NacosException NacosException.
     */
    @PostMapping()
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG)
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
     * Synchronously delete all pre-aggregation data under a dataId.
     *
     * @throws NacosApiException NacosApiException.
     */
    @DeleteMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG)
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
     * Execute delete config operation.
     *
     * @return java.lang.Boolean
     * @Description: delete configuration based on multiple config ids
     * @Param [request, response, dataId, group, tenant, tag]
     */
    @DeleteMapping(params = "delType=ids")
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG)
    public Result<Boolean> deleteConfigs(HttpServletRequest request, @RequestParam(value = "ids") List<Long> ids)
            throws NacosException {
        String clientIp = RequestUtil.getRemoteIp(request);
        String srcUser = RequestUtil.getSrcUserName(request);
        
        return Result.success(configProxy.deleteConfigs(ids, clientIp, srcUser));
    }
    
    /**
     * search config by config detail.
     */
    @GetMapping("/searchDetail")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG)
    @ExtractorManager.Extractor(httpExtractor = ConfigBlurSearchHttpParamExtractor.class)
    public Result<Page<ConfigInfo>> searchConfigByDetails(@RequestParam("dataId") String dataId,
            @RequestParam("group") String group, @RequestParam(value = "appName", required = false) String appName,
            @RequestParam(value = "namespaceId", required = false, defaultValue = StringUtils.EMPTY) String namespaceId,
            @RequestParam(value = "config_tags", required = false) String configTags,
            @RequestParam(value = "config_detail") String configDetail,
            @RequestParam(value = "search", defaultValue = "blur", required = false) String search,
            @RequestParam("pageNo") int pageNo, @RequestParam("pageSize") int pageSize) throws NacosException {
        // check namespaceId
        ParamUtils.checkTenantV2(namespaceId);
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        // check params
        ParamUtils.checkParam(dataId, group, "datumId", "content");
        
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
        
        return Result.success(configProxy.searchConfigByDetails(search, pageNo, pageSize, dataId, group, namespaceId,
                configAdvanceInfo));
    }
    
    /**
     * Subscribe to configured client information.
     */
    @GetMapping("/listener")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG)
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
     * Execute import and publish config operation.
     *
     * @param request     http servlet request .
     * @param srcUser     src user string value.
     * @param namespaceId namespace string value.
     * @param policy      policy model.
     * @param file        MultipartFile.
     * @return RestResult Map.
     * @throws NacosException NacosException.
     */
    @PostMapping(params = "import=true")
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG)
    public RestResult<Map<String, Object>> importAndPublishConfig(HttpServletRequest request,
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
     * Execute clone config operation.
     *
     * @param request         http servlet request .
     * @param srcUser         src user string value.
     * @param namespaceId     namespace string value.
     * @param configBeansList config beans list.
     * @param policy          config policy model.
     * @return RestResult for map.
     * @throws NacosException NacosException.
     */
    @PostMapping(params = "clone=true")
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG)
    public RestResult<Map<String, Object>> cloneConfig(HttpServletRequest request,
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


