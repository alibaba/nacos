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
import com.alibaba.nacos.ai.service.SyncEffectService;
import com.alibaba.nacos.api.ai.model.skills.SkillSubscription;
import com.alibaba.nacos.api.ai.model.skills.SkillSubscriptionDocument;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.dump.DumpRequest;
import com.alibaba.nacos.config.server.service.dump.DumpService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ConfigSkillSubscriptionStorage}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class ConfigSkillSubscriptionStorageTest {
    
    @Mock
    private ConfigInfoPersistService configInfoPersistService;
    
    @Mock
    private ConfigOperationService configOperationService;
    
    @Mock
    private DumpService dumpService;
    
    @Mock
    private SyncEffectService syncEffectService;
    
    private ConfigSkillSubscriptionStorage storage;
    
    @BeforeEach
    void setUp() {
        storage = new ConfigSkillSubscriptionStorage(configInfoPersistService,
            configOperationService, dumpService, syncEffectService);
    }
    
    @Test
    void testBuildDataIdUsesSubscriberSha256() {
        assertEquals("subscriber_2bd806c97f0e00af1a1fc3328fa763a9269723c8db8fac4f93af71"
            + "db186d6e90.json", ConfigSkillSubscriptionStorage.buildDataId("alice"));
    }
    
    @Test
    void testGetReturnsEmptyDocumentWhenConfigMissing() throws Exception {
        when(configInfoPersistService.findConfigAllInfo(
            ConfigSkillSubscriptionStorage.buildDataId("alice"),
            Constants.Skills.SKILL_SUBSCRIPTION_GROUP, "public")).thenReturn(null);
        
        SkillSubscriptionDocument actual = storage.get("public", "alice");
        
        assertEquals("public", actual.getNamespaceId());
        assertEquals("alice", actual.getSubscriber());
        assertEquals(Constants.Skills.SKILL_SUBSCRIPTION_GROUP, actual.getGroupId());
        assertEquals(ConfigSkillSubscriptionStorage.buildDataId("alice"), actual.getDataId());
        assertEquals(0, actual.getSubscriptions().size());
    }
    
    @Test
    void testGetReadsPersistedConfigContent() throws Exception {
        SkillSubscriptionDocument document = new SkillSubscriptionDocument();
        SkillSubscription subscription = new SkillSubscription();
        subscription.setName("doc-format");
        document.getSubscriptions().add(subscription);
        ConfigAllInfo config = new ConfigAllInfo();
        config.setContent(JacksonUtils.toJson(document));
        when(configInfoPersistService.findConfigAllInfo(
            ConfigSkillSubscriptionStorage.buildDataId("alice"),
            Constants.Skills.SKILL_SUBSCRIPTION_GROUP, "public")).thenReturn(config);
        
        SkillSubscriptionDocument actual = storage.get("public", "alice");
        
        assertEquals(1, actual.getSubscriptions().size());
        assertEquals("doc-format", actual.getSubscriptions().get(0).getName());
    }
    
    @Test
    void testSaveWritesExpectedConfigKey() throws Exception {
        SkillSubscriptionDocument document = new SkillSubscriptionDocument();
        
        storage.save("public", "alice", document);
        
        ArgumentCaptor<ConfigForm> formCaptor = ArgumentCaptor.forClass(ConfigForm.class);
        verify(configOperationService).publishConfig(formCaptor.capture(), any(), any());
        ConfigForm form = formCaptor.getValue();
        assertEquals("public", form.getNamespaceId());
        assertEquals(Constants.Skills.SKILL_SUBSCRIPTION_GROUP, form.getGroup());
        assertEquals(ConfigSkillSubscriptionStorage.buildDataId("alice"), form.getDataId());
        assertEquals("alice", form.getSrcUser());
        ArgumentCaptor<DumpRequest> dumpRequestCaptor =
            ArgumentCaptor.forClass(DumpRequest.class);
        verify(dumpService).dump(dumpRequestCaptor.capture());
        DumpRequest dumpRequest = dumpRequestCaptor.getValue();
        assertEquals(form.getDataId(), dumpRequest.getDataId());
        assertEquals(form.getGroup(), dumpRequest.getGroup());
        assertEquals(form.getNamespaceId(), dumpRequest.getTenant());
        verify(syncEffectService).toSync(any(ConfigForm.class), anyLong());
    }
}
