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

package com.alibaba.nacos.persistence.dialects;

import com.alibaba.nacos.persistence.constants.PersistenceConstant;

import java.util.Arrays;

/**
 * Oracle Database pagination statement assembly implementation.
 *
 * @author hkm
 */
public class OracleDialect implements IDialect {
    
    @Override
    public DialectModel buildPaginationSql(String sqlFetchRows, Object[] args, int pageNo, int pageSize) {
        if (!sqlFetchRows.contains(PersistenceConstant.ROWNUM)) {
            String sqlFetch = sqlFetchRows + " ROWNUM <= " + FIRST_MARK + " ) WHERE ROW_ID > " + SECOND_MARK;
            Object[] newArgs = Arrays.copyOf(args, args.length + 2);
            newArgs[args.length] = (pageNo - 1) * pageSize;
            newArgs[args.length + 1] = pageSize;
            return new DialectModel(sqlFetch, newArgs);
        } else {
            return new DialectModel(sqlFetchRows, args);
        }
    }
}
