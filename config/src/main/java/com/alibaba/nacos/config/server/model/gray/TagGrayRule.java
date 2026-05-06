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

package com.alibaba.nacos.config.server.model.gray;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.utils.StringUtils;

import java.util.Map;
import java.util.Objects;

import static com.alibaba.nacos.api.common.Constants.VIPSERVER_TAG;

/**
 * Tag gray rule.
 *
 * @author shiyiyue
 */
public class TagGrayRule extends AbstractGrayRule {
    
    String tagValue;
    
    public static final String VIP_SERVER_TAG_LABEL = VIPSERVER_TAG;
    
    public static final String TYPE_TAG = "tag";
    
    public static final String VERSION = "1.0.0";
    
    public static final int PRIORITY = Integer.MAX_VALUE - 1;
    
    public TagGrayRule() {
        super();
    }
    
    public TagGrayRule(String rawGrayRuleExp, int priority) {
        super(rawGrayRuleExp, priority);
    }
    
    @Override
    protected void parse(String rawGrayRule) throws NacosException {
        if (StringUtils.isBlank(rawGrayRule)) {
            return;
        }
        this.tagValue = rawGrayRule;
    }
    
    @Override
    public boolean match(Map<String, String> labels) {
        return labels.containsKey(VIP_SERVER_TAG_LABEL) && tagValue.equals(labels.get(VIP_SERVER_TAG_LABEL));
    }
    
    @Override
    public String getType() {
        return TYPE_TAG;
    }
    
    @Override
    public String getVersion() {
        return VERSION;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TagGrayRule that = (TagGrayRule) o;
        return tagValue.equals(that.tagValue);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(tagValue);
    }
}
