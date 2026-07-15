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

package com.alibaba.nacos.api.selector;

import com.alibaba.nacos.api.utils.json.JsonUtils;

/**
 * Selector subtype registry for neutral JSON serialization.
 *
 * @author nacos
 */
public final class SelectorFactory {
    
    static {
        registerBuiltInSubTypes();
    }
    
    private SelectorFactory() {
    }
    
    /**
     * Preload built-in selector subtype registrations.
     */
    public static void preload() {
        registerBuiltInSubTypes();
    }
    
    /**
     * Register new sub type of selector for serialize and deserialize.
     *
     * @param selectorClass selector implementation class
     * @param typeName      selector wire type name
     */
    public static void registerSubType(Class<? extends Selector<?, ?, ?>> selectorClass,
        String typeName) {
        JsonUtils.registerSubtype(Selector.class, selectorClass, typeName);
        if (AbstractSelector.class.isAssignableFrom(selectorClass)) {
            JsonUtils.registerSubtype(AbstractSelector.class, selectorClass, typeName);
        }
    }
    
    private static void registerBuiltInSubTypes() {
        registerSubType(NoneSelector.class, SelectorType.none.name());
        registerSubType(NoneSelector.class, NoneSelector.class.getSimpleName());
        registerSubType(ExpressionSelector.class, SelectorType.label.name());
        registerSubType(ExpressionSelector.class, "LabelSelector");
        registerSubType(ExpressionSelector.class, ExpressionSelector.class.getSimpleName());
    }
}
