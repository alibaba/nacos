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
package com.alibaba.nacos.config.server.model;

/**
 * @author klw
 * @ClassName: SameConfigPolicy
 * @Description: processing policy of the same configuration
 * @date 2019/5/21 10:55
 */
public enum SameConfigPolicy {

    /**
     * @Description: abort import  on duplicate
     */
    ABORT,

    /**
     * @Description: skipping on duplicate
     */
    SKIP,

    /**
     * @Description: overwrite on duplicate
     */
    OVERWRITE

}
