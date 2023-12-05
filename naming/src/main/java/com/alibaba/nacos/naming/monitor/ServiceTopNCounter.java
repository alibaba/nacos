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

package com.alibaba.nacos.naming.monitor;

import com.alibaba.nacos.core.monitor.topn.BaseTopNCounter;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;

/**
 * Service topN counter.
 *
 * @author xiweng.yy
 */
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
public class ServiceTopNCounter extends BaseTopNCounter<Service> {
    
    public ServiceTopNCounter() {
        super();
    }
    
    @Override
    protected String keyToString(Service service) {
        return service.getNamespace() + UtilsAndCommons.NAMESPACE_SERVICE_CONNECTOR + service.getGroupedServiceName();
    }
}
