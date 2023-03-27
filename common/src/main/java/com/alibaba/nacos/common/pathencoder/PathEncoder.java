/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.pathencoder;

/**
 * To encode path if illegal,an os may have a PathEncoder.
 *
 * @author daydreamer-ia
 */
public interface PathEncoder {

    /**
     * encode path.
     *
     * @param str origin
     * @param charset charset
     * @return new path
     */
    String encode(String str, String charset);

    /**
     * decode path.
     *
     * @param str new path
     * @param charset charset
     * @return origin path
     */
    String decode(String str, String charset);

    /**
     * return simple lowercase os name.
     *
     * @return simple lowercase os name
     */
    String name();

    /**
     * whether to encode.
     *
     * @param key key
     * @return whether to encode.
     */
    boolean needEncode(String key);
}
