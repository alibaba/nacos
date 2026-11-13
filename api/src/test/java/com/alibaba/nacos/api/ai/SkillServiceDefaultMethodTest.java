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

package com.alibaba.nacos.api.ai;

import com.alibaba.nacos.api.ai.model.skills.SkillBasicInfo;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillServiceDefaultMethodTest {
    
    @Test
    void thirdPartyImplementationRejectsUnsupportedSearchAndSharedDownload() throws Exception {
        SkillService service = mock(SkillService.class, CALLS_REAL_METHODS);
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED,
            assertThrows(NacosException.class,
                () -> service.searchSkills(null, null, 1, 20)).getErrCode());
        SkillBasicInfo shared = new SkillBasicInfo();
        shared.setName("17");
        shared.setDiscoverySource("HUB");
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED,
            assertThrows(NacosException.class,
                () -> service.downloadSkillZipBySearchResult(shared)).getErrCode());
        assertEquals(NacosException.INVALID_PARAM,
            assertThrows(NacosException.class,
                () -> service.downloadSkillZipBySearchResult(null)).getErrCode());
    }
    
    @Test
    void thirdPartyImplementationKeepsExistingLocalDownload() throws Exception {
        SkillService service = mock(SkillService.class, CALLS_REAL_METHODS);
        SkillBasicInfo local = new SkillBasicInfo();
        local.setName("local");
        when(service.downloadSkillZip("local")).thenReturn(new byte[] {1, 2});
        assertArrayEquals(new byte[] {1, 2}, service.downloadSkillZipBySearchResult(local));
    }
}
