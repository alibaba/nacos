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
import com.alibaba.nacos.ai.param.HubSkillHttpParamExtractor;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.ai.service.hub.HubSkillTransferService;
import com.alibaba.nacos.ai.form.hub.HubSkillTransferForm;
import com.alibaba.nacos.ai.service.hub.HubSkillTransferRequestValidator;
import com.alibaba.nacos.api.ai.model.hub.HubSkillTransferRequest;
import com.alibaba.nacos.api.ai.model.hub.HubSkillTransferResult;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.alibaba.nacos.ai.constant.Constants.Hub.ENTRY_RESOURCE;
import static com.alibaba.nacos.plugin.auth.constant.Constants.Tag.ONLY_IDENTITY;

/** Dedicated S3 transfer APIs; the service reauthorizes source and target. */
@NacosApi
@RestController
@RequestMapping(Constants.Hub.ADMIN_PATH)
@ExtractorManager.Extractor(httpExtractor = HubSkillHttpParamExtractor.class)
public class HubSkillTransferAdminController {
    
    private final HubSkillTransferService service;
    
    public HubSkillTransferAdminController(HubSkillTransferService service) {
        this.service = service;
    }
    
    /** Share-grant import into a new draft. */
    @Since("3.3.0")
    @PostMapping("/import")
    @Secured(action = ActionTypes.WRITE, signType = SignType.SPECIFIED,
        resource = ENTRY_RESOURCE, apiType = ApiType.ADMIN_API, tags = {ONLY_IDENTITY})
    public Result<HubSkillTransferResult> importSkill(HubSkillTransferForm form)
        throws NacosException {
        HubSkillTransferRequest request = form.prepareRequest();
        request.setTargetVersion(HubSkillTransferRequestValidator.validate(request, true));
        return Result.success(service.importFromHub(request));
    }
    
    /** Direct source READ + target WRITE copy into a new draft. */
    @Since("3.3.0")
    @PostMapping("/copy")
    @Secured(action = ActionTypes.WRITE, signType = SignType.SPECIFIED,
        resource = ENTRY_RESOURCE, apiType = ApiType.ADMIN_API, tags = {ONLY_IDENTITY})
    public Result<HubSkillTransferResult> copySkill(HubSkillTransferForm form)
        throws NacosException {
        HubSkillTransferRequest request = form.prepareRequest();
        request.setTargetVersion(HubSkillTransferRequestValidator.validate(request, false));
        return Result.success(service.copyDirect(request));
    }
}
