/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.auth.common;

public enum IdentifyPositionTypes {
    /**
     * Identify context in http request header.
     */
    HEADER("HEADER"),
    /**
     * Identify context in http request parameter.
     */
    PARAMETER("PARAMETER"),
    /**
     * Identify context in http request header and parameter..
     */
    HEADER_AND_PARAMETER("HEADER_AND_PARAMETER");
    
    private  String position;
    
    IdentifyPositionTypes(String position) {
        this.position = position;
    }
    
    @Override
    public String toString() {
        return position;
    }
    
}
