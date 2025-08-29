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

package com.alibaba.nacos.console.handler.impl;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * The condition of target function or module is enabled.
 * When target module such as `naming`, `config` or `ai` is disabled or dependency module is disabled
 * The target handler should not be loaded and should use noop handler replaced.
 *
 * @author xiweng.yy
 */
public class ConditionFunctionEnabled implements Condition {
    
    private final String targetFunctionMode;
    
    public ConditionFunctionEnabled(String targetFunctionMode) {
        this.targetFunctionMode = targetFunctionMode;
    }
    
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String functionMode = EnvUtil.getFunctionMode();
        // empty function mode setting means all function is enabled
        if (StringUtils.isEmpty(functionMode)) {
            return true;
        }
        // configured function mode not empty and equals target function mode, means target function is enabled
        return functionMode.equalsIgnoreCase(targetFunctionMode);
    }
    
    public static class ConditionNamingEnabled extends ConditionFunctionEnabled {
        
        public ConditionNamingEnabled() {
            super(EnvUtil.FUNCTION_MODE_NAMING);
        }
    }
    
    public static class ConditionConfigEnabled extends ConditionFunctionEnabled {
        
        public ConditionConfigEnabled() {
            super(EnvUtil.FUNCTION_MODE_CONFIG);
        }
    }
    
    public static class ConditionAiEnabled extends ConditionFunctionEnabled {
        
        public ConditionAiEnabled() {
            super("");
        }
    }
}
