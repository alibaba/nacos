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

package com.alibaba.nacos.client.utils;

import com.alibaba.nacos.common.utils.StringUtils;

import java.util.concurrent.Callable;

/**
 * Template Utils.
 *
 * @author pbting
 * @date 2019-03-04 1:31 PM
 */
public class TemplateUtils {
    
    /**
     * Execute if string not empty.
     *
     * @param source   source
     * @param runnable execute runnable
     */
    public static void stringNotEmptyAndThenExecute(String source, Runnable runnable) {
        
        if (StringUtils.isNotEmpty(source)) {
            
            try {
                runnable.run();
            } catch (Exception e) {
                LogUtils.NAMING_LOGGER.error("string empty and then execute cause an exception.", e);
            }
        }
    }
    
    /**
     * Execute if string empty.
     *
     * @param source   empty source
     * @param callable execute callable
     * @return result
     */
    public static String stringEmptyAndThenExecute(String source, Callable<String> callable) {
        
        if (StringUtils.isEmpty(source)) {
            
            try {
                return callable.call();
            } catch (Exception e) {
                LogUtils.NAMING_LOGGER.error("string empty and then execute cause an exception.", e);
            }
        }
        
        return source == null ? null : source.trim();
    }
    
    /**
     * Execute if string blank.
     *
     * @param source   empty source
     * @param callable execute callable
     * @return result
     */
    public static String stringBlankAndThenExecute(String source, Callable<String> callable) {
        
        if (StringUtils.isBlank(source)) {
            
            try {
                return callable.call();
            } catch (Exception e) {
                LogUtils.NAMING_LOGGER.error("string empty and then execute cause an exception.", e);
            }
        }
        
        return source == null ? null : source.trim();
    }
}
