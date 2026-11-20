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
import com.alibaba.nacos.ai.param.HubAgentSpecHttpParamExtractor;

import com.alibaba.nacos.ai.form.hub.HubAgentSpecImportForm;
import com.alibaba.nacos.api.ai.model.hub.HubResourceTransferResult;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.ai.service.hub.HubAgentSpecService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.alibaba.nacos.api.ai.constant.AiConstants.Hub.ENTRY_RESOURCE;
import static com.alibaba.nacos.plugin.auth.constant.Constants.Tag.ONLY_IDENTITY;

/** Management-only Hub operations; original Client business URLs remain the read surface. */
@NacosApi
@RestController
@RequestMapping(Constants.HubAgentSpec.ADMIN_PATH)
@ExtractorManager.Extractor(httpExtractor = HubAgentSpecHttpParamExtractor.class)
public class HubAgentSpecImportAdminController {
    
    private final HubAgentSpecService service;
    
    public HubAgentSpecImportAdminController(HubAgentSpecService service) {
        this.service = service;
    }
    
    /** Import one fixed shared version into a new independent draft. */
    @Since("3.3.0")
    @PostMapping("/import")
    @Secured(action = ActionTypes.WRITE, signType = SignType.SPECIFIED,
        resource = ENTRY_RESOURCE, apiType = ApiType.ADMIN_API, tags = {ONLY_IDENTITY})
    public Result<HubResourceTransferResult> importFromHub(HubAgentSpecImportForm form)
        throws NacosException {
        return Result.success(service.importFromHub(form.prepareRequest()));
    }
}
