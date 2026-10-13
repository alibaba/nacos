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
import com.alibaba.nacos.ai.form.hub.HubSkillReadEntryForm;
import com.alibaba.nacos.ai.form.hub.HubSkillReadListForm;
import com.alibaba.nacos.ai.param.HubSkillHttpParamExtractor;
import com.alibaba.nacos.api.ai.model.hub.HubSkillContent;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDetail;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDownload;
import com.alibaba.nacos.api.ai.model.hub.HubSkillSummary;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.console.proxy.ai.HubSkillReadProxy;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

import static com.alibaba.nacos.ai.constant.Constants.Hub.ENTRY_RESOURCE;
import static com.alibaba.nacos.plugin.auth.constant.Constants.Tag.ONLY_IDENTITY;

@NacosApi
@RestController
@RequestMapping(Constants.Hub.CONSOLE_PATH)
@ExtractorManager.Extractor(httpExtractor = HubSkillHttpParamExtractor.class)
public class ConsoleHubSkillReadController {
    
    private final HubSkillReadProxy proxy;
    
    public ConsoleHubSkillReadController(HubSkillReadProxy proxy) {
        this.proxy = proxy;
    }
    
    @Since("3.3.0")
    @GetMapping("/list")
    @Secured(action = ActionTypes.READ, signType = SignType.SPECIFIED,
        resource = ENTRY_RESOURCE, apiType = ApiType.CONSOLE_API, tags = {ONLY_IDENTITY})
    public Result<Page<HubSkillSummary>> list(HubSkillReadListForm form) throws NacosException {
        return Result.success(proxy.list(form.prepareRequest()));
    }
    
    @Since("3.3.0")
    @GetMapping("/detail")
    @Secured(action = ActionTypes.READ, signType = SignType.SPECIFIED,
        resource = ENTRY_RESOURCE, apiType = ApiType.CONSOLE_API, tags = {ONLY_IDENTITY})
    public Result<HubSkillDetail> detail(HubSkillReadEntryForm form)
        throws NacosException {
        return Result.success(proxy.detail(form.prepareEntryId()));
    }
    
    @Since("3.3.0")
    @GetMapping("/content")
    @Secured(action = ActionTypes.READ, signType = SignType.SPECIFIED,
        resource = ENTRY_RESOURCE, apiType = ApiType.CONSOLE_API, tags = {ONLY_IDENTITY})
    public Result<HubSkillContent> content(HubSkillReadEntryForm form)
        throws NacosException {
        return Result.success(proxy.content(form.prepareEntryId()));
    }
    
    /**
     * Download the fixed source package after Hub read and revocation checks.
     *
     * @param form immutable Hub Entry ID
     * @return original shared package, including the resolved version
     * @throws NacosException if the share is denied, revoked or unavailable
     */
    @Since("3.3.0")
    @GetMapping("/download")
    @Secured(action = ActionTypes.READ, signType = SignType.SPECIFIED,
        resource = ENTRY_RESOURCE, apiType = ApiType.CONSOLE_API, tags = {ONLY_IDENTITY})
    public ResponseEntity<byte[]> download(HubSkillReadEntryForm form) throws NacosException {
        HubSkillDownload download = proxy.download(form.prepareEntryId());
        String filename = download.getDisplayName() + "-" + download.getSharedVersion() + ".zip";
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/zip"))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8)
                    .build().toString())
            .header("X-Nacos-Hub-Entry-Id", String.valueOf(download.getHubEntryId()))
            .header("X-Nacos-Skill-Resolved-Version", download.getSharedVersion())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(download.getZipBytes());
    }
}
