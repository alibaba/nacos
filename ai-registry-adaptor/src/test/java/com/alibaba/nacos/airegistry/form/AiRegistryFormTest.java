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

package com.alibaba.nacos.airegistry.form;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiRegistryFormTest {
    
    @Test
    void testListServerFormValidateAcceptsValidSearchMode() {
        ListServerForm form = new ListServerForm();
        form.setOffset(1);
        form.setLimit(Constants.MAX_LIST_SIZE);
        form.setNamespaceId("public");
        form.setServerName("server");
        form.setSearchMode(Constants.MCP_LIST_SEARCH_BLUR);
        
        assertDoesNotThrow(form::validate);
        assertEquals(1, form.getOffset());
        assertEquals(Constants.MAX_LIST_SIZE, form.getLimit());
        assertEquals("public", form.getNamespaceId());
        assertEquals("server", form.getServerName());
        assertEquals(Constants.MCP_LIST_SEARCH_BLUR, form.getSearchMode());
    }
    
    @Test
    void testListServerFormValidateRejectsNegativeOffset() {
        ListServerForm form = new ListServerForm();
        form.setOffset(-1);
        
        assertThrows(NacosApiException.class, form::validate);
    }
    
    @Test
    void testListServerFormValidateRejectsLimitTooLarge() {
        ListServerForm form = new ListServerForm();
        form.setLimit(Constants.MAX_LIST_SIZE + 1);
        
        assertThrows(NacosApiException.class, form::validate);
    }
    
    @Test
    void testListServerFormValidateRejectsUnknownSearchMode() {
        ListServerForm form = new ListServerForm();
        form.setSearchMode("unknown");
        
        assertThrows(NacosApiException.class, form::validate);
    }
    
    @Test
    void testListServersOfficialFormResolveOffsetDefaultsToZero() throws Exception {
        ListServersOfficialForm form = new ListServersOfficialForm();
        
        assertEquals(0, form.resolveOffset());
        form.validate();
        assertEquals(ListServersOfficialForm.DEFAULT_LIMIT, form.getLimit());
    }
    
    @Test
    void testListServersOfficialFormValidateRejectsNegativeLimit() {
        ListServersOfficialForm form = new ListServersOfficialForm();
        form.setLimit(-1);
        
        assertThrows(NacosApiException.class, form::validate);
    }
    
    @Test
    void testListServersOfficialFormValidateRejectsLimitTooLarge() {
        ListServersOfficialForm form = new ListServersOfficialForm();
        form.setLimit(ListServersOfficialForm.MAX_LIMIT + 1);
        
        assertThrows(NacosApiException.class, form::validate);
    }
    
    @Test
    void testListServersOfficialFormResolveOffsetRejectsNegativeCursor() {
        ListServersOfficialForm form = new ListServersOfficialForm();
        form.setCursor("-1");
        
        assertThrows(NacosApiException.class, form::resolveOffset);
    }
    
    @Test
    void testListServersOfficialFormResolveOffsetRejectsInvalidCursor() {
        ListServersOfficialForm form = new ListServersOfficialForm();
        form.setCursor("abc");
        
        assertThrows(NacosApiException.class, form::resolveOffset);
    }
    
    @Test
    void testListServersOfficialFormGetterAndSetter() throws Exception {
        ListServersOfficialForm form = new ListServersOfficialForm();
        form.setCursor("7");
        form.setLimit(null);
        form.setSearch("redis");
        form.setUpdatedSince("2026-06-01T00:00:00Z");
        
        form.validate();
        
        assertEquals(7, form.resolveOffset());
        assertEquals(ListServersOfficialForm.DEFAULT_LIMIT, form.getLimit());
        assertEquals("redis", form.getSearch());
        assertEquals("2026-06-01T00:00:00Z", form.getUpdatedSince());
    }
    
    @Test
    void testListServersNacosFormGetterAndSetter() {
        ListServersNacosForm form = new ListServersNacosForm();
        form.setNamespaceId("namespace");
        
        assertEquals("namespace", form.getNamespaceId());
    }
    
    @Test
    void testGetServerFormValidateAndNamespace() {
        GetServerForm form = new GetServerForm();
        form.setNamespaceId("namespace");
        
        assertDoesNotThrow(form::validate);
        assertEquals("namespace", form.getNamespaceId());
    }
    
    @Test
    void testSkillsSearchFormValidateRequiresNamespaceId() {
        SkillsSearchForm form = new SkillsSearchForm();
        
        assertThrows(NacosApiException.class, form::validate);
    }
    
    @Test
    void testSkillsSearchFormValidateDefaultsAndCapsLimit() throws Exception {
        SkillsSearchForm form = new SkillsSearchForm();
        form.setNamespaceId("namespace");
        form.setQ("deploy");
        form.setLimit(100);
        
        form.validate();
        
        assertEquals("namespace", form.getNamespaceId());
        assertEquals("deploy", form.getQ());
        assertEquals(10, form.getLimit());
        
        form.setLimit(0);
        form.validate();
        assertEquals(10, form.getLimit());
    }
    
    @Test
    void testSkillsFileQueryFormValidateRequiresFields() {
        SkillsFileQueryForm form = new SkillsFileQueryForm();
        
        assertThrows(NacosApiException.class, form::validate);
        
        form.setNamespaceId("namespace");
        assertThrows(NacosApiException.class, form::validate);
        
        form.setSkillName("skill");
        assertThrows(NacosApiException.class, form::validate);
    }
    
    @Test
    void testSkillsFileQueryFormValidateRejectsUnsafeFilePath() {
        SkillsFileQueryForm form = new SkillsFileQueryForm();
        form.setNamespaceId("namespace");
        form.setSkillName("skill");
        form.setFilePath("../README.md");
        
        assertThrows(NacosApiException.class, form::validate);
        
        form.setFilePath("/README.md");
        assertThrows(NacosApiException.class, form::validate);
        
        form.setFilePath("\\README.md");
        assertThrows(NacosApiException.class, form::validate);
    }
    
    @Test
    void testSkillsFileQueryFormValidateAcceptsRelativeFilePath() {
        SkillsFileQueryForm form = new SkillsFileQueryForm();
        form.setNamespaceId("namespace");
        form.setSkillName("skill");
        form.setFilePath("docs/README.md");
        
        assertDoesNotThrow(form::validate);
        assertEquals("namespace", form.getNamespaceId());
        assertEquals("skill", form.getSkillName());
        assertEquals("docs/README.md", form.getFilePath());
    }
}
