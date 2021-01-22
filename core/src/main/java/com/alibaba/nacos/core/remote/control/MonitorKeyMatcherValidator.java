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
import com.alibaba.nacos.common.utils.StringUtils;

/**
 * MonitorKeyMatcherValidator.
 *
 * @author liuzunfei
 * @version $Id: MonitorKeyMatcherValidator.java, v 0.1 2021年01月22日 12:38 PM liuzunfei Exp $
 */
public class MonitorKeyMatcherValidator {
    
    /**
     * parse key matcher.
     * @param rule rule content
     * @return
     */
    public static MonitorKeyMatcher parse(String rule) {
        if (StringUtils.isBlank(rule)) {
            return null;
        }
        
        if (rule.startsWith(MatchMode.EQUAL.model + Constants.POUND)) {
            return new MonitorKeyEqualMatcher(rule.split(Constants.POUND)[1]);
        }
        
        if (rule.startsWith(MatchMode.PREFIX.model + Constants.POUND)) {
            return new MonitorKeyPrefixMatcher(rule.split(Constants.POUND)[1]);
        }
        
        if (rule.startsWith(MatchMode.POSTFIX.model + Constants.POUND)) {
            return new MonitorKeyPostfixMatcher(rule.split(Constants.POUND)[1]);
        }
        
        if (rule.startsWith(MatchMode.MIDDLE_FUZZY.model + Constants.POUND)) {
            return new MiddleFuzzyMonitorKeyMatcher(rule.split(Constants.POUND)[1]);
        }
        return null;
    }
}
