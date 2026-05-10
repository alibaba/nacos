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

package com.alibaba.nacos.console.proxy.ai;

import com.alibaba.nacos.ai.form.AiResourceFilterableForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillListForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillPublishForm;
import com.alibaba.nacos.api.ai.model.skills.SkillSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.ai.SkillHandler;
import com.alibaba.nacos.core.model.form.PageForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SkillProxy.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
public class SkillProxyTest {
    
    private static final String NAMESPACE_ID = "test-ns";
    
    private static final String SKILL_NAME = "test-skill";
    
    @Mock
    private SkillHandler skillHandler;
    
    private SkillProxy skillProxy;
    
    @BeforeEach
    public void setUp() {
        skillProxy = new SkillProxy(skillHandler);
    }
    
    @Test
    public void testForcePublish() throws NacosException {
        SkillPublishForm form = new SkillPublishForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        form.setVersion("v1");
        form.setUpdateLatestLabel(true);
        
        doNothing().when(skillHandler).forcePublish(form);
        
        skillProxy.forcePublish(form);
        
        verify(skillHandler, times(1)).forcePublish(form);
    }
    
    @Test
    public void testForcePublishPropagatesException() throws NacosException {
        SkillPublishForm form = new SkillPublishForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setSkillName(SKILL_NAME);
        form.setVersion("v1");
        
        NacosApiException expectedException = new NacosApiException(NacosException.NOT_FOUND,
            com.alibaba.nacos.api.model.v2.ErrorCode.RESOURCE_NOT_FOUND, "version not found");
        doThrow(expectedException).when(skillHandler).forcePublish(any(SkillPublishForm.class));
        
        NacosApiException ex =
            assertThrows(NacosApiException.class, () -> skillProxy.forcePublish(form));
        assertEquals(NacosException.NOT_FOUND, ex.getErrCode());
    }
    
    @Test
    public void testListSkills() throws NacosException {
        SkillListForm listForm = new SkillListForm();
        listForm.setNamespaceId(NAMESPACE_ID);
        listForm.setSkillName(SKILL_NAME);
        AiResourceFilterableForm filterableForm = new AiResourceFilterableForm();
        filterableForm.setOwner("alice");
        PageForm pageForm = new PageForm();
        pageForm.setPageNo(1);
        pageForm.setPageSize(10);
        Page<SkillSummary> page = new Page<>();
        page.setTotalCount(1);
        SkillSummary item = new SkillSummary();
        item.setName(SKILL_NAME);
        page.setPageItems(java.util.List.of(item));
        when(skillHandler.listSkills(any(SkillListForm.class), any(AiResourceFilterableForm.class),
            any(PageForm.class))).thenReturn(page);
        
        Page<SkillSummary> result = skillProxy.listSkills(listForm, filterableForm, pageForm);
        
        assertNotNull(result);
        assertEquals(1, result.getTotalCount());
        verify(skillHandler, times(1)).listSkills(listForm, filterableForm, pageForm);
    }
}
