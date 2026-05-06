/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.condition;

import com.alibaba.nacos.sys.env.Constants;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * When nacos deployment type is `merged` or `server`.
 *
 * @author xiweng.yy
 */
public class ConditionOnInnerDatasource implements Condition {
    
    @Override
    public boolean matches(ConditionContext conditionContext, AnnotatedTypeMetadata annotatedTypeMetadata) {
        return !Constants.NACOS_DEPLOYMENT_TYPE_CONSOLE.equalsIgnoreCase(
                conditionContext.getEnvironment().getProperty(Constants.NACOS_DEPLOYMENT_TYPE));
    }
}
