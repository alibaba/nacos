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

package com.alibaba.nacos.common;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public interface Serializer {

    /**
     * 将数据反序列化
     *
     * @param data
     * @param cls
     * @param <T>
     * @return
     */
    <T> T deSerialize(byte[] data, Class<T> cls);

    /**
     * 将数据反序列化
     *
     * @param data
     * @param classFullName
     * @param <T>
     * @return
     */
    <T> T deSerialize(byte[] data, String classFullName);

    /**
     * 将数据序列化
     *
     * @param obj
     * @return
     */
    byte[] serialize(Object obj);

    /**
     * The name of the serializer implementer
     * <ul>
     *     <li>If fastjson is used, fastjson is returned.</li>
     *     <li>If hession is used, hession is returned.</li>
     *     <li>If it is kryo, kryo is returned.</li>
     * </ul>
     *
     * @return name
     */
    String name();

}
