/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.model;

import com.alibaba.nacos.api.exception.api.NacosApiException;

import java.io.Serializable;

/**
 * Nacos HTTP Form API Object.
 *
 * @author xiweng.yy
 */
public interface NacosForm extends Serializable {
    
    /**
     * check form parameters while valid.
     *
     * @throws NacosApiException when form parameters is invalid.
     */
    void validate() throws NacosApiException;
}
