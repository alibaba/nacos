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

package com.alibaba.nacos.ai.controller;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.form.hub.HubSkillReadEntryForm;
import com.alibaba.nacos.ai.param.HubSkillHttpParamExtractor;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.ai.service.hub.HubSkillShareStateService;
import com.alibaba.nacos.api.ai.model.hub.HubSkillShareState;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.alibaba.nacos.ai.constant.Constants.Hub.ENTRY_RESOURCE;
import static com.alibaba.nacos.ai.service.hub.HubSkillPermissionGuard.HUB_MANAGE_RESOURCE;
import static com.alibaba.nacos.plugin.auth.constant.Constants.Tag.ONLY_IDENTITY;

/** Authenticated mutation-state lookup; these are not public directory APIs. */
@NacosApi
@RestController
@RequestMapping(Constants.Hub.ADMIN_PATH)
@ExtractorManager.Extractor(httpExtractor = HubSkillHttpParamExtractor.class)
public class HubSkillStateAdminController {
    
    private final HubSkillShareStateService service;
    
    public HubSkillStateAdminController(HubSkillShareStateService service) {
        this.service = service;
    }
    
    /** Query source share state with source WRITE authorization. */
    @Since("3.3.0")
    @GetMapping("/source-share")
    @Secured(action = ActionTypes.WRITE, signType = SignType.SPECIFIED,
        resource = ENTRY_RESOURCE, apiType = ApiType.ADMIN_API, tags = {ONLY_IDENTITY})
    public Result<HubSkillShareState> bySource(
        @RequestParam("sourceNamespaceId") String namespaceId,
        @RequestParam("sourceName") String sourceName) throws NacosException {
        return Result.success(service.bySource(namespaceId, sourceName));
    }
    
    /** Query governance state with the dedicated Hub management permission. */
    @Since("3.3.0")
    @GetMapping("/governance-state")
    @Secured(action = ActionTypes.WRITE, signType = SignType.SPECIFIED,
        resource = HUB_MANAGE_RESOURCE, apiType = ApiType.ADMIN_API, tags = {ONLY_IDENTITY})
    public Result<HubSkillShareState> governanceState(HubSkillReadEntryForm form)
        throws NacosException {
        return Result.success(service.byEntryForAdmin(form.prepareEntryId()));
    }
}
