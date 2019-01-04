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
package com.alibaba.nacos.client.logger.json;

import java.io.IOException;
import java.io.Writer;

/**
 * Beans that support customized output of JSON text to a writer shall implement this interface.
 *
 * @author FangYidong<fangyidong   @   yahoo.com.cn>
 */
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
public interface JSONStreamAware {
    /**
     * write JSON string to out.
     *
     * @param out out writer
     * @throws IOException Exception
     */
    void writeJSONString(Writer out) throws IOException;
}
