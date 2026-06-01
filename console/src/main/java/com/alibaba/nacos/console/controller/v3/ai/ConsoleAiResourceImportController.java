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

import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.form.importer.AiResourceImportExecuteForm;
import com.alibaba.nacos.ai.form.importer.AiResourceImportSearchForm;
import com.alibaba.nacos.ai.form.importer.AiResourceImportSourceListForm;
import com.alibaba.nacos.ai.form.importer.AiResourceImportValidateForm;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportExecuteResponse;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportSearchResponse;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportSourceInfo;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidateResponse;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.console.proxy.ai.AiResourceImportProxy;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Console API controller for AI resource import.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
@NacosApi
@RestController
@RequestMapping(Constants.AI_RESOURCE_IMPORT_CONSOLE_PATH)
public class ConsoleAiResourceImportController {
    
    private final AiResourceImportProxy importProxy;
    
    public ConsoleAiResourceImportController(AiResourceImportProxy importProxy) {
        this.importProxy = importProxy;
    }
    
    /**
     * List configured import sources for Console.
     *
     * @param form source list form
     * @return source list
     * @throws NacosException if source configuration is invalid
     */
    @Since("3.2.2")
    @GetMapping("/sources")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<List<AiResourceImportSourceInfo>> listSources(
        AiResourceImportSourceListForm form) throws NacosException {
        form.validate();
        return Result.success(importProxy.listSources(form.getResourceType()));
    }
    
    /**
     * Search external import candidates for Console.
     *
     * @param form search form
     * @return candidate page
     * @throws NacosException if the source cannot be searched
     */
    @Since("3.2.2")
    @PostMapping("/search")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<AiResourceImportSearchResponse> search(AiResourceImportSearchForm form)
        throws NacosException {
        form.validate();
        return Result.success(importProxy.search(form.toRequest()));
    }
    
    /**
     * Validate selected import candidates for Console.
     *
     * @param form validate form
     * @return validation result
     * @throws NacosException if validation cannot start
     */
    @Since("3.2.2")
    @PostMapping("/validate")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<AiResourceImportValidateResponse> validate(AiResourceImportValidateForm form)
        throws NacosException {
        form.validate();
        return Result.success(importProxy.validate(form.toRequest()));
    }
    
    /**
     * Execute import for selected candidates from Console.
     *
     * @param form execute form
     * @return import result
     * @throws NacosException if import cannot start
     */
    @Since("3.2.2")
    @PostMapping("/execute")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<AiResourceImportExecuteResponse> execute(AiResourceImportExecuteForm form)
        throws NacosException {
        form.validate();
        return Result.success(importProxy.execute(form.toRequest()));
    }
}
