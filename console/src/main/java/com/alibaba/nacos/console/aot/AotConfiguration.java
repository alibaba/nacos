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

import org.rocksdb.NativeLibraryLoader;

import java.lang.reflect.Field;

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

}
