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

package com.alibaba.nacos.common.utils;

/**
 * Http method constants.
 *
 * @author nkorange
 * @since 0.8.0
 */
public class HttpMethod {
    
    public static final String GET = "GET";
    
    /**
     * this is only use in nacos, Custom request type, essentially a GET request, Mainly used for GET request parameters
     * are relatively large,can not be placed on the URL, so it needs to be placed in the body.
     */
    public static final String GET_LARGE = "GET-LARGE";
    
    public static final String HEAD = "HEAD";
    
    public static final String POST = "POST";
    
    public static final String PUT = "PUT";
    
    public static final String PATCH = "PATCH";
    
    public static final String DELETE = "DELETE";
    
    /**
     * this is only use in nacos, Custom request type, essentially a DELETE request, Mainly used for DELETE request
     * parameters are relatively large, can not be placed on the URL, so it needs to be placed in the body.
     */
    public static final String DELETE_LARGE = "DELETE_LARGE";
    
    public static final String OPTIONS = "OPTIONS";
    
    public static final String TRACE = "TRACE";
}
