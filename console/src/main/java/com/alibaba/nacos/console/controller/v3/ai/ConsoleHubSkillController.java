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
import com.alibaba.nacos.ai.form.hub.HubShareSkillForm;
import com.alibaba.nacos.ai.form.hub.HubAdjustShareForm;
import com.alibaba.nacos.ai.form.hub.HubUnshareForm;
import com.alibaba.nacos.ai.form.hub.HubForceUnshareForm;
import com.alibaba.nacos.ai.param.HubSkillHttpParamExtractor;
import com.alibaba.nacos.console.proxy.ai.HubSkillProxy;
import com.alibaba.nacos.api.ai.model.hub.HubShareResult;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.alibaba.nacos.ai.constant.Constants.Hub.ENTRY_RESOURCE;
import static com.alibaba.nacos.api.ai.constant.AiConstants.Hub.ADMIN_RESOURCE;
import static com.alibaba.nacos.plugin.auth.constant.Constants.Tag.ONLY_IDENTITY;

/**
 * Hub Skill lifecycle console APIs.
 *
 * <p>The entry point authenticates the caller. The Hub service authorizes the
 * actual source Skill or the existing Console administrator resource.</p>
 *
 * @author nacos
 */
@NacosApi
@RestController
@RequestMapping(Constants.Hub.CONSOLE_PATH)
@ExtractorManager.Extractor(httpExtractor = HubSkillHttpParamExtractor.class)
public class ConsoleHubSkillController {
    
    private final HubSkillProxy hubSkillProxy;
    
    public ConsoleHubSkillController(HubSkillProxy hubSkillProxy) {
        this.hubSkillProxy = hubSkillProxy;
    }
    
    /**
     * Execute the Hub Skill share operation.
     *
     * @param form operation parameters
     * @return operation result
     * @throws NacosException if validation or authorization fails
     */
    @Since("3.3.0")
    @PostMapping("/share")
    @Secured(action = ActionTypes.WRITE, signType = SignType.SPECIFIED,
        resource = ENTRY_RESOURCE, apiType = ApiType.CONSOLE_API, tags = {ONLY_IDENTITY})
    public Result<HubShareResult> share(HubShareSkillForm form) throws NacosException {
        return Result.success(hubSkillProxy.share(form.prepareRequest()));
    }
    
    /**
     * Execute the Hub Skill adjust operation.
     *
     * @param form operation parameters
     * @return operation result
     * @throws NacosException if validation or authorization fails
     */
    @Since("3.3.0")
    @PostMapping("/adjust")
    @Secured(action = ActionTypes.WRITE, signType = SignType.SPECIFIED,
        resource = ENTRY_RESOURCE, apiType = ApiType.CONSOLE_API, tags = {ONLY_IDENTITY})
    public Result<HubShareResult> adjust(HubAdjustShareForm form) throws NacosException {
        return Result.success(hubSkillProxy.adjust(form.prepareRequest()));
    }
    
    /**
     * Execute the Hub Skill unshare operation.
     *
     * @param form operation parameters
     * @return operation result
     * @throws NacosException if validation or authorization fails
     */
    @Since("3.3.0")
    @PostMapping("/unshare")
    @Secured(action = ActionTypes.WRITE, signType = SignType.SPECIFIED,
        resource = ENTRY_RESOURCE, apiType = ApiType.CONSOLE_API, tags = {ONLY_IDENTITY})
    public Result<HubShareResult> unshare(HubUnshareForm form) throws NacosException {
        return Result.success(hubSkillProxy.unshare(form.prepareRequest()));
    }
    
    /**
     * Execute the Hub Skill forceUnshare operation.
     *
     * @param form operation parameters
     * @return operation result
     * @throws NacosException if validation or authorization fails
     */
    @Since("3.3.0")
    @PostMapping("/force-unshare")
    @Secured(action = ActionTypes.WRITE, signType = SignType.SPECIFIED,
        resource = ADMIN_RESOURCE, apiType = ApiType.CONSOLE_API)
    public Result<HubShareResult> forceUnshare(HubForceUnshareForm form) throws NacosException {
        return Result.success(hubSkillProxy.forceUnshare(form.prepareRequest()));
    }
}
