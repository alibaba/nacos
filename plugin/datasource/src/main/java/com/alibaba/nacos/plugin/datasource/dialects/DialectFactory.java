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

package com.alibaba.nacos.plugin.datasource.dialects;

import com.alibaba.nacos.plugin.datasource.enums.DbTypeEnum;
import com.alibaba.nacos.plugin.datasource.exception.DbDialectNotSupportException;

import java.util.EnumMap;
import java.util.Map;

/**
 * Paginated dialect factory.
 *
 * @author huangKeMing
 */
public class DialectFactory {
    
    private static final Map<DbTypeEnum, IDialect> DIALECT_ENUM_MAP = new EnumMap<>(DbTypeEnum.class);
    
    public static IDialect getDialect(final DbTypeEnum dbType) {
        IDialect dialect = DIALECT_ENUM_MAP.get(dbType);
        if (null == dialect) {
            if (dbType == DbTypeEnum.OTHER) {
                throw new DbDialectNotSupportException(" DbDialect database not supported. " + dbType.getDb());
            } else if (dbType == DbTypeEnum.MYSQL) {
                // mysql same type
                dialect = new MySqlDialect();
            } else if (dbType == DbTypeEnum.DERBY) {
                //  derby same type
                dialect = new DerbyDialect();
            }
            DIALECT_ENUM_MAP.put(dbType, dialect);
        }
        return dialect;
    }
}
