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

package com.alibaba.nacos.api;

import com.alibaba.nacos.api.exception.NacosException;

/**
 * @author liaochuntao
 * @date 2019-09-06 15:07
 **/
public interface LifeCycle {

    /**
     * The success of start-up
     *
     * @return start label
     */
    boolean isStart();

    /**
     * The success of destroy
     *
     * @return destroy label
     */
    boolean isDestroy();

    /**
     * The service start
     *
     * @throws NacosException
     */
    void start() throws NacosException;

    /**
     * The service destroy
     *
     * @throws NacosException
     */
    void destroy() throws NacosException ;

}
