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

import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.form.skills.client.SkillQueryForm;
import com.alibaba.nacos.ai.param.SkillHttpParamExtractor;
import com.alibaba.nacos.ai.service.skills.SkillClientOperationService;
import com.alibaba.nacos.ai.service.skills.SkillQueryResult;
import com.alibaba.nacos.ai.utils.SkillRequestUtil;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.alibaba.nacos.plugin.auth.constant.Constants.Tag.ALLOW_ANONYMOUS;

/**
 * Skill client controller for runtime read query.
 *
 * @author nacos
 */
@NacosApi
@RestController
@RequestMapping(Constants.Skills.CLIENT_PATH)
@ExtractorManager.Extractor(httpExtractor = SkillHttpParamExtractor.class)
public class SkillClientController {
    
    private final SkillClientOperationService skillClientOperationService;
    
    public SkillClientController(SkillClientOperationService skillClientOperationService) {
        this.skillClientOperationService = skillClientOperationService;
    }
    
    /**
     * Download an online skill version as ZIP file by label/version/latest.
     *
     * <p>Supports listener-style polling: when the {@code md5} query parameter matches the
     * server-side published content MD5, the server returns HTTP 304 with the listener headers
     * ({@code ETag}/{@code X-Nacos-Skill-Md5}) so the client can keep using its local cache.
     */
    @Since("3.2.0")
    @GetMapping
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.OPEN_API,
        tags = {ALLOW_ANONYMOUS})
    public ResponseEntity<byte[]> get(SkillQueryForm form) throws NacosException {
        form.validate();
        SkillQueryResult result = skillClientOperationService.querySkill(form.getNamespaceId(),
            form.getName(), form.getVersion(), form.getLabel(), form.getMd5());
        if (result.isNotModified()) {
            // Client-supplied MD5 equals the published one; echo it back as the ETag without
            // re-loading the skill bytes.
            return SkillRequestUtil.buildSkillNotModifiedResponse(result.getMd5(),
                result.getResolvedVersion());
        }
        return SkillRequestUtil.buildSkillZipResponseWithMd5(result.getSkill(),
            result.getMd5(), result.getResolvedVersion());
    }
}
