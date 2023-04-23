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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class ConditionOnExternalStorageTest {
    
    private ConditionOnExternalStorage conditionOnExternalStorage;
    
    @Mock
    ConditionContext context;
    
    @Mock
    AnnotatedTypeMetadata metadata;
    
    @Before
    public void init() {
        conditionOnExternalStorage = new ConditionOnExternalStorage();
    }
    
    @Test
    public void testMatches() {
        MockedStatic<DatasourceConfiguration> mockedStatic = Mockito.mockStatic(DatasourceConfiguration.class);
        
        mockedStatic.when(DatasourceConfiguration::isEmbeddedStorage).thenReturn(true);
        Assert.assertFalse(conditionOnExternalStorage.matches(context, metadata));
        
        mockedStatic.when(DatasourceConfiguration::isEmbeddedStorage).thenReturn(false);
        Assert.assertTrue(conditionOnExternalStorage.matches(context, metadata));
        
        mockedStatic.close();
    }
    
}
