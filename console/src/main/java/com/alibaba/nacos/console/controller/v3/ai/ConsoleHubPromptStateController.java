/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.controller.v3.ai;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.param.HubPromptHttpParamExtractor;

import com.alibaba.nacos.ai.utils.HubResourceNameUtils;
import com.alibaba.nacos.api.ai.model.hub.HubResourceShareState;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.console.proxy.ai.HubPromptProxy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.alibaba.nacos.api.ai.constant.AiConstants.Hub.ENTRY_RESOURCE;
import static com.alibaba.nacos.api.ai.constant.AiConstants.Hub.ADMIN_RESOURCE;
import static com.alibaba.nacos.plugin.auth.constant.Constants.Tag.ONLY_IDENTITY;

/** Management-only Hub operations; original Client business URLs remain the read surface. */
@NacosApi
@RestController
@RequestMapping(Constants.HubPrompt.CONSOLE_PATH)
@ExtractorManager.Extractor(httpExtractor = HubPromptHttpParamExtractor.class)
public class ConsoleHubPromptStateController {
    
    private final HubPromptProxy service;
    
    public ConsoleHubPromptStateController(HubPromptProxy service) {
        this.service = service;
    }
    
    /** Inspect exact mutation state with source WRITE, not directory READ. */
    @Since("3.3.0")
    @GetMapping("/source-share")
    @Secured(action = ActionTypes.WRITE, signType = SignType.SPECIFIED,
        resource = ENTRY_RESOURCE, apiType = ApiType.CONSOLE_API, tags = {ONLY_IDENTITY})
    public Result<HubResourceShareState> sourceState(
        
        @RequestParam("sourceNamespaceId") String namespaceId,
        @RequestParam("sourceName") String name) throws NacosException {
        return Result.success(service.sourceState(namespaceId, name));
    }
    
    /** Inspect governance revision with independent administrator authority. */
    @Since("3.3.0")
    @GetMapping("/governance-state")
    @Secured(action = ActionTypes.WRITE, signType = SignType.SPECIFIED,
        resource = ADMIN_RESOURCE, apiType = ApiType.CONSOLE_API)
    public Result<HubResourceShareState> governanceState(
        
        @RequestParam("hubEntryId") String entryId) throws NacosException {
        return Result.success(
            service.governanceState(HubResourceNameUtils.parseEntryId(entryId)));
    }
    
}
