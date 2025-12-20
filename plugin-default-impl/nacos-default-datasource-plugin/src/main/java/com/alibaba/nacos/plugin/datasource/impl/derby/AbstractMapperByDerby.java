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

package com.alibaba.nacos.plugin.datasource.impl.derby;

import com.alibaba.nacos.plugin.datasource.enums.derby.TrustedDerbylFunctionEnum;
import com.alibaba.nacos.plugin.datasource.mapper.AbstractMapper;

/**
 * The abstract derby mapper contains CRUD methods.
 *
 * @author blake.qiu
 **/
public abstract class AbstractMapperByDerby extends AbstractMapper {

    @Override
    public String getFunction(String functionName) {
        return TrustedDerbylFunctionEnum.getFunctionByName(functionName);
    }

}
