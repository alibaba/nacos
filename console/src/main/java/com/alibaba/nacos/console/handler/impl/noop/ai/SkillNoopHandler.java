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

package com.alibaba.nacos.console.handler.impl.noop.ai;

import com.alibaba.nacos.ai.form.skills.admin.SkillForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillListForm;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillBasicInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.console.handler.ai.SkillHandler;
import com.alibaba.nacos.core.model.form.PageForm;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * Noop implementation of Skill handler.
 * Used when AI module is not enabled or both `naming` and `config` modules are not available.
 *
 * @author nacos
 */
@Service
@ConditionalOnMissingBean(value = SkillHandler.class, ignored = SkillNoopHandler.class)
public class SkillNoopHandler implements SkillHandler {
    
    private static final String SKILL_NOT_ENABLED_MESSAGE = 
            "Nacos AI Skill module and API required both `naming` and `config` module.";

    @Override
    public Skill getSkill(SkillForm form) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                SKILL_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public void deleteSkill(SkillForm form) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                SKILL_NOT_ENABLED_MESSAGE);
    }

    @Override
    public Page<SkillBasicInfo> listSkills(SkillListForm skillListForm, PageForm pageForm) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                SKILL_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public String uploadSkillFromZip(String namespaceId, byte[] zipBytes) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                SKILL_NOT_ENABLED_MESSAGE);
    }
}
