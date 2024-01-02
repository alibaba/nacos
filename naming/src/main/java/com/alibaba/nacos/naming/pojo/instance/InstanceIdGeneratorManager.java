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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.spi.generator.InstanceIdGenerator;
import com.alibaba.nacos.api.utils.StringUtils;
import com.alibaba.nacos.common.spi.NacosServiceLoader;

import java.util.Collection;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * InstanceIdGeneratorManager.
 *
 * @author : huangtianhui
 */
public class InstanceIdGeneratorManager {
    
    private static final InstanceIdGeneratorManager INSTANCE = new InstanceIdGeneratorManager();
    
    private final Map<String, InstanceIdGenerator> generatorMap = new ConcurrentHashMap<>();
    
    public InstanceIdGeneratorManager() {
        init();
    }
    
    private void init() {
        Collection<InstanceIdGenerator> instanceIdGenerators = NacosServiceLoader.load(InstanceIdGenerator.class);
        for (InstanceIdGenerator instanceIdGenerator : instanceIdGenerators) {
            generatorMap.put(instanceIdGenerator.type(), instanceIdGenerator);
        }
    }
    
    private InstanceIdGenerator getInstanceIdGenerator(String type) {
        if (generatorMap.containsKey(type)) {
            return generatorMap.get(type);
        }
        throw new NoSuchElementException("The InstanceIdGenerator type is not found ");
    }
    
    /**
     * spi generateInstanceId.
     *
     * @param instance instance
     * @return InstanceId
     */
    public static String generateInstanceId(Instance instance) {
        String instanceIdGeneratorType = instance.getInstanceIdGenerator();
        if (StringUtils.isBlank(instanceIdGeneratorType)) {
            instanceIdGeneratorType = Constants.DEFAULT_INSTANCE_ID_GENERATOR;
        }
        return INSTANCE.getInstanceIdGenerator(instanceIdGeneratorType).generateInstanceId(instance);
    }
    
}
