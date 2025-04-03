/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.controller.v3;

import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.config.model.ConfigListenerInfo;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.paramcheck.ConfigDefaultHttpParamExtractor;
import com.alibaba.nacos.config.server.service.listener.ConfigListenerStateDelegate;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.core.model.form.AggregationForm;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Listener management.
 *
 * @author Nacos
 */
@NacosApi
@RestController
@RequestMapping(Constants.LISTENER_CONTROLLER_V3_ADMIN_PATH)
@ExtractorManager.Extractor(httpExtractor = ConfigDefaultHttpParamExtractor.class)
@Tag(name = "nacos.admin.config.config.api.controller.name", description = "nacos.admin.config.config.api.controller.description", extensions = {
        @Extension(name = RemoteConstants.LABEL_MODULE,
                properties = @ExtensionProperty(name = RemoteConstants.LABEL_MODULE, value = RemoteConstants.LABEL_MODULE_CONFIG))})
public class ListenerControllerV3 {
    
    private final ConfigListenerStateDelegate configListenerStateDelegate;
    
    public ListenerControllerV3(ConfigListenerStateDelegate configListenerStateDelegate) {
        this.configListenerStateDelegate = configListenerStateDelegate;
    }
    
    /**
     * Get subscribe information from client side.
     */
    @GetMapping
    @Secured(resource = Constants.LISTENER_CONTROLLER_V3_ADMIN_PATH, signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    @Operation(summary = "nacos.admin.config.config.api.listener.ip.summary",
            description = "nacos.admin.config.config.api.listener.ip.description", security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.admin.config.config.api.listener.ip.example")))
    @Parameters(value = {@Parameter(name = "ip", required = true, example = "127.0.0.1"),
            @Parameter(name = "namespaceId", example = "public"), @Parameter(name = "aggregation", example = "true"),
            @Parameter(name = "aggregationForm", hidden = true)})
    public Result<ConfigListenerInfo> getAllSubClientConfigByIp(@RequestParam("ip") String ip,
            @RequestParam(value = "all", required = false) boolean all,
            @RequestParam(value = "namespaceId", required = false) String namespaceId,
            AggregationForm aggregationForm) {
        ConfigListenerInfo result = configListenerStateDelegate.getListenerStateByIp(ip,
                aggregationForm.isAggregation());
        result.setQueryType(ConfigListenerInfo.QUERY_TYPE_IP);
        Map<String, String> configMd5Status = new HashMap<>(100);
        if (result.getListenersStatus() == null || result.getListenersStatus().isEmpty()) {
            return Result.success(result);
        }
        Map<String, String> status = result.getListenersStatus();
        for (Map.Entry<String, String> config : status.entrySet()) {
            if (!StringUtils.isBlank(namespaceId) && config.getKey().contains(namespaceId)) {
                configMd5Status.put(config.getKey(), config.getValue());
                continue;
            }
            if (all) {
                configMd5Status.put(config.getKey(), config.getValue());
            } else {
                String[] configKeys = GroupKey2.parseKey(config.getKey());
                if (StringUtils.isBlank(configKeys[2])) {
                    configMd5Status.put(config.getKey(), config.getValue());
                }
            }
        }
        result.setListenersStatus(configMd5Status);
        return Result.success(result);
    }
    
}