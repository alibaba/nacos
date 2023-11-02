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

import com.alibaba.nacos.persistence.enums.DbTypeEnum;
import com.alibaba.nacos.persistence.exception.DbDialectNotSupportException;

import java.util.EnumMap;
import java.util.Map;

/**
 * Paginated dialect factory.
 *
 * @author hkm
 */
public class DialectFactory {
    
    private static final Map<DbTypeEnum, IDialect> DIALECT_ENUM_MAP = new EnumMap<>(DbTypeEnum.class);
    
    public static IDialect getDialect(final DbTypeEnum dbType) {
        IDialect dialect = DIALECT_ENUM_MAP.get(dbType);
        if (null == dialect) {
            if (dbType == DbTypeEnum.OTHER) {
                throw new DbDialectNotSupportException(" DialectFactory database not supported. " + dbType.getDb());
            } else if (dbType == DbTypeEnum.MYSQL || dbType == DbTypeEnum.MARIADB || dbType == DbTypeEnum.GBASE
                    || dbType == DbTypeEnum.OSCAR || dbType == DbTypeEnum.XU_GU || dbType == DbTypeEnum.CLICK_HOUSE
                    || dbType == DbTypeEnum.OCEAN_BASE || dbType == DbTypeEnum.CUBRID || dbType == DbTypeEnum.GOLDILOCKS
                    || dbType == DbTypeEnum.CSIIDB) {
                // mysql same type
                dialect = new MySqlDialect();
            } else if (dbType == DbTypeEnum.ORACLE || dbType == DbTypeEnum.DM || dbType == DbTypeEnum.GAUSS) {
                // oracle same type
                dialect = new OracleDialect();
            } else if (dbType == DbTypeEnum.POSTGRE_SQL || dbType == DbTypeEnum.H2 || dbType == DbTypeEnum.LEALONE
                    || dbType == DbTypeEnum.SQLITE || dbType == DbTypeEnum.HSQL || dbType == DbTypeEnum.KINGBASE_ES
                    || dbType == DbTypeEnum.PHOENIX || dbType == DbTypeEnum.SAP_HANA || dbType == DbTypeEnum.IMPALA
                    || dbType == DbTypeEnum.HIGH_GO || dbType == DbTypeEnum.VERTICA || dbType == DbTypeEnum.REDSHIFT
                    || dbType == DbTypeEnum.OPENGAUSS || dbType == DbTypeEnum.TDENGINE || dbType == DbTypeEnum.UXDB) {
                // postgresql same type
                dialect = new PostgreSqlDialect();
            } else if (dbType == DbTypeEnum.ORACLE_12C || dbType == DbTypeEnum.FIREBIRD || dbType == DbTypeEnum.DERBY
                    || dbType == DbTypeEnum.SQL_SERVER) {
                //  derby same type
                dialect = new DerbyDialect();
            }
            DIALECT_ENUM_MAP.put(dbType, dialect);
        }
        return dialect;
    }
}
