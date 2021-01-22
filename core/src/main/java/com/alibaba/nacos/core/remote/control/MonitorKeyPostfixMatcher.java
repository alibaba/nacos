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
 * MonitorKeyPostfixMatcher.
 *
 * @author liuzunfei
 * @version $Id: MonitorKeyPostfixMatcher.java, v 0.1 2021年01月22日 12:38 PM liuzunfei Exp $
 */
public class MonitorKeyPostfixMatcher extends MonitorKeyMatcher {
    
    public MonitorKeyPostfixMatcher(String pattern) {
        super.pattern = pattern;
        
    }
    
    @Override
    public boolean match(String monitorKey) {
        return monitorKey.endsWith(pattern);
    }
    
    @Override
    public String getMatchModel() {
        return MatchMode.POSTFIX.model;
    }
    
}
