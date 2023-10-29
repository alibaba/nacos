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
     * ORACLE.
     */
    ORACLE("oracle"),
    
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
