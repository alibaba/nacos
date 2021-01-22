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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;

/**
 * MatchMode.
 *
 * @author liuzunfei
 * @version $Id: MatchMode.java, v 0.1 2021年01月22日 12:38 PM liuzunfei Exp $
 */
public class MiddleFuzzyMonitorKeyMatcher extends MonitorKeyMatcher {
    
    private String prefix;
    
    private String postfix;
    
    private static final int PATTERN_LENGTH = 2;
    
    public MiddleFuzzyMonitorKeyMatcher(String pattern) {
        super.pattern = pattern;
        String[] split = pattern.split("\\" + Constants.ALL_PATTERN);
        if (split.length != PATTERN_LENGTH) {
            throw new NacosRuntimeException(NacosException.INVALID_PARAM,
                    "Invalid pattern of post &pre fix monitor key matcher.");
        }
        this.prefix = split[0];
        this.postfix = split[1];
        
    }
    
    @Override
    public boolean match(String monitorKey) {
        return monitorKey.endsWith(postfix) && monitorKey.startsWith(prefix);
    }
    
    @Override
    public String getMatchModel() {
        return MatchMode.MIDDLE_FUZZY.model;
    }
    
}
