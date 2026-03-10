/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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
import com.alibaba.nacos.ai.form.skills.admin.SkillDetailForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillListForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillUpdateForm;
import com.alibaba.nacos.ai.param.SkillHttpParamExtractor;
import com.alibaba.nacos.ai.service.skills.SkillOperationService;
import com.alibaba.nacos.ai.utils.SkillRequestUtil;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillBasicInfo;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Skill admin controller.
 *
 * @author nacos
 */
@NacosApi
@RestController
@RequestMapping(Constants.Skills.ADMIN_PATH)
@ExtractorManager.Extractor(httpExtractor = SkillHttpParamExtractor.class)
public class SkillAdminController {
    
    private final SkillOperationService skillOperationService;
    
    public SkillAdminController(SkillOperationService skillOperationService) {
        this.skillOperationService = skillOperationService;
    }
    
    /**
     * Register skill.
     *
     * @param form the skill detail form to register
     * @return result of the registration operation
     * @throws NacosException if the skill registration fails
     */
    @PostMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<String> registerSkill(SkillDetailForm form) throws NacosException {
        form.validate();
        Skill skill = SkillRequestUtil.parseSkill(form);
        String skillName = skillOperationService.registerSkill(skill, form.getNamespaceId());
        return Result.success(skillName);
    }
    
    /**
     * Get skill.
     *
     * @param form the skill form to get
     * @return result of the get operation
     * @throws NacosException if the skill get fails
     */
    @GetMapping
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<Skill> getSkill(SkillForm form) throws NacosException {
        form.validate();
        return Result.success(
                skillOperationService.getSkillDetail(form.getNamespaceId(), form.getSkillName()));
    }
    
    /**
     * Update skill.
     *
     * @param form the skill update form to update
     * @return result of the update operation
     * @throws NacosException if the skill update fails
     */
    @PutMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<String> updateSkill(SkillUpdateForm form) throws NacosException {
        form.validate();
        Skill skill = SkillRequestUtil.parseSkill(form);
        skillOperationService.updateSkill(skill, form.getNamespaceId());
        return Result.success("ok");
    }
    
    /**
     * Delete skill.
     *
     * @param form the skill form to delete
     * @return result of the deletion operation
     * @throws NacosException if the skill deletion fails
     */
    @DeleteMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<String> deleteSkill(SkillForm form) throws NacosException {
        form.validate();
        skillOperationService.deleteSkill(form.getNamespaceId(), form.getSkillName());
        return Result.success("ok");
    }
    
    /**
     * List skills.
     *
     * @param skillListForm the skill list form to list
     * @param pageForm the page form to list
     * @return result of the list operation
     * @throws NacosException if the skill list fails
     */
    @GetMapping("/list")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<Page<SkillBasicInfo>> listSkills(SkillListForm skillListForm, PageForm pageForm)
            throws NacosException {
        skillListForm.validate();
        pageForm.validate();
        return Result.success(
                skillOperationService.listSkills(skillListForm.getNamespaceId(), skillListForm.getSkillName(),
                        skillListForm.getSearch(), pageForm.getPageNo(), pageForm.getPageSize()));
    }

    /**
     * Upload skill from zip file.
     *
     * @param request HTTP servlet request
     * @param namespaceId namespace ID
     * @param file zip file containing skill
     * @return result of the upload operation
     * @throws NacosException if the upload fails
     */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    @ExtractorManager.Extractor(httpExtractor = ExtractorManager.DefaultHttpExtractor.class)
    public Result<String> uploadSkill(HttpServletRequest request,
            @RequestParam(value = "namespaceId", required = false) String namespaceId,
            @RequestParam("file") MultipartFile file) throws NacosException {
        byte[] zipBytes = SkillRequestUtil.validateAndExtractZipBytes(file);
        String skillName = skillOperationService.uploadSkillFromZip(namespaceId, zipBytes);
        return Result.success(skillName);
    }
}
