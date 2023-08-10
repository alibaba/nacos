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

import com.alibaba.nacos.common.paramcheck.ParamInfo;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * Extract param from http-request.
 *
 * @author zhuoguang
 */
public abstract class AbstractHttpParamExtractor implements ParamExtractor<HttpServletRequest> {
    
    private static final String SPLITTER = "@@";
    
    private static final String NACOS_SERVER_CONTEXT = "/nacos";
    
    private final List<String> targetRequestList;
    
    /**
     * Instantiates a new Abstract http param extractor.
     */
    public AbstractHttpParamExtractor() {
        targetRequestList = new ArrayList<>();
        init();
    }
    
    /**
     * Init,add target request to the target request list.
     */
    public abstract void init();
    
    @Override
    public List<String> getTargetRequestList() {
        return targetRequestList;
    }
    
    /**
     * Extract param.
     *
     * @param request the request
     * @return the list
     * @throws Exception the exception
     */
    @Override
    public abstract List<ParamInfo> extractParam(HttpServletRequest request) throws Exception;
    
    /**
     * Add target request.
     *
     * @param uri    the uri
     * @param method the method
     */
    public void addTargetRequest(String uri, String method) {
        targetRequestList.add(NACOS_SERVER_CONTEXT + uri + SPLITTER + method);
        targetRequestList.add(uri + SPLITTER + method);
    }
    
    /**
     * Add default target request.
     *
     * @param module the module
     */
    public void addDefaultTargetRequest(String module) {
        targetRequestList.add("default" + SPLITTER + module);
    }
}
