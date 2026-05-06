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

package com.alibaba.nacos.persistence.repository.embedded.hook;

import java.util.HashSet;
import java.util.Set;

/**
 * Holder for Embedded apply hook.
 *
 * @author xiweng.yy
 */
public class EmbeddedApplyHookHolder {
    
    private static final EmbeddedApplyHookHolder INSTANCE = new EmbeddedApplyHookHolder();
    
    private final Set<EmbeddedApplyHook> hooks;
    
    private EmbeddedApplyHookHolder() {
        hooks = new HashSet<>();
    }
    
    public static EmbeddedApplyHookHolder getInstance() {
        return INSTANCE;
    }
    
    public void register(EmbeddedApplyHook hook) {
        this.hooks.add(hook);
    }
    
    public Set<EmbeddedApplyHook> getAllHooks() {
        return this.hooks;
    }
}
