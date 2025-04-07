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

package com.alibaba.nacos.config.server.model;

import com.alibaba.nacos.config.server.model.gray.GrayRule;
import com.alibaba.nacos.config.server.model.gray.GrayRuleManager;

import java.io.Serializable;
import java.util.Map;

/**
 * extensible config cache.
 *
 * @author rong
 */
public class ConfigCacheGray extends ConfigCache implements Serializable {
    
    private String grayName;
    
    private GrayRule grayRule;
    
    /**
     * clear cache.
     */
    @Override
    public void clear() {
        super.clear();
    }
    
    public ConfigCacheGray() {}
    
    public ConfigCacheGray(String grayName) {
        this.grayName = grayName;
    }
    
    public GrayRule getGrayRule() {
        return grayRule;
    }
    
    public String getGrayName() {
        return grayName;
    }
    
    public void setGrayName(String grayName) {
        this.grayName = grayName;
    }
    
    /**
     * get raw gray rule from db.
     *
     * @return raw gray rule from db.
     * @date 2024/3/14
     */
    public String getRawGrayRule() {
        return grayRule.getRawGrayRuleExp();
    }
    
    /**
     * reset gray rule.
     *
     * @param grayRule raw gray rule from db.
     * @throws RuntimeException if gray rule is invalid.
     * @date 2024/3/14
     */
    public void resetGrayRule(String grayRule) throws RuntimeException {
        this.grayRule = GrayRuleManager.constructGrayRule(GrayRuleManager.deserializeConfigGrayPersistInfo(grayRule));
        if (this.grayRule == null || !this.grayRule.isValid()) {
            throw new RuntimeException("raw gray rule is invalid");
        }
    }
    
    /**
     * judge whether match gray rule.
     *
     * @param tags conn tags.
     * @return true if match, false otherwise.
     * @date 2024/3/14
     */
    public boolean match(Map<String, String> tags) {
        return grayRule.match(tags);
    }
    
    public int getPriority() {
        return grayRule.getPriority();
    }
    
    /**
     * if gray rule is valid.
     *
     * @return true if valid, false otherwise.
     * @date 2024/3/14
     */
    public boolean isValid() {
        return grayRule != null && grayRule.isValid();
    }
}