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

import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.plugin.PluginManager;
import com.alibaba.nacos.core.plugin.model.PluginInfo;
import com.alibaba.nacos.core.plugin.model.vo.PluginDetailVO;
import com.alibaba.nacos.core.plugin.model.vo.PluginInfoVO;
import com.alibaba.nacos.core.plugin.model.form.PluginStatusForm;
import com.alibaba.nacos.core.plugin.model.form.PluginConfigForm;
import com.alibaba.nacos.core.utils.Commons;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.alibaba.nacos.core.utils.Commons.NACOS_ADMIN_CORE_CONTEXT_V3;

/**
 * Plugin Management V3 Controller.
 *
 * @author Nacos
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
    @GetMapping("/list")
    @Secured(resource = Commons.NACOS_ADMIN_CORE_CONTEXT_V3
            + "/plugin", action = ActionTypes.READ, signType = SignType.CONSOLE, apiType = ApiType.ADMIN_API)
    public Result<List<PluginInfoVO>> getPluginList(@RequestParam(value = "pluginType", required = false) String pluginType) {
        List<PluginInfo> plugins = unifiedPluginManager.listAllPlugins();

        if (StringUtils.isNotBlank(pluginType)) {
            plugins = plugins.stream()
                    .filter(p -> pluginType.equals(p.getPluginType().getType()))
                    .toList();
        }

        List<PluginInfoVO> vos = plugins.stream()
                .map(this::convertToVO)
                .toList();

        return Result.success(vos);
    }

    /**
     * Get plugin detail.
     *
     * @param pluginType plugin type
     * @param pluginName plugin name
     * @return plugin detail
     */
    @GetMapping("/detail")
    @Secured(resource = Commons.NACOS_ADMIN_CORE_CONTEXT_V3
            + "/plugin", action = ActionTypes.READ, signType = SignType.CONSOLE, apiType = ApiType.ADMIN_API)
    public Result<PluginDetailVO> getPluginDetail(
            @RequestParam("pluginType") String pluginType,
            @RequestParam("pluginName") String pluginName) throws NacosApiException {

        String pluginId = pluginType + ":" + pluginName;
        PluginInfo pluginInfo = unifiedPluginManager.getPlugin(pluginId)
                .orElseThrow(() -> new NacosApiException(HttpStatus.NOT_FOUND.value(), ErrorCode.RESOURCE_NOT_FOUND,
                        "Plugin not found: " + pluginId));

        PluginDetailVO detailVO = convertToDetailVO(pluginInfo);
        return Result.success(detailVO);
    }

    /**
     * Enable or disable plugin.
     *
     * @param form plugin status form
     * @return success result
     */
    @PutMapping("/status")
    @Secured(resource = Commons.NACOS_ADMIN_CORE_CONTEXT_V3
            + "/plugin", action = ActionTypes.WRITE, signType = SignType.CONSOLE, apiType = ApiType.ADMIN_API)
    public Result<String> updatePluginStatus(@RequestBody PluginStatusForm form) throws NacosApiException {
        validatePluginIdentifier(form.getPluginType(), form.getPluginName());
        String pluginId = form.getPluginType() + ":" + form.getPluginName();
        unifiedPluginManager.setPluginEnabled(pluginId, form.isEnabled(), form.isLocalOnly());
        return Result.success("Plugin status updated successfully");
    }

    /**
     * Update plugin configuration.
     *
     * @param form plugin config form
     * @return success result
     */
    @PutMapping("/config")
    @Secured(resource = Commons.NACOS_ADMIN_CORE_CONTEXT_V3
            + "/plugin", action = ActionTypes.WRITE, signType = SignType.CONSOLE, apiType = ApiType.ADMIN_API)
    public Result<String> updatePluginConfig(@RequestBody PluginConfigForm form) throws NacosApiException {
        validatePluginIdentifier(form.getPluginType(), form.getPluginName());
        if (form.getConfig() == null) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Plugin configuration is required");
        }
        String pluginId = form.getPluginType() + ":" + form.getPluginName();
        unifiedPluginManager.updatePluginConfig(pluginId, form.getConfig(), form.isLocalOnly());
        return Result.success("Plugin configuration updated successfully");
    }

    private void validatePluginIdentifier(String pluginType, String pluginName) throws NacosApiException {
        if (StringUtils.isBlank(pluginType) || StringUtils.isBlank(pluginName)) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_VALIDATE_ERROR,
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
        return vo;
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
