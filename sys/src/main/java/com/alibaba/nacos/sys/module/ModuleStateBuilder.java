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

package com.alibaba.nacos.sys.module;

/**
 * Module state builder.
 *
 * @author xiweng.yy
 */
public interface ModuleStateBuilder {
    
    /**
     * Build module state.
     *
     * @return ModuleState
     */
    ModuleState build();
    
    /**
     * Whether module is ignored, default return false.
     *
     * @return boolean
     */
    default boolean isIgnore() {
        return false;
    }
    
    /**
     * Whether module is cache, default return true.
     *
     * @return boolean
     */
    default boolean isCacheable() {
        return true;
    }
    
}
