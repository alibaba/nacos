/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.configuration;

import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * ConditionOnLdapAuth test.
 * @ClassName: ConditionOnLdapAuthTest
 * @Author: ChenHao26
 * @Date: 2022/8/16 17:03
 */
@RunWith(MockitoJUnitRunner.class)
public class ConditionOnLdapAuthTest {
    
    private ConditionOnLdapAuth conditionOnLdapAuth;
    
    @Mock
    private ConditionContext conditionContext;
    
    @Mock
    private AnnotatedTypeMetadata annotatedTypeMetadata;
    
    @Mock
    private static ConfigurableEnvironment environment;
    
    @Before
    public void setup() {
        conditionOnLdapAuth = new ConditionOnLdapAuth();
        EnvUtil.setEnvironment(environment);
    }
    
    @Test
    public void matches() {
        boolean matches = conditionOnLdapAuth.matches(conditionContext, annotatedTypeMetadata);
        Assert.assertFalse(matches);
    }
}
