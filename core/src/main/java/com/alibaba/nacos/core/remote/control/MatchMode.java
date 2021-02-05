/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.remote.control;

/**
 * MatchMode.
 *
 * @author liuzunfei
 * @version $Id: MatchMode.java, v 0.1 2021年01月22日 12:38 PM liuzunfei Exp $
 */
public enum MatchMode {
    
    /**
     * equal match .
     */
    EQUAL("equal", "complete equal."),
    
    /**
     * prefix match. nacosConfig matches "prefix#nacos"
     */
    PREFIX("prefix", "prefix match."),
    
    /**
     * postfix match.nacosConfig matches "postfix#Config"
     */
    POSTFIX("postfix", "postfix match."),
    
    /**
     * middle fuzzy. nacosTestConfig matches "middlefuzzy#nacos*Config"
     */
    MIDDLE_FUZZY("middlefuzzy", "middle fuzzy, both match prefix and postfix.");
    
    String model;
    
    String desc;
    
    MatchMode(String model, String desc) {
        this.model = model;
        this.desc = desc;
    }
}
