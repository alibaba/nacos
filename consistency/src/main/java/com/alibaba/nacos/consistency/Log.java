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

package com.alibaba.nacos.consistency;

import java.io.Serializable;
import java.util.Map;

/**
 * Transactions committed by the distributed consistency protocol
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public interface Log extends Serializable {

    /**
     * Returns the business to which this Log belongs
     * This is an important distinction. How Protocol
     * distributes different logs to different logs
     * depends on this information.
     *
     * @return business-info
     */
    String getBiz();

    /**
     * setting biz info
     *
     * @param biz biz info
     */
    void setBiz(String biz);

    /**
     * datum key
     *
     * @return key
     */
    String getKey();

    /**
     * Actual committed transaction data to be serialized
     *
     * @return byte[]
     */
    byte[] getData();

    /**
     * Class name information of the data, used for deserialization
     *
     * @return {@link Class#getCanonicalName()}
     */
    String getClassName();

    /**
     * Specific data manipulation
     *
     * @return operation name
     */
    String getOperation();

    /**
     * Information carried in this transaction
     *
     * @param key key
     * @return value
     */
    String extendVal(String key);

    /**
     * Set additional info data
     *
     * @param key key
     * @param val value
     */
    void addExtendVal(String key, String val);

    /**
     * Set additional information Map
     *
     * @param extendInfo {@link Map<String, String>}
     */
    void appendExtendInfo(Map<String, String> extendInfo);

    /**
     * list all extendInfo
     *
     * @return {@link Map<String, String>}
     */
    Map<String, String> listExtendInfo();

}
