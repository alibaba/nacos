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

package com.alibaba.nacos.naming.pojo.instance;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.spi.generator.InstanceIdGenerator;
import com.alibaba.nacos.core.distributed.id.SnowFlowerIdGenerator;

import static com.alibaba.nacos.api.common.Constants.NAMING_INSTANCE_ID_SPLITTER;
import static com.alibaba.nacos.api.common.Constants.SNOWFLAKE_INSTANCE_ID_GENERATOR;

/**
 * SnowFlake InstanceId Generator..
 *
 * @author : huangtianhui
 */
public class SnowFlakeInstanceIdGenerator implements InstanceIdGenerator {
    
    private static final SnowFlowerIdGenerator SNOW_FLOWER_ID_GENERATOR = new SnowFlowerIdGenerator();
    
    static {
        SNOW_FLOWER_ID_GENERATOR.init();
    }
    
    @Override
    public String generateInstanceId(Instance instance) {
        return SNOW_FLOWER_ID_GENERATOR.nextId() + NAMING_INSTANCE_ID_SPLITTER
                + instance.getClusterName() + NAMING_INSTANCE_ID_SPLITTER
                + instance.getServiceName();
    }
    
    @Override
    public String type() {
        return SNOWFLAKE_INSTANCE_ID_GENERATOR;
    }
}
