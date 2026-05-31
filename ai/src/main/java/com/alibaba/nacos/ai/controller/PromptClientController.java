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
import com.alibaba.nacos.ai.form.prompt.PromptQueryForm;
import com.alibaba.nacos.ai.param.PromptHttpParamExtractor;
import com.alibaba.nacos.ai.service.prompt.PromptClientOperationService;
import com.alibaba.nacos.ai.utils.PromptConvertUtils;
import com.alibaba.nacos.api.ai.model.prompt.Prompt;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prompt client controller for prompt runtime read query.
 *
 * @author nacos
 */
@NacosApi
@RestController
@RequestMapping(Constants.Prompt.CLIENT_PATH)
@ExtractorManager.Extractor(httpExtractor = PromptHttpParamExtractor.class)
public class PromptClientController {
    
    private final PromptClientOperationService promptOperationService;
    
    public PromptClientController(PromptClientOperationService promptOperationService) {
        this.promptOperationService = promptOperationService;
    }
    
    /**
     * Query prompt by version/label/latest with priority version > label > latest.
     */
    @Since("3.2.0")
    @GetMapping
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.OPEN_API)
    public Result<Prompt> queryPrompt(PromptQueryForm form, HttpServletResponse response)
        throws NacosException {
        form.validate();
        try {
            PromptVersionInfo result =
                promptOperationService.queryPrompt(form.getNamespaceId(), form.getPromptKey(),
                    form.getVersion(), form.getLabel(), form.getMd5());
            return Result.success(PromptConvertUtils.toClientPrompt(result));
        } catch (NacosException ex) {
            if (ex.getErrCode() == NacosException.NOT_MODIFIED) {
                response.setStatus(NacosException.NOT_MODIFIED);
                return Result.success(null);
            }
            throw ex;
        }
    }
}
