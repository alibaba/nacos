/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.aot;

import org.apache.derby.iapi.services.loader.InstanceGetter;
import org.apache.derby.iapi.services.monitor.Monitor;
import org.rocksdb.NativeLibraryLoader;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/**
 * Help graalvm and spring-aot find specific native file from jar like rocksdb.
 *
 * @author Dioxide.CN
 * @date 2024/8/16
 * @since 2.4.0
 */
public class AotConfiguration {

    /**
     * To help find rocksdb inner fields' value.
     */
    public static String reflectToNativeLibraryLoader() {
        Class<NativeLibraryLoader> clazz = NativeLibraryLoader.class;
        try {
            Field jniLibraryFileNameField = clazz.getDeclaredField("jniLibraryFileName");
            jniLibraryFileNameField.setAccessible(true);
            Field fallbackJniLibraryFileNameField = clazz.getDeclaredField("fallbackJniLibraryFileName");
            fallbackJniLibraryFileNameField.setAccessible(true);
            return (String) jniLibraryFileNameField.get(null);
        } catch (NoSuchFieldException
                 | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * To help find derby error.
     */
    public static void findMonitor() {
        Class<Monitor> monitorClass = Monitor.class;
        try {
            Field monitorField = monitorClass.getDeclaredField("monitor");
            monitorField.setAccessible(true);
            Object monitorValue = monitorField.get(null);
            Class<?> baseMonitorClass = Class.forName("org.apache.derby.impl.services.monitor.BaseMonitor");
            if (baseMonitorClass.isInstance(monitorValue)) {
                System.out.println("monitor value is BaseMonitor.");
                Object baseMonitorInstance = baseMonitorClass.cast(monitorValue);
                Field rc2Field = baseMonitorClass.getDeclaredField("rc2");
                rc2Field.setAccessible(true);
                Object instanceRc2Value = rc2Field.get(baseMonitorInstance);
                if (instanceRc2Value instanceof InstanceGetter[]) {
                    InstanceGetter[] rc2 = (InstanceGetter[]) instanceRc2Value;
                    System.out.println("rc2 length: " + rc2.length);
                    Set<String> instanceGetterNameSet = new HashSet<>(32);
                    for (InstanceGetter instanceGetter : rc2) {
                        if (instanceGetter != null) {
                            instanceGetterNameSet.add(instanceGetter.getClass().getName() + ".class");
                        }
                    }
                    instanceGetterNameSet.forEach(System.out::println);
                }
            } else {
                System.out.println("monitor value is not BaseMonitor.");
            }
        } catch (NoSuchFieldException
                 | IllegalAccessException
                 | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

}
