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

package com.alibaba.nacos.ai.service.skills;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.skills.SkillIndexManifest;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SkillClientOperationServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
class SkillClientOperationServiceImplTest {
    
    @Mock
    private SkillOperationService skillOperationService;
    
    @Mock
    private SkillIndexManifestService manifestService;
    
    @Mock
    private AiResourceVersionPersistService aiResourceVersionPersistService;
    
    private SkillClientOperationServiceImpl service;
    
    @BeforeEach
    void setUp() {
        service = new SkillClientOperationServiceImpl(skillOperationService, manifestService,
            aiResourceVersionPersistService);
    }
    
    @Test
    void querySkillShouldRejectBlankName() {
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.querySkill("public", " ", null, null, null));
        
        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode());
    }
    
    @Test
    void querySkillShouldReturnNotModifiedWhenStoredMd5MatchesClient()
        throws NacosException {
        when(manifestService.query("public", "skill-a")).thenReturn(manifest("1.0.0"));
        AiResourceVersion version = versionRow("{\"contentMd5\":\"md5-1\"}");
        when(aiResourceVersionPersistService.find("public", "skill-a",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0")).thenReturn(version);
        
        SkillQueryResult result = service.querySkill("public", "skill-a", null, "latest",
            "md5-1");
        
        assertTrue(result.isNotModified());
        assertNull(result.getSkill());
        assertEquals("md5-1", result.getMd5());
        assertEquals("1.0.0", result.getResolvedVersion());
        verify(skillOperationService, never()).querySkill("public", "skill-a", null, "latest");
    }
    
    @Test
    void querySkillShouldLoadSkillWhenStoredMd5DiffersFromClient() throws NacosException {
        when(manifestService.query("public", "skill-a")).thenReturn(manifest("1.0.0"));
        when(aiResourceVersionPersistService.find("public", "skill-a",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0"))
            .thenReturn(versionRow("{\"contentMd5\":\"md5-2\"}"));
        Skill skill = newSkill();
        when(skillOperationService.querySkill("public", "skill-a", null, "latest"))
            .thenReturn(skill);
        
        SkillQueryResult result = service.querySkill("public", "skill-a", null, "latest",
            "md5-1");
        
        assertFalse(result.isNotModified());
        assertSame(skill, result.getSkill());
        assertEquals("md5-2", result.getMd5());
        assertEquals("1.0.0", result.getResolvedVersion());
    }
    
    @Test
    void querySkillShouldBackfillContentMd5ForLegacyStorage() throws NacosException {
        when(manifestService.query("public", "skill-a")).thenReturn(manifest("1.0.0"));
        when(aiResourceVersionPersistService.find("public", "skill-a",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0")).thenReturn(versionRow("{}"));
        Skill skill = newSkill();
        when(skillOperationService.querySkill("public", "skill-a", null, null)).thenReturn(skill);
        
        SkillQueryResult result = service.querySkill("public", "skill-a", null, null, null);
        
        assertFalse(result.isNotModified());
        assertSame(skill, result.getSkill());
        assertEquals(32, result.getMd5().length());
        verify(aiResourceVersionPersistService).updateStorageMd5("public", "skill-a",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0", result.getMd5());
    }
    
    @Test
    void querySkillShouldIgnoreBrokenStorageJsonAndStillReturnLoadedSkill()
        throws NacosException {
        when(manifestService.query("public", "skill-a")).thenReturn(manifest("1.0.0"));
        when(aiResourceVersionPersistService.find("public", "skill-a",
            Constants.Skills.RESOURCE_TYPE_SKILL, "1.0.0"))
            .thenReturn(versionRow("{broken"));
        Skill skill = newSkill();
        when(skillOperationService.querySkill("public", "skill-a", "1.0.0", null))
            .thenReturn(skill);
        
        SkillQueryResult result = service.querySkill("public", "skill-a", "1.0.0", null,
            "md5-1");
        
        assertSame(skill, result.getSkill());
        assertEquals(32, result.getMd5().length());
    }
    
    private static SkillIndexManifest manifest(String version) {
        SkillIndexManifest manifest = new SkillIndexManifest();
        manifest.setLabels(Map.of("latest", version));
        manifest.setVersions(Map.of(version, List.of("SKILL.md")));
        return manifest;
    }
    
    private static AiResourceVersion versionRow(String storage) {
        AiResourceVersion version = new AiResourceVersion();
        version.setStorage(storage);
        return version;
    }
    
    private static Skill newSkill() {
        Skill skill = new Skill();
        skill.setName("skill-a");
        skill.setSkillMd("---\nname: skill-a\ndescription: desc\n---\n\nbody");
        return skill;
    }
}
