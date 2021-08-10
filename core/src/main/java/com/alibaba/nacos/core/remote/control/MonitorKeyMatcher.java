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

import java.util.Objects;

/**
 * MatchMode.
 *
 * @author liuzunfei
 * @version $Id: MatchMode.java, v 0.1 2021年01月22日 12:38 PM liuzunfei Exp $
 */
@SuppressWarnings({"PMD.AbstractClassShouldStartWithAbstractNamingRule", "PMD.UndefineMagicConstantRule"})
public class MonitorKeyMatcher {
    
    /**
     * if provided monitor key match this monitor ,with monitor type.
     *
     * @param monitorKey monitorKey.
     * @return type matched result.
     */
    public static boolean matchWithType(String pattern, String monitorKey) {
        String[] typeInPattern = pattern.split(Constants.COLON);
        String[] typeInMonitorKey = monitorKey.split(Constants.COLON);
        if (!Objects.equals(typeInPattern[0], typeInMonitorKey[0])) {
            return false;
        }
        return match(pattern.substring(pattern.indexOf(Constants.COLON)),
                monitorKey.substring(monitorKey.indexOf(Constants.COLON)));
    }
    
    /**
     * if provided monitor key match this monitor.
     *
     * @param monitorKey monitorKey.
     * @return matched result.
     */
    public static boolean match(String pattern, String monitorKey) {
        pattern = pattern.trim();
        monitorKey = monitorKey.trim();
        //"AB",equals.
        if (!pattern.contains(Constants.ALL_PATTERN)) {
            return pattern.equals(monitorKey.trim());
        }
        //"*",match all.
        if (pattern.equals(Constants.ALL_PATTERN)) {
            return true;
        }
        String[] split = pattern.split("\\" + Constants.ALL_PATTERN);
        
        if (split.length == 1) {
            //"A*",prefix match.
            return monitorKey.startsWith(split[0]);
        } else if (split.length == 2) {
            //"*A",postfix match.
            if (StringUtils.isBlank(split[0])) {
                return monitorKey.endsWith(split[1]);
            }
            return monitorKey.startsWith(split[0]) && monitorKey.endsWith(split[1]);
        }
        
        return false;
    }
    
}
