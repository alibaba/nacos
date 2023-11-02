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

package com.alibaba.nacos.persistence.enums;

/**
 * Supported database types, primarily for pagination dialects.
 *
 * @author hkm
 */
public enum DbTypeEnum {
    
    /**
     * MYSQL.
     */
    MYSQL("mysql"),
    
    /**
     * DERBY.
     */
    DERBY("derby"),
    
    /**
     * MARIADB.
     */
    MARIADB("mariadb"),
    
    /**
     * ORACLE.
     */
    ORACLE("oracle"),
    
    /**
     * ORACLE_12C.
     */
    ORACLE_12C("oracle12c"),
    
    /**
     * GBASE.
     */
    GBASE("gbase"),
    
    /**
     * DB2.
     */
    DB2("db2"),
    
    /**
     * H2.
     */
    H2("h2"),
    
    /**
     * HSQL.
     */
    HSQL("hsql"),
    
    /**
     * SQLITE.
     */
    SQLITE("sqlite"),
    
    /**
     * POSTGRE_SQL.
     */
    POSTGRE_SQL("postgresql"),
    
    /**
     * SQL_SERVER2005.
     */
    SQL_SERVER2005("sqlserver2005"),
    
    /**
     * SQL_SERVER.
     */
    SQL_SERVER("sqlserver"),
    
    /**
     * DM.
     */
    DM("dm"),
    
    /**
     * XU_GU.
     */
    XU_GU("xugu"),
    
    /**
     * KINGBASE_ES.
     */
    KINGBASE_ES("kingbasees"),
    
    /**
     * PHOENIX.
     */
    PHOENIX("phoenix"),
    
    /**
     * GAUSS.
     */
    GAUSS("zenith"),
    
    /**
     * CLICK_HOUSE.
     */
    CLICK_HOUSE("clickhouse"),
    
    /**
     * SINODB.
     */
    SINODB("sinodb"),
    
    /**
     * OSCAR.
     */
    OSCAR("oscar"),
    
    /**
     * SYBASE.
     */
    SYBASE("sybase"),
    
    /**
     * OCEANBASE.
     */
    OCEAN_BASE("oceanbase"),
    
    /**
     * FIREBIRD.
     */
    FIREBIRD("Firebird"),
    
    /**
     * HIGH_GO.
     */
    HIGH_GO("highgo"),
    
    /**
     * CUBRID.
     */
    CUBRID("cubrid"),
    
    /**
     * GOLDILOCKS.
     */
    GOLDILOCKS("goldilocks"),
    
    /**
     * CSIIDB.
     */
    CSIIDB("csiidb"),
    /**
     * HANA.
     */
    SAP_HANA("hana"),
    
    /**
     * IMPALA.
     */
    IMPALA("impala"),
    
    /**
     * VERTICA.
     */
    VERTICA("vertica"),
    
    /**
     * XCLOUD.
     */
    XCLOUD("xcloud"),
    
    /**
     * REDSHIFT.
     */
    REDSHIFT("redshift"),
    
    /**
     * OPENGAUSS.
     */
    OPENGAUSS("openGauss"),
    
    /**
     * TDENGINE.
     */
    TDENGINE("TDengine"),
    
    /**
     * INFORMIX.
     */
    INFORMIX("informix"),
    
    /**
     * UXDB.
     */
    UXDB("uxdb"),
    
    /**
     * LEALONE.
     */
    LEALONE("lealone"),
    
    /**
     * UNKNOWN DB.
     */
    OTHER("other");
    
    /**
     * The name of the database.
     */
    private final String db;
    
    public static DbTypeEnum getDbType(String dbType) {
        for (DbTypeEnum type : DbTypeEnum.values()) {
            if (type.db.equalsIgnoreCase(dbType)) {
                return type;
            }
        }
        return OTHER;
    }
    
    DbTypeEnum(String db) {
        this.db = db;
    }
    
    public String getDb() {
        return db;
    }
}
