/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.persistence.configuration.condition;

import com.alibaba.nacos.persistence.configuration.DatasourceConfiguration;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionDistributedEmbedStorageTest {
    
    @Mock
    ConditionContext context;
    
    @Mock
    AnnotatedTypeMetadata metadata;
    
    private ConditionDistributedEmbedStorage conditionDistributedEmbedStorage;
    
    @BeforeEach
    void init() {
        conditionDistributedEmbedStorage = new ConditionDistributedEmbedStorage();
        
    }
    
    @Test
    void testMatches() {
        MockedStatic<DatasourceConfiguration> propertyUtilMockedStatic = Mockito.mockStatic(DatasourceConfiguration.class);
        MockedStatic<EnvUtil> envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        
        propertyUtilMockedStatic.when(DatasourceConfiguration::isEmbeddedStorage).thenReturn(true);
        envUtilMockedStatic.when(EnvUtil::getStandaloneMode).thenReturn(true);
        assertFalse(conditionDistributedEmbedStorage.matches(context, metadata));
        
        Mockito.when(DatasourceConfiguration.isEmbeddedStorage()).thenReturn(true);
        Mockito.when(EnvUtil.getStandaloneMode()).thenReturn(false);
        propertyUtilMockedStatic.when(DatasourceConfiguration::isEmbeddedStorage).thenReturn(true);
        envUtilMockedStatic.when(EnvUtil::getStandaloneMode).thenReturn(false);
        assertTrue(conditionDistributedEmbedStorage.matches(context, metadata));
        
        propertyUtilMockedStatic.when(DatasourceConfiguration::isEmbeddedStorage).thenReturn(false);
        envUtilMockedStatic.when(EnvUtil::getStandaloneMode).thenReturn(true);
        assertFalse(conditionDistributedEmbedStorage.matches(context, metadata));
        
        propertyUtilMockedStatic.when(DatasourceConfiguration::isEmbeddedStorage).thenReturn(false);
        envUtilMockedStatic.when(EnvUtil::getStandaloneMode).thenReturn(false);
        assertFalse(conditionDistributedEmbedStorage.matches(context, metadata));
        
        propertyUtilMockedStatic.close();
        envUtilMockedStatic.close();
    }
    
}
