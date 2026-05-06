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

package com.alibaba.nacos.client.auth.ram;

/**
 * Ram auth plugin constants.
 *
 * @author xiweng.yy
 */
public class RamConstants {
    
    public static final String SIGNATURE_VERSION = "signatureVersion";
    
    public static final String V4 = "v4";
    
    public static final String SIGNATURE_V4_METHOD = "HmacSHA256";
    
    public static final String SIGNATURE_V4_PRODUCE = "mse-nacos";
    
}
