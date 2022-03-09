/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.config.server.remote;

import com.alibaba.nacos.common.remote.PayloadPackageProvider;

import java.util.HashSet;
import java.util.Set;

/**
 * config package provider.
 *
 * @author hujun
 */
public class ConfigPayloadPackageProvider implements PayloadPackageProvider {
    
    private final Set<String> scanPackage = new HashSet<>();
    
    {
        scanPackage.add("com.alibaba.nacos.api.remote.request");
        scanPackage.add("com.alibaba.nacos.api.remote.response");
        scanPackage.add("com.alibaba.nacos.api.config.remote.request");
        scanPackage.add("com.alibaba.nacos.api.config.remote.response");
    }
    
    @Override
    public Set<String> getScanPackage() {
        return scanPackage;
    }
}
