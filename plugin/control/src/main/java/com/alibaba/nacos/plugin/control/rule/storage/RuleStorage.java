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

package com.alibaba.nacos.plugin.control.rule.storage;

/**
 * rule storage.
 *
 * @author shiyiyue
 * @date 2022-10-26 11:43:00
 */
public interface RuleStorage {
    
    /**
     * get storage name.
     *
     * @return
     */
    String getName();
    
    /**
     * save connection rule to storage.
     *
     * @param ruleContent rule content.
     * @throws Exception exception.
     */
    void saveConnectionRule(String ruleContent) throws Exception;
    
    /**
     * get connection rule.
     *
     * @return
     */
    String getConnectionRule();
    
    /**
     * save tps rule.
     *
     * @param pointName   point name.
     * @param ruleContent rule content.
     * @throws Exception exception.
     */
    void saveTpsRule(String pointName, String ruleContent) throws Exception;
    
    /**
     * get tps rule.
     *
     * @param pointName point name.
     * @return
     */
    String getTpsRule(String pointName);
    
}
