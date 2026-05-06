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

package com.alibaba.nacos.plugin.auth.impl.persistence.handler.support;

import com.alibaba.nacos.persistence.constants.PersistenceConstant;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthPageConstant;
import com.alibaba.nacos.plugin.auth.impl.model.OffsetFetchResult;
import com.alibaba.nacos.plugin.auth.impl.persistence.handler.PageHandlerAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * mysql page handler adapter.
 *
 * @author huangKeMing
 */
public class MysqlPageHandlerAdapter implements PageHandlerAdapter {
    
    @Override
    public boolean supports(String dataSourceType) {
        return PersistenceConstant.MYSQL.equals(dataSourceType);
    }
    
    @Override
    public OffsetFetchResult addOffsetAndFetchNext(String fetchSql, Object[] arg, int pageNo, int pageSize) {
        if (!fetchSql.contains(AuthPageConstant.LIMIT)) {
            fetchSql += " " + AuthPageConstant.LIMIT_SIZE;
            List<Object> newArgsList = new ArrayList<>(Arrays.asList(arg));
            newArgsList.add((pageNo - 1) * pageSize);
            newArgsList.add(pageSize);
            
            Object[] newArgs = newArgsList.toArray(new Object[newArgsList.size()]);
            return new OffsetFetchResult(fetchSql, newArgs);
        }
        
        return new OffsetFetchResult(fetchSql, arg);
    }
    
}
