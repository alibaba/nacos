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

package com.alibaba.nacos.api.config.convert;

/**
 * Nacos Config Converter.
 *
 * @param <T> the target type that wanted
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 * @since 0.2.0
 */
public interface NacosConfigConverter<T> {
    
    /**
     * can convert to be target type or not.
     *
     * @param targetType the type of target
     * @return If can , return <code>true</code>, or <code>false</code>
     */
    boolean canConvert(Class<T> targetType);
    
    /**
     * Convert the Nacos' config of type S to target type T.
     *
     * @param config the Naocs's config to convert, which must be an instance of S (never {@code null})
     * @return the converted object, which must be an instance of T (potentially {@code null})
     */
    T convert(String config);
    
}
