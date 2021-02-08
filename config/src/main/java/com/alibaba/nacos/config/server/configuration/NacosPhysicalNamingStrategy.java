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

package com.alibaba.nacos.config.server.configuration;

import org.hibernate.boot.model.naming.DatabaseIdentifier;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.springframework.boot.orm.jpa.hibernate.SpringPhysicalNamingStrategy;

/**
 * Used to solve the problem of Oracle reserved words.
 *
 * @author Nacos
 */
public class NacosPhysicalNamingStrategy extends SpringPhysicalNamingStrategy {
    
    private static final String RESERVED_WORD_RESOURCE = "resource";
    
    @Override
    public Identifier toPhysicalColumnName(Identifier name, JdbcEnvironment jdbcEnvironment) {
        if (RESERVED_WORD_RESOURCE.equals(name.getText())) {
            return DatabaseIdentifier.toIdentifier("[`RESOURCE`]");
        }
        return super.toPhysicalColumnName(name, jdbcEnvironment);
    }
}
