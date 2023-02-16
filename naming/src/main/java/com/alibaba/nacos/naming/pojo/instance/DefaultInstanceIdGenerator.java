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

import com.alibaba.nacos.api.naming.spi.generator.IdGenerator;

/**
 * Default instance id generator.
 *
 * @author xiweng.yy
 */
public class DefaultInstanceIdGenerator implements IdGenerator {
    
    public static final String ID_DELIMITER = "#";
    
    private final String serviceName;
    
    private final String clusterName;
    
    private final String ip;
    
    private final int port;
    
    public DefaultInstanceIdGenerator(String serviceName, String clusterName, String ip, int port) {
        this.serviceName = serviceName;
        this.clusterName = clusterName;
        this.ip = ip;
        this.port = port;
    }
    
    @Override
    public String generateInstanceId() {
        return ip + ID_DELIMITER + port + ID_DELIMITER + clusterName + ID_DELIMITER + serviceName;
    }
}
