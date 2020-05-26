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

import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Http;
import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Mysql;
import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Tcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author nkorange
 */
public enum HealthCheckType {
    /**
     * TCP type
     */
    TCP(Tcp.class),
    /**
     * HTTP type
     */
    HTTP(Http.class),
    /**
     * MySQL type
     */
    MYSQL(Mysql.class),
    /**
     * No check
     */
    NONE(AbstractHealthChecker.None.class);

    private final Class<? extends AbstractHealthChecker> healthCheckerClass;

    private static final Map<String, Class<? extends AbstractHealthChecker>> EXTEND = new ConcurrentHashMap<String, Class<? extends AbstractHealthChecker>>();

    HealthCheckType(Class<? extends AbstractHealthChecker> healthCheckerClass) {
        this.healthCheckerClass = healthCheckerClass;
    }

    public static void registerHealthChecker(String type, Class<? extends AbstractHealthChecker> healthCheckerClass){
        if (!EXTEND.containsKey(type)) {
            EXTEND.put(type, healthCheckerClass);
            HealthCheckerFactory.registerSubType(healthCheckerClass, type);
        }
    }

    public static Class<? extends AbstractHealthChecker> ofHealthCheckerClass(String type){
        HealthCheckType enumType;
        try {
            enumType = valueOf(type);
        }catch (Exception e){
            return EXTEND.get(type);
        }
        return enumType.healthCheckerClass;
    }

    public static List<Class<? extends AbstractHealthChecker>> getLoadedHealthCheckerClasses(){
        List<Class<? extends AbstractHealthChecker>> all = new ArrayList<Class<? extends AbstractHealthChecker>>();
        for(HealthCheckType type : values()){
            all.add(type.healthCheckerClass);
        }
        for(Map.Entry<String, Class<? extends AbstractHealthChecker>> entry : EXTEND.entrySet()){
            all.add(entry.getValue());
        }
        return all;
    }
}
