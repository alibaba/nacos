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

package com.alibaba.nacos.config.server.model.gray;

import java.util.Map;

/**
 * gray rule.
 *
 * @author rong
 */
public interface GrayRule {
    
    /**
    * gray rule match labels or not.
    *
    * @date 2024/3/14
    * @param labels conn labels.
    * @return true if match, false otherwise.
    */
    boolean match(Map<String, String> labels);
    
    /**
    * if the gray rule is valid.
    *
    * @date 2024/3/14
    * @return true if valid, false otherwise.
    */
    boolean isValid();
    
    /**
    * get gray rule type.
    *
    * @date 2024/3/14
    * @return the gray rule type.
    */
    String getType();
    
    /**
    * get gray rule version.
    *
    * @date 2024/3/14
    * @return the gray rule version.
    */
    String getVersion();
    
    /**
    * get gray rule priority.
    *
    * @date 2024/3/14
    * @return the gray rule priority.
    */
    int getPriority();
    
    /**
    * get raw String of gray rule.
    *
    * @date 2024/3/14
    * @return the raw String of gray rule.
    */
    String getRawGrayRuleExp();
}
