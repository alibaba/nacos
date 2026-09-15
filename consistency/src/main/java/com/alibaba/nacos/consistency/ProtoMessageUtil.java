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
import com.google.protobuf.InvalidProtocolBufferException;
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
     * Request type prefix tag, distinct from fields of ReadRequest and WriteRequest.
     */
    public static final int REQUEST_TYPE_FIELD_TAG = 7 << 3;
    
    public static final int REQUEST_TYPE_READ = 1;
    
    public static final int REQUEST_TYPE_WRITE = 2;
    
    /**
     * Parses Raft task data with a request type prefix into a Protobuf request.
     *
     * @param bytes request type prefix followed by the serialized request
     * @return the read or write request selected by the prefix
     * @throws ConsistencyException if the prefix or Protobuf data is invalid
     */
    public static Message parse(byte[] bytes) {
        if (bytes == null || bytes.length < 2 || bytes[0] != REQUEST_TYPE_FIELD_TAG) {
            LOGGER.debug("Failed to parse protocol request: missing request type prefix");
            throw new ConsistencyException("Missing request type prefix");
        }
        
        try {
            switch (bytes[1]) {
                case REQUEST_TYPE_READ:
                    return ReadRequest.parseFrom(bytes);
                case REQUEST_TYPE_WRITE:
                    return WriteRequest.parseFrom(bytes);
                default:
                    LOGGER.debug("Failed to parse protocol request: unsupported request type {}",
                        bytes[1]);
                    throw new ConsistencyException("Unsupported request type: " + bytes[1]);
            }
        } catch (InvalidProtocolBufferException e) {
            LOGGER.debug("Failed to parse protocol request, request type: {}", bytes[1], e);
            throw new ConsistencyException("Failed to parse protocol request", e);
        }
    }
}
