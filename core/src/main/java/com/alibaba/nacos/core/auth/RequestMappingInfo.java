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

package com.alibaba.nacos.core.auth;

import com.alibaba.nacos.core.auth.condition.ParamRequestCondition;
import com.alibaba.nacos.core.auth.condition.PathRequestCondition;

import java.util.Comparator;

/**
 * Request mapping information. to find the matched method by request
 *
 * @author horizonzy
 * @since 1.3.2
 */
public class RequestMappingInfo {
    
    private PathRequestCondition pathRequestCondition;
    
    private ParamRequestCondition paramRequestCondition;
    
    public ParamRequestCondition getParamRequestCondition() {
        return paramRequestCondition;
    }
    
    public void setParamRequestCondition(ParamRequestCondition paramRequestCondition) {
        this.paramRequestCondition = paramRequestCondition;
    }
    
    public void setPathRequestCondition(PathRequestCondition pathRequestCondition) {
        this.pathRequestCondition = pathRequestCondition;
    }
    
    @Override
    public String toString() {
        return "RequestMappingInfo{" + "pathRequestCondition=" + pathRequestCondition + ", paramRequestCondition="
                + paramRequestCondition + '}';
    }
    
    public static class RequestMappingInfoComparator implements Comparator<RequestMappingInfo> {
        
        @Override
        public int compare(RequestMappingInfo o1, RequestMappingInfo o2) {
            return Integer.compare(o2.getParamRequestCondition().getExpressions().size(),
                    o1.getParamRequestCondition().getExpressions().size());
        }
    }
}
