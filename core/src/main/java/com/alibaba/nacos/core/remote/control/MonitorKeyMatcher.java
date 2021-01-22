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

import java.util.Objects;

/**
 * MatchMode.
 *
 * @author liuzunfei
 * @version $Id: MatchMode.java, v 0.1 2021年01月22日 12:38 PM liuzunfei Exp $
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public abstract class MonitorKeyMatcher {
    
    protected String pattern;
    
    /**
     * if provided monitor key match this monitor.
     *
     * @param monitorKey monitorKey.
     * @return
     */
    abstract boolean match(String monitorKey);
    
    /**
     * get mode.
     *
     * @return
     */
    abstract String getMatchModel();
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MonitorKeyMatcher that = (MonitorKeyMatcher) o;
        return Objects.equals(getMatchModel(), that.getMatchModel()) && Objects.equals(pattern, that.pattern);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(getMatchModel(), pattern);
    }
    
    @Override
    public String toString() {
        return getMatchModel() + Constants.POUND + pattern;
    }
}
