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

package com.alibaba.nacos.client.auth.ram.injector;

import com.alibaba.nacos.plugin.auth.api.LoginIdentityContext;
import com.alibaba.nacos.client.auth.ram.RamContext;
import com.alibaba.nacos.plugin.auth.api.RequestResource;

/**
 * Abstract aliyun RAM resource injector.
 *
 * @author xiweng.yy
 */
public abstract class AbstractResourceInjector {
    
    /**
     * Generate and inject resource into context. Default impl will do nothing.
     *
     * @param resource request resource
     * @param context  ram context
     * @param result   the result identity context
     */
    public void doInject(RequestResource resource, RamContext context, LoginIdentityContext result) {
    }
}
