/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.push.v2.hook;

import com.alibaba.nacos.common.spi.NacosServiceLoader;

import java.util.Collection;

/**
 * Push result hook holder for SPI.
 *
 * @author xiweng.yy
 */
public class PushResultHookHolder implements PushResultHook {
    
    private static final PushResultHookHolder INSTANCE = new PushResultHookHolder();
    
    private final Collection<PushResultHook> hooks;
    
    private PushResultHookHolder() {
        hooks = NacosServiceLoader.load(PushResultHook.class);
    }
    
    public static PushResultHookHolder getInstance() {
        return INSTANCE;
    }
    
    @Override
    public void pushSuccess(PushResult result) {
        hooks.forEach(each -> each.pushSuccess(result));
    }
    
    @Override
    public void pushFailed(PushResult result) {
        hooks.forEach(each -> each.pushFailed(result));
    }
}
