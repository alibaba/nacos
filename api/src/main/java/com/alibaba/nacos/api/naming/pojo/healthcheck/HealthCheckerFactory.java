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

package com.alibaba.nacos.api.naming.pojo.healthcheck;

import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker.None;
import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Http;
import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Mysql;
import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Tcp;
import com.alibaba.nacos.api.utils.json.JsonUtils;

/**
 * health checker factory.
 *
 * @author yangyi
 */
public class HealthCheckerFactory {
    
    static {
        registerSubType(Http.class, Http.TYPE);
        registerSubType(Mysql.class, Mysql.TYPE);
        registerSubType(Tcp.class, Tcp.TYPE);
        registerSubType(None.class, None.TYPE);
    }
    
    /**
     * Register new sub type of health checker to factory for serialize and deserialize.
     *
     * @param extendHealthChecker extend health checker
     */
    public static void registerSubType(AbstractHealthChecker extendHealthChecker) {
        registerSubType(extendHealthChecker.getClass(), extendHealthChecker.getType());
    }
    
    /**
     * Register new sub type of health checker to factory for serialize and deserialize.
     *
     * @param extendHealthCheckerClass extend health checker
     * @param typeName                 typeName of health checker
     */
    public static void registerSubType(
        Class<? extends AbstractHealthChecker> extendHealthCheckerClass,
        String typeName) {
        JsonUtils.registerSubtype(AbstractHealthChecker.class, extendHealthCheckerClass, typeName);
    }
    
    /**
     * Deserialize and create an instance of health checker.
     *
     * @param jsonString json string of health checker
     * @return new instance
     */
    public static AbstractHealthChecker deserialize(String jsonString) {
        return JsonUtils.toObj(jsonString, AbstractHealthChecker.class);
    }
    
    /**
     * Serialize an instance of health checker to json.
     *
     * @param healthChecker health checker instance
     * @return json string after serializing
     */
    public static String serialize(AbstractHealthChecker healthChecker) {
        return JsonUtils.toJson(healthChecker);
    }
}
