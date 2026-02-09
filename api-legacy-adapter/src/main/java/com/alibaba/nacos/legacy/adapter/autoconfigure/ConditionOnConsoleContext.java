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

package com.alibaba.nacos.legacy.adapter.autoconfigure;

import com.alibaba.nacos.console.NacosConsole;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

/**
 * Condition that matches when the current Spring context was started with
 * {@link NacosConsole} as the primary application class (e.g. console-only or
 * console child context in merged mode). Uses the registry to detect if any
 * bean definition has NacosConsole as its source or bean class.
 *
 * @author xiweng.yy
 */
public class ConditionOnConsoleContext implements Condition {

    private static final String CONSOLE_MAIN_CLASS = NacosConsole.class.getName();

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        BeanDefinitionRegistry registry = context.getRegistry();
        for (String name : registry.getBeanDefinitionNames()) {
            BeanDefinition bd = registry.getBeanDefinition(name);
            Object source = bd.getSource();
            if (source instanceof Class && CONSOLE_MAIN_CLASS.equals(((Class<?>) source).getName())) {
                return true;
            }
            if (CONSOLE_MAIN_CLASS.equals(bd.getBeanClassName())) {
                return true;
            }
        }
        return false;
    }
}
