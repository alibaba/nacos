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

package com.alibaba.nacos.plugin.datasource.model;

import com.alibaba.nacos.plugin.datasource.enums.TrustedSqlFunctionEnum;

import java.util.Objects;

/**
 * Column function pair.
 *
 * @author blake.qiu
 */
public class ColumnFunctionPair {
    private final String column;

    private final TrustedSqlFunctionEnum functionEnum;

    public ColumnFunctionPair(String column) {
        this.column = column;
        this.functionEnum = null;
    }

    public ColumnFunctionPair(String column, TrustedSqlFunctionEnum functionEnum) {
        this.column = column;
        this.functionEnum = functionEnum;
    }

    /**
     * New ColumnFunctionPair with column.
     *
     * @param columnName columnName
     * @return ColumnFunctionPair
     */
    public static ColumnFunctionPair withColumn(String columnName) {
        return new ColumnFunctionPair(columnName);
    }

    /**
     * New ColumnFunctionPair with column and function.
     *
     * @param columnName columnName
     * @param function   function
     * @return ColumnFunctionPair
     */
    public static ColumnFunctionPair withColumnAndFunction(String columnName, TrustedSqlFunctionEnum function) {
        return new ColumnFunctionPair(columnName, function);
    }

    public String getColumn() {
        return column;
    }

    public String getFunction() {
        return functionEnum == null ? null : functionEnum.getFunction();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ColumnFunctionPair that = (ColumnFunctionPair) o;
        return Objects.equals(getColumn(), that.getColumn());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getColumn());
    }

    @Override
    public String toString() {
        return "ColumnFunctionPair{"
                + "column='" + column + '\''
                + ", function='" + this.getFunction() + '\''
                + '}';
    }
}
