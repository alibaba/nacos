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

package com.alibaba.nacos.plugin.auth.impl.persistence.handler;

import com.alibaba.nacos.plugin.auth.impl.model.OffsetFetchResult;

/**
 * Auth plugin page handler adapter.
 *
 * @author huangKeMing
 */
public interface PageHandlerAdapter {
    
    /**
     * Determine whether the current data source supports paging.
     *
     * @param dataSourceType data source type
     * @return true if the current data source supports paging
     */
    boolean supports(String dataSourceType);
    
    /**
     * Add offset and fetch next.
     *
     * @param fetchSql fetch sql.
     * @param arg      arguments.
     * @param pageNo   page number.
     * @param pageSize page size.
     * @return
     */
    OffsetFetchResult addOffsetAndFetchNext(String fetchSql, Object[] arg, int pageNo, int pageSize);
}
