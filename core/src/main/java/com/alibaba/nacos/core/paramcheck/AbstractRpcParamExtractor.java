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

package com.alibaba.nacos.core.paramcheck;

import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.common.paramcheck.ParamInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract ParamExtractor class for rpc request.
 *
 * @author zhuoguang
 */
public abstract class AbstractRpcParamExtractor implements ParamExtractor<Request> {
    
    private final List<String> targetrequestlist;
    
    public AbstractRpcParamExtractor() {
        targetrequestlist = new ArrayList<>();
        init();
    }
    
    /**
     * Init, add target request to the target request list.
     */
    public abstract void init();
    
    @Override
    public List<String> getTargetRequestList() {
        return targetrequestlist;
    }
    
    /**
     * Extract param.
     *
     * @param request the request
     * @return the list
     * @throws Exception the exception
     */
    @Override
    public abstract List<ParamInfo> extractParam(Request request) throws Exception;
    
    public void addTargetRequest(String type) {
        targetrequestlist.add(type);
    }
}
