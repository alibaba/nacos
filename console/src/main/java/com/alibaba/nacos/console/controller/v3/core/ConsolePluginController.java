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

package com.alibaba.nacos.console.controller.v3.core;

import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.console.paramcheck.ConsoleDefaultHttpParamExtractor;
import com.alibaba.nacos.console.proxy.core.PluginProxy;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.plugin.model.form.PluginConfigForm;
import com.alibaba.nacos.core.plugin.model.vo.PluginDetailVO;
import com.alibaba.nacos.core.plugin.model.vo.PluginInfoVO;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Controller for handling HTTP requests related to plugin operations.
 *
 * @author WangzJi
 */
@NacosApi
@RestController
@RequestMapping("/v3/console/plugin")
@ExtractorManager.Extractor(httpExtractor = ConsoleDefaultHttpParamExtractor.class)
public class ConsolePluginController {
    
    private final PluginProxy pluginProxy;
    
    public ConsolePluginController(PluginProxy pluginProxy) {
        this.pluginProxy = pluginProxy;
    }
    
    /**
     * Get plugin list.
     *
     * @param pluginType plugin type filter (optional)
     * @return plugin list
     */
    @Since("3.2.0")
    @GetMapping("/list")
    @Secured(resource = Constants.Resource.CONSOLE_RESOURCE_NAME_PREFIX
        + "plugins", action = ActionTypes.READ, signType = SignType.CONSOLE,
        apiType = ApiType.CONSOLE_API)
    public Result<List<PluginInfoVO>> getPluginList(
        @RequestParam(value = "pluginType", required = false) String pluginType)
        throws NacosException {
        return Result.success(pluginProxy.listPlugins(pluginType));
    }
    
    /**
     * Get plugin detail.
     *
     * @param pluginType plugin type
     * @param pluginName plugin name
     * @return plugin detail
     */
    @Since("3.2.0")
    @GetMapping
    @Secured(resource = Constants.Resource.CONSOLE_RESOURCE_NAME_PREFIX
        + "plugins", action = ActionTypes.READ, signType = SignType.CONSOLE,
        apiType = ApiType.CONSOLE_API)
    public Result<PluginDetailVO> getPluginDetail(@RequestParam("pluginType") String pluginType,
        @RequestParam("pluginName") String pluginName) throws NacosException {
        return Result.success(pluginProxy.getPluginDetail(pluginType, pluginName));
    }
    
    /**
     * Enable or disable plugin.
     *
     * @param pluginType plugin type
     * @param pluginName plugin name
     * @param enabled    enable or disable
     * @return success result
     */
    @Since("3.2.0")
    @PutMapping("/status")
    @Secured(resource = Constants.Resource.CONSOLE_RESOURCE_NAME_PREFIX
        + "plugins", action = ActionTypes.WRITE, signType = SignType.CONSOLE,
        apiType = ApiType.CONSOLE_API)
    public Result<String> updatePluginStatus(@RequestParam("pluginType") String pluginType,
        @RequestParam("pluginName") String pluginName, @RequestParam("enabled") boolean enabled,
        @RequestParam(value = "localOnly", defaultValue = "false") boolean localOnly)
        throws NacosException {
        pluginProxy.updatePluginStatus(pluginType, pluginName, enabled, localOnly);
        return Result.success("Plugin status updated successfully");
    }
    
    /**
     * Update plugin configuration.
     *
     * @param form plugin config form
     * @return success result
     */
    @Since("3.2.0")
    @PutMapping("/config")
    @Secured(resource = Constants.Resource.CONSOLE_RESOURCE_NAME_PREFIX
        + "plugins", action = ActionTypes.WRITE, signType = SignType.CONSOLE,
        apiType = ApiType.CONSOLE_API)
    public Result<String> updatePluginConfig(PluginConfigForm form) throws NacosException {
        if (StringUtils.isBlank(form.getPluginType())
            || StringUtils.isBlank(form.getPluginName())) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(),
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Plugin type and name are required");
        }
        if (form.getConfig() == null) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(),
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Plugin configuration is required");
        }
        pluginProxy.updatePluginConfig(form.getPluginType(), form.getPluginName(), form.getConfig(),
            form.isLocalOnly());
        return Result.success("Plugin configuration updated successfully");
    }
    
    /**
     * Get plugin availability across cluster nodes.
     *
     * @param pluginType plugin type
     * @param pluginName plugin name
     * @return node availability map
     */
    @Since("3.2.0")
    @GetMapping("/availability")
    @Secured(resource = Constants.Resource.CONSOLE_RESOURCE_NAME_PREFIX
        + "plugins", action = ActionTypes.READ, signType = SignType.CONSOLE,
        apiType = ApiType.CONSOLE_API)
    public Result<Map<String, Boolean>> getPluginAvailability(
        @RequestParam("pluginType") String pluginType,
        @RequestParam("pluginName") String pluginName) throws NacosException {
        return Result.success(pluginProxy.getPluginAvailability(pluginType, pluginName));
    }
}
