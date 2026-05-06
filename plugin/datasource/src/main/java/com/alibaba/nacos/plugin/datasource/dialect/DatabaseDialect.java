/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.datasource.dialect;

/**
 * DatabaseDialect interface.
 * @author Long Yu
 */
public interface DatabaseDialect {

    /**
     * get database type.
     * @return return database type name
     */
    public String getType();

    /**
     * get frist index page param.
     * @param page current pageNo
     * @param pageSize current pageSize
     * @return offset val or maxRange
     */
    public int getPagePrevNum(int page, int pageSize);

    /**
     * get second index page param.
     * @param page current pageNo
     * @param pageSize current pageSize
     * @return limit val or minRange
     */
    public int getPageLastNum(int page, int pageSize);

    /**
     * get page limit top data sql,contain  placeholder.
     * @param sql orign sql
     * @return append limit sql
     */
    public String getLimitTopSqlWithMark(String sql);

    /**
     * get page limit page data sql,contain  placeholder.
     * @param sql orign sql
     * @return append limit sql
     */
    public String getLimitPageSqlWithMark(String sql);

    /**
     * get page limit page data sql,using number.
     * @param sql orign sql
     * @param pageNo current pageNo
     * @param pageSize current pageSize
     * @return contain page number param sql
     */
    public String getLimitPageSql(String sql, int pageNo, int pageSize);
    
    /**
     * get page limit page data sql,using offset.
     * @param sql orign sql
     * @param startOffset current offset row
     * @param pageSize current pageSize
     * @return contain page number param sql
     */
    public String getLimitPageSqlWithOffset(String sql, int startOffset, int pageSize);

    /**
     * get database return primary keys.
     * @return
     */
    public String[] getReturnPrimaryKeys();

    /**
     * Get the function corresponding to the dialect according to the function name
     * @author Mr.Muzhi
     * @since 2025/1/7 16:30
     * @param functionName functionName
     * @return function
     */
    String getFunction(String functionName);
}
