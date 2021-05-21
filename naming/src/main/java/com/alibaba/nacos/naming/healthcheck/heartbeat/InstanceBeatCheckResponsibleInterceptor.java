/*
 * Copyright (c) 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.healthcheck.heartbeat;

import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

/**
 * Instance responsibility check interceptor.
 *
 * @author gengtuo.ygt
 * on 2021/3/24
 */
public class InstanceBeatCheckResponsibleInterceptor extends AbstractBeatCheckInterceptor {

    @Override
    public boolean intercept(InstanceBeatCheckTask object) {
        return !ApplicationUtils.getBean(DistroMapper.class).responsible(object.getClient().getResponsibleId());
    }

    @Override
    public int order() {
        return Integer.MIN_VALUE + 2;
    }

}
