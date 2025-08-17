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

package com.alibaba.nacos.plugin.datasource.mapper.ext;

import com.alibaba.nacos.common.constant.Symbols;

/**
 * Where Builder.
 *
 * @author haiqi.wang
 * @date 2024/08/13
 */
public class WhereBuilder extends AbstractWhereBuilder<WhereBuilder> {

    /**
     * Default Construct.
     *
     * @param sql Sql Script
     */
    public WhereBuilder(String sql) {
        super(sql);
    }

    /**
     * Build offset.
     *
     * @param startRow Start row
     * @param pageSize Page size
     * @return Return {@link WhereBuilder}
     */
    public WhereBuilder offset(int startRow, int pageSize) {
        paging.append(" OFFSET ").append(startRow).append(" ROWS FETCH NEXT ").append(pageSize).append(" ROWS ONLY");
        return this;
    }

    /**
     * Build limit.
     *
     * @param startRow Start row
     * @param pageSize Page size
     * @return Return {@link WhereBuilder}
     */
    public WhereBuilder limit(int startRow, int pageSize) {
        paging.append(" LIMIT ").append(startRow).append(Symbols.COMMA).append(pageSize);
        return this;
    }

}