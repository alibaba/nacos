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

package com.alibaba.nacos.config.server.utils;

/**
 * Stat constant.
 *
 * @author Nacos
 */
public class StatConstants {
    
    public static final String APP_NAME = "nacos";
    
    public static final String STAT_AVERAGE_HTTP_GET_OK = "AverageHttpGet_OK";
    
    public static final String STAT_AVERAGE_HTTP_GET_NOT_MODIFIED = "AverageHttpGet_Not_Modified";
    
    public static final String STAT_AVERAGE_HTTP_GET_OTHER = "AverageHttpGet_Other_Status";
    
    public static final String STAT_AVERAGE_HTTP_POST_CHECK = "AverageHttpPost_Check";
    
    private StatConstants() {
    }
    
}
