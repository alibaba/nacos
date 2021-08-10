/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.execute;

import com.alibaba.nacos.naming.core.Instance;

/**
 * Help converting instance when upgrading/downgrading.
 *
 * @author gengtuo.ygt
 * on 2021/2/25
 */
public interface InstanceUpgradeHelper {

    /**
     * Convert to v1 instance.
     * 
     * @param v2 instance v2
     * @return instance v1
     */
    Instance toV1(com.alibaba.nacos.api.naming.pojo.Instance v2);

    /**
     * Convert to v2 instance.
     * 
     * @param v1 instance v1
     * @return instance v2
     */
    com.alibaba.nacos.api.naming.pojo.Instance toV2(Instance v1);

}
