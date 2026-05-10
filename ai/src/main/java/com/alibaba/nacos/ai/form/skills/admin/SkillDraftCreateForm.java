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

package com.alibaba.nacos.ai.form.skills.admin;

import com.alibaba.nacos.ai.utils.SkillRequestUtil;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;

import java.io.Serial;

/**
 * Create skill draft: inherits {@code skillCard} from {@link SkillDetailForm} (required unless forking). When
 * {@code basedOnVersion} is set, forks from that version and must not send {@code skillCard}.
 *
 * @author nacos
 */
public class SkillDraftCreateForm extends SkillDetailForm {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    private String basedOnVersion;
    
    private String targetVersion;
    
    private String commitMsg;
    
    /**
     * Parsed skill for create-draft after {@link #prepareCreateDraftRequest()}; not part of the serialized form.
     */
    private transient Skill resolvedInitialSkill;
    
    /**
     * The request form allow user create a new craft from current version. So if {@code basedOnVersion} is set,
     * {@code skillCard} will be ignored, and {@code skillName} is required. Otherwise, means users create a new skill,
     * so {@code skillCard} is required and {@code skillName} is ignored.
     */
    @Override
    public void validate() throws NacosApiException {
        fillDefaultNamespaceId();
        if (StringUtils.isNotBlank(basedOnVersion)) {
            if (StringUtils.isEmpty(getSkillName())) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "Required parameter 'skillName' when basedOnVersion is set");
            }
            return;
        }
        super.validate();
    }
    
    /**
     * Validates this request, normalizes {@link #setSkillName(String)} when the name only appears inside
     * {@code skillCard}, and caches the parsed skill for {@link #getResolvedInitialSkillOrNull()}.
     * <p>
     * Console and admin controllers must invoke this before {@code SkillProxy} / {@code SkillHandler}; handlers then
     * only forward to service or remote client without repeating validation.
     * </p>
     */
    public void prepareCreateDraftRequest() throws NacosApiException {
        validate();
        resolvedInitialSkill = parseInitialSkillOrNull();
        String skillName = requireResolvedSkillName(resolvedInitialSkill);
        if (resolvedInitialSkill != null) {
            SkillRequestUtil.validateInitialDraftSkill(resolvedInitialSkill, getNamespaceId(),
                skillName);
        }
        setSkillName(skillName);
    }
    
    /**
     * Non-null only after {@link #prepareCreateDraftRequest()} when {@code skillCard} was present (not forking).
     */
    public Skill getResolvedInitialSkillOrNull() {
        return resolvedInitialSkill;
    }
    
    public String getBasedOnVersion() {
        return basedOnVersion;
    }
    
    public void setBasedOnVersion(String basedOnVersion) {
        this.basedOnVersion = basedOnVersion;
    }
    
    public String getTargetVersion() {
        return targetVersion;
    }
    
    public void setTargetVersion(String targetVersion) {
        this.targetVersion = targetVersion;
    }
    
    public String getCommitMsg() {
        return commitMsg;
    }
    
    public void setCommitMsg(String commitMsg) {
        this.commitMsg = commitMsg;
    }
    
    private Skill parseInitialSkillOrNull() throws NacosApiException {
        if (StringUtils.isBlank(getSkillCard())) {
            return null;
        }
        Skill skill = SkillRequestUtil.parseSkill(this);
        if (StringUtils.isNotBlank(getSkillName()) && !getSkillName().equals(skill.getName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "skillCard name must match skillName parameter");
        }
        return skill;
    }
    
    private String requireResolvedSkillName(Skill initialOrNull) throws NacosApiException {
        String skillName = StringUtils.isNotBlank(getSkillName()) ? getSkillName()
            : (initialOrNull != null ? initialOrNull.getName() : null);
        if (StringUtils.isBlank(skillName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "skillName or skillCard with name is required");
        }
        return skillName;
    }
}
