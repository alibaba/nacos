/*
 *  Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.alibaba.nacos.consistency;

import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.consistency.exception.ConsistencyException;
import com.google.protobuf.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * protobuf message utils.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ProtoMessageUtil {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ProtoMessageUtil.class);
    
    /**
     * should be different from field tags of ReadRequest or WriteQuest.
     */
    public static final int REQUEST_TYPE_FIELD_TAG = 7 << 3;
    
    public static final int REQUEST_TYPE_READ = 1;
    
    public static final int REQUEST_TYPE_WRITE = 2;
    
    /**
     * Converts the byte array to a specific Protobuf object.
     *
     * @param bytes An array of bytes
     * @return Message
     */
    public static Message parse(byte[] bytes) {
        try {
            if (bytes[0] == REQUEST_TYPE_FIELD_TAG) {
                if (bytes[1] == REQUEST_TYPE_READ) {
                    return ReadRequest.parseFrom(bytes);
                } else {
                    return WriteRequest.parseFrom(bytes);
                }
            }
        } catch (Throwable e) {
            LOGGER.debug("Failed to parse protocol request", e);
        }
        
        throw new ConsistencyException(
            "The current array cannot be serialized to the corresponding object");
    }
}
