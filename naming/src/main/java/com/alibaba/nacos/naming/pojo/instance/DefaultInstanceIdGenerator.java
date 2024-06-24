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

package com.alibaba.nacos.naming.pojo.instance;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.spi.generator.InstanceIdGenerator;

import static com.alibaba.nacos.api.common.Constants.DEFAULT_INSTANCE_ID_GENERATOR;
import static com.alibaba.nacos.api.common.Constants.NAMING_INSTANCE_ID_SPLITTER;

/**
 * Default instance id generator.
 *
 * @author xiweng.yy
 */
public class DefaultInstanceIdGenerator implements InstanceIdGenerator {
    
    @Override
    public String generateInstanceId(Instance instance) {
        return instance.getIp() + NAMING_INSTANCE_ID_SPLITTER
                + instance.getPort() + NAMING_INSTANCE_ID_SPLITTER
                + instance.getClusterName() + NAMING_INSTANCE_ID_SPLITTER
                + instance.getServiceName();
    }
    
    @Override
    public String type() {
        return DEFAULT_INSTANCE_ID_GENERATOR;
    }
}
