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

package com.alibaba.nacos.client.ai;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.skills.SkillBasicInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.client.ai.remote.AiHttpClientProxy;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class SkillDiscoveryClientTest {
    
    private final AiHttpClientProxy proxy = mock(AiHttpClientProxy.class);
    
    @Test
    void defaultDiscoveryOnlyUsesBoundNamespace() throws Exception {
        Page<SkillBasicInfo> expected = page(1, skill("same"));
        when(proxy.searchSkills("public", "text", null, 1, 20)).thenReturn(expected);
        Page<SkillBasicInfo> result = new SkillDiscoveryClient(proxy, "public", false)
            .search("text", null, 1, 20);
        assertSame(expected, result);
        assertEquals("NAMESPACE", result.getPageItems().get(0).getDiscoverySource());
        assertEquals("public", result.getPageItems().get(0).getNamespaceId());
        verify(proxy).searchSkills("public", "text", null, 1, 20);
        verifyNoMoreInteractions(proxy);
    }
    
    @Test
    void combinedPageRetainsSameNamesAndSharedServerOrder() throws Exception {
        when(proxy.searchSkills("team", "text", Arrays.asList("tag"), 2, 3))
            .thenReturn(page(4, skill("same")));
        when(proxy.searchSkills(AiConstants.Hub.NAMESPACE_ID, "text", Arrays.asList("tag"), 1, 1))
            .thenReturn(page(4, skill("17")));
        when(proxy.searchSkills(AiConstants.Hub.NAMESPACE_ID, "text", Arrays.asList("tag"), 1, 3))
            .thenReturn(page(4, skill("17"), skill("18"), skill("19")));
        Page<SkillBasicInfo> result = new SkillDiscoveryClient(proxy, "team", true)
            .search("text", Arrays.asList("tag"), 2, 3);
        assertEquals(8, result.getTotalCount());
        assertEquals(3, result.getPagesAvailable());
        assertEquals(2, result.getPageNumber());
        assertEquals(Arrays.asList("same", "17", "18"), Arrays.asList(
            result.getPageItems().get(0).getName(), result.getPageItems().get(1).getName(),
            result.getPageItems().get(2).getName()));
        assertEquals("HUB", result.getPageItems().get(1).getDiscoverySource());
    }
    
    @Test
    void shiftedSharedPageFetchesOnlyTwoBoundedPages() throws Exception {
        when(proxy.searchSkills("team", null, null, 3, 3)).thenReturn(page(4));
        when(proxy.searchSkills(AiConstants.Hub.NAMESPACE_ID, null, null, 1, 1))
            .thenReturn(page(6, skill("10")));
        when(proxy.searchSkills(AiConstants.Hub.NAMESPACE_ID, null, null, 1, 3))
            .thenReturn(page(6, skill("10"), skill("11"), skill("12")));
        when(proxy.searchSkills(AiConstants.Hub.NAMESPACE_ID, null, null, 2, 3))
            .thenReturn(page(6, skill("13"), skill("14"), skill("15")));
        Page<SkillBasicInfo> result = new SkillDiscoveryClient(proxy, "team", true)
            .search(null, null, 3, 3);
        assertEquals(Arrays.asList("12", "13", "14"), Arrays.asList(
            result.getPageItems().get(0).getName(), result.getPageItems().get(1).getName(),
            result.getPageItems().get(2).getName()));
        assertEquals(10, result.getTotalCount());
    }
    
    @Test
    void explicitSharedBindingDoesNotQueryTwice() throws Exception {
        when(proxy.searchSkills(AiConstants.Hub.NAMESPACE_ID, null, null, 1, 5))
            .thenReturn(page(1, skill("12")));
        new SkillDiscoveryClient(proxy, AiConstants.Hub.NAMESPACE_ID, true)
            .search(null, null, 1, 5);
        verify(proxy).searchSkills(AiConstants.Hub.NAMESPACE_ID, null, null, 1, 5);
        verifyNoMoreInteractions(proxy);
    }
    
    @Test
    void invalidPagingAndSharedErrorsDoNotReturnPartialResults() throws Exception {
        SkillDiscoveryClient client = new SkillDiscoveryClient(proxy, "team", true);
        assertEquals(NacosException.INVALID_PARAM,
            assertThrows(NacosException.class, () -> client.search(null, null, 0, 10))
                .getErrCode());
        assertThrows(NacosException.class,
            () -> client.search(null, null, Integer.MAX_VALUE, 10));
        when(proxy.searchSkills("team", null, null, 1, 10)).thenReturn(page(1, skill("local")));
        NacosException denied = new NacosException(NacosException.NO_RIGHT, "denied");
        when(proxy.searchSkills(AiConstants.Hub.NAMESPACE_ID, null, null, 1, 1)).thenThrow(denied);
        assertSame(denied,
            assertThrows(NacosException.class, () -> client.search(null, null, 1, 10)));
    }
    
    @Test
    void selectedSharedDownloadKeepsIdentityAndPinnedVersion() throws Exception {
        SkillBasicInfo shared = skill("17");
        shared.setDiscoverySource("HUB");
        shared.setHubEntryId(17L);
        shared.setVersion("v2");
        when(proxy.downloadSkillZip(AiConstants.Hub.NAMESPACE_ID, "17", "v2", null))
            .thenReturn(new byte[] {1, 2});
        SkillDiscoveryClient client = new SkillDiscoveryClient(proxy, "team", false);
        assertArrayEquals(new byte[] {1, 2}, client.download(shared));
        SkillBasicInfo foreign = skill("local");
        foreign.setNamespaceId("another-team");
        assertThrows(NacosException.class, () -> client.download(foreign));
        shared.setVersion(null);
        assertThrows(NacosException.class, () -> client.download(shared));
    }
    
    private static SkillBasicInfo skill(String name) {
        SkillBasicInfo result = new SkillBasicInfo();
        result.setName(name);
        result.setDisplayName("same");
        return result;
    }
    
    private static Page<SkillBasicInfo> page(int total, SkillBasicInfo... items) {
        Page<SkillBasicInfo> result = new Page<>();
        result.setTotalCount(total);
        result.setPageItems(items.length == 0 ? Collections.emptyList() : Arrays.asList(items));
        return result;
    }
}
