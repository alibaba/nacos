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

package com.alibaba.nacos.ai.service.hub;

import com.alibaba.nacos.api.ai.model.hub.HubSkillListRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Service-boundary Hub read request validation.
 *
 * @author nacos
 */
class HubSkillReadRequestValidatorTest {
    
    @Test
    void acceptsAndNormalizesValidRequest() throws NacosException {
        HubSkillListRequest request = new HubSkillListRequest();
        request.setQuery("  find-skills  ");
        request.setTagsAll(Arrays.asList(" search ", "tools", "search", " ", null));
        
        HubSkillReadRequestValidator.validate(request);
        
        assertEquals("find-skills", request.getQuery());
        assertEquals(List.of("search", "tools"), request.getTagsAll());
        assertEquals(1, request.getPageNo());
        assertEquals(20, request.getPageSize());
    }
    
    @Test
    void rejectsInvalidPageBounds() {
        HubSkillListRequest request = new HubSkillListRequest();
        request.setPageNo(0);
        assertInvalid(() -> HubSkillReadRequestValidator.validate(request));
        request.setPageNo(-1);
        assertInvalid(() -> HubSkillReadRequestValidator.validate(request));
        request.setPageNo(1);
        request.setPageSize(0);
        assertInvalid(() -> HubSkillReadRequestValidator.validate(request));
        request.setPageSize(-1);
        assertInvalid(() -> HubSkillReadRequestValidator.validate(request));
        request.setPageSize(101);
        assertInvalid(() -> HubSkillReadRequestValidator.validate(request));
    }
    
    @Test
    void rejectsOversizedQueryAndTags() {
        HubSkillListRequest request = new HubSkillListRequest();
        request.setQuery(new String(new char[1025]).replace('\0', 'x'));
        assertInvalid(() -> HubSkillReadRequestValidator.validate(request));
        request.setQuery(null);
        List<String> tags = new ArrayList<>();
        for (int i = 0; i < 33; i++) {
            tags.add("tag-" + i);
        }
        request.setTagsAll(tags);
        assertInvalid(() -> HubSkillReadRequestValidator.validate(request));
        request.setTagsAll(List.of(new String(new char[129]).replace('\0', 'x')));
        assertInvalid(() -> HubSkillReadRequestValidator.validate(request));
    }
    
    @Test
    void blankFiltersBecomeEmpty() throws NacosException {
        HubSkillListRequest request = new HubSkillListRequest();
        request.setQuery("   ");
        request.setTagsAll(Arrays.asList(null, " "));
        HubSkillReadRequestValidator.validate(request);
        assertNull(request.getQuery());
        assertEquals(List.of(), request.getTagsAll());
    }
    
    @Test
    void rejectsMissingOrNonpositiveEntryId() {
        assertInvalid(() -> HubSkillReadRequestValidator.validateEntryId(null));
        assertInvalid(() -> HubSkillReadRequestValidator.validateEntryId(0L));
        assertInvalid(() -> HubSkillReadRequestValidator.validateEntryId(-1L));
    }
    
    @Test
    void rejectsMissingListRequestWithControlledError() {
        assertInvalid(() -> HubSkillReadRequestValidator.validate(null));
    }
    
    @Test
    void acceptsExactQueryTagAndPageSizeLimits() throws NacosException {
        HubSkillListRequest request = new HubSkillListRequest();
        request.setPageSize(100);
        request.setQuery("x".repeat(1024));
        List<String> tags = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            tags.add("x".repeat(126) + String.format("%02d", i));
        }
        request.setTagsAll(tags);
        
        HubSkillReadRequestValidator.validate(request);
        
        assertEquals(100, request.getPageSize());
        assertEquals(1024, request.getQuery().length());
        assertEquals(tags, request.getTagsAll());
    }
    
    @Test
    void absentTagsBecomeEmptyAndValidationIsIdempotent() throws NacosException {
        HubSkillListRequest request = new HubSkillListRequest();
        request.setQuery("  find-skills  ");
        HubSkillReadRequestValidator.validate(request);
        HubSkillReadRequestValidator.validate(request);
        assertEquals("find-skills", request.getQuery());
        assertEquals(List.of(), request.getTagsAll());
    }
    
    @Test
    void acceptsPositiveEntryIdsWithoutTruncation() {
        assertDoesNotThrow(() -> HubSkillReadRequestValidator.validateEntryId(1L));
        assertDoesNotThrow(() -> HubSkillReadRequestValidator.validateEntryId(Long.MAX_VALUE));
    }
    
    private static void assertInvalid(Executable operation) {
        NacosApiException exception = assertThrows(NacosApiException.class, operation);
        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode());
        assertEquals(ErrorCode.PARAMETER_VALIDATE_ERROR.getCode(), exception.getDetailErrCode());
    }
}
