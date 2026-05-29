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

package com.alibaba.nacos.core.controller.v3;

import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.plugin.PluginManager;
import com.alibaba.nacos.core.plugin.model.PluginInfo;
import com.alibaba.nacos.core.plugin.model.vo.PluginDetailVO;
import com.alibaba.nacos.core.plugin.model.vo.PluginInfoVO;
import com.alibaba.nacos.core.utils.Commons;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.alibaba.nacos.core.utils.Commons.NACOS_ADMIN_CORE_CONTEXT_V3;

/**
 * Plugin Management V3 Controller.
 *
 * @author WangzJi
 */
@NacosApi
@RestController
@RequestMapping(NACOS_ADMIN_CORE_CONTEXT_V3 + "/plugin")
public class PluginControllerV3 {
    
    private final PluginManager unifiedPluginManager;
    
    public PluginControllerV3(PluginManager unifiedPluginManager) {
        this.unifiedPluginManager = unifiedPluginManager;
    }
    
    /**
     * Get plugin list.
     *
     * @param pluginType plugin type filter (optional)
     * @return plugin list
     */
    @Since("3.2.0")
    @GetMapping("/list")
    @Secured(resource = Commons.NACOS_ADMIN_CORE_CONTEXT_V3
        + "/plugin", action = ActionTypes.READ, signType = SignType.CONSOLE,
        apiType = ApiType.ADMIN_API)
    public Result<List<PluginInfoVO>> getPluginList(
        @RequestParam(value = "pluginType", required = false) String pluginType) {
        List<PluginInfo> plugins = unifiedPluginManager.listAllPlugins();
        
        if (StringUtils.isNotBlank(pluginType)) {
            plugins = plugins.stream().filter(p -> pluginType.equals(p.getPluginType().getType()))
                .toList();
        }
        
        List<PluginInfoVO> vos = plugins.stream().map(this::convertToVO).toList();
        
        return Result.success(vos);
    }
    
    /**
     * Get plugin detail.
     *
     * @param pluginType plugin type
     * @param pluginName plugin name
     * @return plugin detail
     */
    @Since("3.2.0")
    @GetMapping("/detail")
    @Secured(resource = Commons.NACOS_ADMIN_CORE_CONTEXT_V3
        + "/plugin", action = ActionTypes.READ, signType = SignType.CONSOLE,
        apiType = ApiType.ADMIN_API)
    public Result<PluginDetailVO> getPluginDetail(@RequestParam("pluginType") String pluginType,
        @RequestParam("pluginName") String pluginName) throws NacosApiException {
        
        String pluginId = pluginType + ":" + pluginName;
        PluginInfo pluginInfo = unifiedPluginManager.getPlugin(pluginId).orElseThrow(
            () -> new NacosApiException(HttpStatus.NOT_FOUND.value(), ErrorCode.RESOURCE_NOT_FOUND,
                "Plugin not found: " + pluginId));
        
        PluginDetailVO detailVO = convertToDetailVO(pluginInfo);
        return Result.success(detailVO);
    }
    
    /**
     * Enable or disable plugin.
     *
     * @param pluginType plugin type
     * @param pluginName plugin name
     * @param enabled    enable or disable
     * @param localOnly  whether only apply to local node
     * @return success result
     */
    @Since("3.2.0")
    @PutMapping("/status")
    @Secured(resource = Commons.NACOS_ADMIN_CORE_CONTEXT_V3
        + "/plugin", action = ActionTypes.WRITE, signType = SignType.CONSOLE,
        apiType = ApiType.ADMIN_API)
    public Result<String> updatePluginStatus(@RequestParam("pluginType") String pluginType,
        @RequestParam("pluginName") String pluginName, @RequestParam("enabled") boolean enabled,
        @RequestParam(value = "localOnly", defaultValue = "false") boolean localOnly)
        throws NacosApiException {
        validatePluginIdentifier(pluginType, pluginName);
        String pluginId = pluginType + ":" + pluginName;
        unifiedPluginManager.setPluginEnabled(pluginId, enabled, localOnly);
        return Result.success("Plugin status updated successfully");
    }
    
    /**
     * Update plugin configuration.
     *
     * @param pluginType plugin type
     * @param pluginName plugin name
     * @param configJson plugin configuration as JSON string
     * @param localOnly  whether only apply to local node
     * @return success result
     */
    @Since("3.2.0")
    @PutMapping("/config")
    @Secured(resource = Commons.NACOS_ADMIN_CORE_CONTEXT_V3
        + "/plugin", action = ActionTypes.WRITE, signType = SignType.CONSOLE,
        apiType = ApiType.ADMIN_API)
    public Result<String> updatePluginConfig(@RequestParam("pluginType") String pluginType,
        @RequestParam("pluginName") String pluginName, @RequestParam("config") String configJson,
        @RequestParam(value = "localOnly", defaultValue = "false") boolean localOnly)
        throws NacosApiException {
        validatePluginIdentifier(pluginType, pluginName);
        Map<String, String> config =
            JacksonUtils.toObj(configJson, new TypeReference<Map<String, String>>() {
            });
        if (config == null) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(),
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Plugin configuration is required");
        }
        String pluginId = pluginType + ":" + pluginName;
        unifiedPluginManager.updatePluginConfig(pluginId, config, localOnly);
        return Result.success("Plugin configuration updated successfully");
    }
    
    private void validatePluginIdentifier(String pluginType, String pluginName)
        throws NacosApiException {
        if (StringUtils.isBlank(pluginType) || StringUtils.isBlank(pluginName)) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(),
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Plugin type and name are required");
        }
    }
    
    private PluginInfoVO convertToVO(PluginInfo pluginInfo) {
        PluginInfoVO vo = new PluginInfoVO();
        vo.setPluginId(pluginInfo.getPluginId());
        vo.setPluginType(pluginInfo.getPluginType().getType());
        vo.setPluginName(pluginInfo.getPluginName());
        vo.setEnabled(pluginInfo.isEnabled());
        vo.setCritical(pluginInfo.isCritical());
        vo.setConfigurable(pluginInfo.isConfigurable());
        vo.setExclusive(isExclusiveType(pluginInfo.getPluginType()));
        return vo;
    }
    
    /**
     * Check if the plugin type is exclusive (only one can be active at a time). Exclusive types: AUTH,
     * DATASOURCE_DIALECT.
     *
     * @param type plugin type
     * @return true if exclusive
     */
    private boolean isExclusiveType(PluginType type) {
        return type == PluginType.AUTH || type == PluginType.DATASOURCE_DIALECT;
    }
    
    private PluginDetailVO convertToDetailVO(PluginInfo pluginInfo) {
        PluginDetailVO vo = new PluginDetailVO();
        vo.setPluginId(pluginInfo.getPluginId());
        vo.setPluginType(pluginInfo.getPluginType().getType());
        vo.setPluginName(pluginInfo.getPluginName());
        vo.setEnabled(pluginInfo.isEnabled());
        vo.setCritical(pluginInfo.isCritical());
        vo.setConfigurable(pluginInfo.isConfigurable());
        vo.setConfig(pluginInfo.getConfig());
        vo.setConfigDefinitions(pluginInfo.getConfigDefinitions());
        return vo;
    }
}
