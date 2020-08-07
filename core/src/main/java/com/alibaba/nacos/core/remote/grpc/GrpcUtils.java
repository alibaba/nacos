/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.remote.grpc;

import com.alibaba.nacos.api.grpc.GrpcMetadata;
import com.alibaba.nacos.api.grpc.GrpcResponse;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.api.remote.response.UnKnowResponse;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;

import java.io.UnsupportedEncodingException;

/**
 * GrpcUtils.
 *
 * @author liuzunfei
 * @version $Id: GrpcUtils.java, v 0.1 2020年07月14日 12:15 AM liuzunfei Exp $
 */
public class GrpcUtils {
    
    /**
     * convert Response to GrpcResponse.
     *
     * @param request request.
     * @return
     */
    public static GrpcResponse convert(Request request, String ackId) {
    
        String jsonString = JacksonUtils.toJson(request);
        byte[] bytes = null;
        try {
            bytes = jsonString.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
        GrpcMetadata metadata = GrpcMetadata.newBuilder().build();
        GrpcResponse grpcResponse = GrpcResponse.newBuilder().setAck(ackId).setType(request.getType())
                .setBody(Any.newBuilder().setValue(ByteString.copyFrom(bytes))).build();
        return grpcResponse;
    }
    
    /**
     * convert Response to GrpcResponse.
     *
     * @param response response.
     * @return
     */
    public static GrpcResponse convert(Response response) {
        
        String jsonString = JacksonUtils.toJson(response);
        byte[] bytes = null;
        try {
            bytes = jsonString.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
        GrpcMetadata metadata = GrpcMetadata.newBuilder().build();
        GrpcResponse grpcResponse = GrpcResponse.newBuilder().setType(response.getType())
                .setBody(Any.newBuilder().setValue(ByteString.copyFrom(bytes))).build();
        return grpcResponse;
    }
    /**
     * build fail response.
     *
     * @param msg errorMsg
     * @return
     */
    public static GrpcResponse buildFailResponse(String msg) {
        UnKnowResponse response = new UnKnowResponse();
        response.setErrorCode(ResponseCode.FAIL.getCode());
        response.setMessage(msg);
        byte[] bytes = JacksonUtils.toJsonBytes(response);
        GrpcMetadata metadata = GrpcMetadata.newBuilder().build();
        GrpcResponse grpcResponse = GrpcResponse.newBuilder()
                .setBody(Any.newBuilder().setValue(ByteString.copyFrom(bytes))).build();
        return grpcResponse;
    }
    
}
