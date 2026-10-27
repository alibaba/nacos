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

package com.alibaba.nacos.console.handler.impl.remote.ai;

import com.alibaba.nacos.ai.form.AiResourceFilterableForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillListForm;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.skills.SkillDownload;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import com.alibaba.nacos.maintainer.client.ai.SkillMaintainerService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SharedSkillRemoteReadTest {
    
    private final NacosMaintainerClientHolder holder = mock(NacosMaintainerClientHolder.class);
    private final HubRemoteCallerGuard guard = mock(HubRemoteCallerGuard.class);
    private final SkillRemoteHandler handler = new SkillRemoteHandler(holder);
    
    @Test
    void sharedReadsUseExistingMaintainerCallsAfterAdmittingOriginalCaller() throws Exception {
        handler.setSharedReadGuard(guard);
        AiMaintainerService ai = mock(AiMaintainerService.class);
        SkillMaintainerService skill = mock(SkillMaintainerService.class);
        when(holder.getAiMaintainerService()).thenReturn(ai);
        when(ai.skill()).thenReturn(skill);
        SkillListForm form = form();
        handler.getSkill(form);
        handler.getSkillVersion(form);
        handler.listSkills(form, new AiResourceFilterableForm(), new PageForm());
        SkillDownload download = new SkillDownload();
        when(skill.downloadSkillVersion(AiConstants.Hub.NAMESPACE_ID, "91", "1.0.0"))
            .thenReturn(download);
        assertSame(download, handler.downloadSkillPackage(form));
        verify(guard, times(4)).requireForwardableRead();
        verify(skill).getSkillMeta(AiConstants.Hub.NAMESPACE_ID, "91");
        verify(skill).getSkillVersionDetail(AiConstants.Hub.NAMESPACE_ID, "91", "1.0.0");
    }
    
    @Test
    void unforwardableSharedIdentityFailsBeforeContactingAdmin() throws Exception {
        handler.setSharedReadGuard(guard);
        doThrow(new NacosException(NacosException.NO_RIGHT, "not forwardable"))
            .when(guard).requireForwardableRead();
        assertThrows(NacosException.class, () -> handler.getSkill(form()));
        assertThrows(NacosException.class, () -> handler.getSkillVersion(form()));
        assertThrows(NacosException.class,
            () -> handler.listSkills(form(), new AiResourceFilterableForm(), new PageForm()));
        assertThrows(NacosException.class, () -> handler.downloadSkillPackage(form()));
        verifyNoInteractions(holder);
    }
    
    private SkillListForm form() {
        SkillListForm form = new SkillListForm();
        form.setNamespaceId(AiConstants.Hub.NAMESPACE_ID);
        form.setSkillName("91");
        form.setVersion("1.0.0");
        return form;
    }
}
