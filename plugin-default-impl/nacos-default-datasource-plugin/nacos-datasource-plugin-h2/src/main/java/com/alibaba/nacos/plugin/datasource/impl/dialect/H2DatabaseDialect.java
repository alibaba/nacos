/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.datasource.impl.dialect;

import com.alibaba.nacos.plugin.datasource.constants.DatabaseTypeConstant;
import com.alibaba.nacos.plugin.datasource.impl.enums.h2.TrustedH2FunctionEnum;

/**
 * H2 database dialect for the embedded standalone storage.
 *
 * <p>H2 accepts the SQL:2008 {@code OFFSET ... ROWS FETCH NEXT ... ROWS ONLY} form used by the mappers. The H2
 * driver is not in the default plugin distribution. Add {@code com.h2database:h2} and the H2 plugin to
 * {@code plugins/}.
 *
 * @author Nacos
 */
public class H2DatabaseDialect extends AbstractDatabaseDialect {
    
    private static final String DEFAULT_DRIVER_CLASS_NAME = "org.h2.Driver";
    
    @Override
    public String getType() {
        return DatabaseTypeConstant.H2;
    }
    
    @Override
    public String getDefaultDriverClassName() {
        return DEFAULT_DRIVER_CLASS_NAME;
    }
    
    @Override
    public String getFunction(String functionName) {
        return TrustedH2FunctionEnum.getFunctionByName(functionName);
    }
}
