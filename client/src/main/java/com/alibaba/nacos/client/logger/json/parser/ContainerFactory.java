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
package com.alibaba.nacos.client.logger.json.parser;

import java.util.List;
import java.util.Map;

/**
 * Container factory for creating containers for JSON object and JSON array.
 *
 * @author FangYidong<fangyidong   @   yahoo.com.cn>
 * @see com.alibaba.nacos.client.logger.json.parser.JSONParser#parse(java.io.Reader, ContainerFactory)
 */
public interface ContainerFactory {
    /**
     * create json container
     *
     * @return A Map instance to store JSON object, or null if you want to use com.alibaba.nacos.client.logger
     * .jsonJSONObject.
     */
    Map createObjectContainer();

    /**
     * create array json container
     *
     * @return A List instance to store JSON array, or null if you want to use com.alibaba.nacos.client.logger
     * .jsonJSONArray.
     */
    List creatArrayContainer();
}
