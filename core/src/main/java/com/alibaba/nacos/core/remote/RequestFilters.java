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

package com.alibaba.nacos.core.remote;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * filters of request.
 *
 * @author liuzunfei
 * @version $Id: RequestFilters.java, v 0.1 2020年09月14日 12:00 PM liuzunfei Exp $
 */
@Service
public class RequestFilters {
    
    List<AbstractRequestFilter> filters = new ArrayList<AbstractRequestFilter>();
    
    public void registerFilter(AbstractRequestFilter requestFilter) {
        filters.add(requestFilter);
    }
}
