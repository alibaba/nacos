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

package com.alibaba.nacos.core.distributed;

/**
 * Double discovery using Java SPI mechanism or Spring IOC
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public interface KeyAnalysis {

    /**
     * The key may be complicated, and need to remove unnecessary
     * information to avoid affecting the result of distro
     *
     * @param source origin key
     * @return Key after operation
     */
    String analyze(String source);

    /**
     * Determine if you need to operate on the key
     *
     * @param source origin key
     * @return Determine if you need to operate on the key
     */
    boolean interest(String source);

}
