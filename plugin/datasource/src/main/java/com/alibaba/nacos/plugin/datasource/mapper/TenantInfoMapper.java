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

package com.alibaba.nacos.plugin.datasource.mapper;

import com.alibaba.nacos.plugin.datasource.constants.TableConstant;

/**
 * The tenant info mapper. If there is a new non-universal sql, you can add it here.
 *
 * @author hyx
 **/

public interface TenantInfoMapper extends Mapper {
    
    /**
     * 获取返回表名.
     *
     * @return 表名
     */
    default String getTableName() {
        return TableConstant.TENANT_INFO;
    }

}
