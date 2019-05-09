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
package com.alibaba.nacos.naming.healthcheck;

import com.alibaba.nacos.api.naming.pojo.AbstractHealthChecker;

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
    TCP("tcp", AbstractHealthChecker.Tcp.class),
    /**
     * HTTP type
     */
    HTTP("http", AbstractHealthChecker.Http.class),
    /**
     * MySQL type
     */
    MYSQL("mysql", AbstractHealthChecker.Mysql.class),
    /**
     * No check
     */
    NONE("none", AbstractHealthChecker.None.class);

    private String name;

    private Class healthCheckerClass;

    private static Map<String, Class> EXTEND =
        new ConcurrentHashMap<>();

    HealthCheckType(String name, Class healthCheckerClass) {
        this.name = name;
        this.healthCheckerClass = healthCheckerClass;
    }

    public static void registerHealthChecker(String type, Class healthCheckerClass){
        EXTEND.putIfAbsent(type, healthCheckerClass);
    }

    public static Class ofHealthCheckerClass(String type){
        HealthCheckType enumType;
        try {
            enumType = valueOf(type);
        }catch (Exception e){
            return EXTEND.get(type);
        }
        return enumType.healthCheckerClass;
    }

    public static List<Class> getLoadedHealthCheckerClasses(){
        List<Class> all = new ArrayList<>();
        for(HealthCheckType type : values()){
            all.add(type.healthCheckerClass);
        }
        for(Map.Entry<String, Class> entry : EXTEND.entrySet()){
            all.add(entry.getValue());
        }
        return all;
    }
}
