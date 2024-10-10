/*
 *
 *  * Copyright 1999-2021 Alibaba Group Holding Ltd.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.alibaba.nacos.core.cluster.remote;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.common.utils.LoggerUtils;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.remote.request.MemberReportRequest;
import com.alibaba.nacos.core.cluster.remote.response.MemberReportResponse;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.core.remote.grpc.InvokeSource;
import com.alibaba.nacos.core.utils.Loggers;
import org.springframework.stereotype.Component;

/**
 * MemberReportHandler.
 *
 * @author : huangtianhui
 */
@Component
@InvokeSource(source = {RemoteConstants.LABEL_SOURCE_CLUSTER})
public class MemberReportHandler extends RequestHandler<MemberReportRequest, MemberReportResponse> {
    
    private final ServerMemberManager memberManager;
    
    public MemberReportHandler(ServerMemberManager memberManager) {
        this.memberManager = memberManager;
    }
    
    @Override
    public MemberReportResponse handle(MemberReportRequest request, RequestMeta meta) throws NacosException {
        Member node = request.getNode();
        if (!node.check()) {
            MemberReportResponse result = new MemberReportResponse();
            result.setErrorInfo(400, "Node information is illegal");
            return result;
        }
        LoggerUtils.printIfDebugEnabled(Loggers.CLUSTER, "node state report, receive info : {}", node);
        node.setState(NodeState.UP);
        node.setFailAccessCnt(0);
        memberManager.update(node);
        return new MemberReportResponse(memberManager.getSelf());
    }
    
}
