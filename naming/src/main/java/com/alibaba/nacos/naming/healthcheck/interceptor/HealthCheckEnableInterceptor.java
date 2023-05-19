/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.healthcheck.interceptor;

import com.alibaba.nacos.naming.healthcheck.NacosHealthCheckTask;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

/**
 * Health check enable interceptor.
 *
 * @author xiweng.yy
 */
public class HealthCheckEnableInterceptor extends AbstractHealthCheckInterceptor {
    
    @Override
    public boolean intercept(NacosHealthCheckTask object) {
        try {
            return !ApplicationUtils.getBean(SwitchDomain.class).isHealthCheckEnabled();
        } catch (Exception e) {
            return true;
        }
    }
    
    @Override
    public int order() {
        return Integer.MIN_VALUE;
    }
}
