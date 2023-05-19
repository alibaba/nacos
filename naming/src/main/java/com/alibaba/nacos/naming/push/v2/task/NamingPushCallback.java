/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.push.v2.task;

import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.remote.PushCallBack;

/**
 * Push callback for Naming.
 *
 * @author xiweng.yy
 */
public interface NamingPushCallback extends PushCallBack {
    
    /**
     * Set actual pushed service info, the host list of service info may be changed by selector. Detail see implement of
     * {@link com.alibaba.nacos.naming.push.v2.executor.PushExecutor}.
     *
     * @param serviceInfo actual pushed service info
     */
    void setActualServiceInfo(ServiceInfo serviceInfo);
}
