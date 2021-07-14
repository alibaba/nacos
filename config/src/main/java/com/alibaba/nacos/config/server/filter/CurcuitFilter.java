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

package com.alibaba.nacos.config.server.filter;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.SmartSubscriber;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.ExceptionUtil;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.event.RaftDbErrorEvent;
import com.alibaba.nacos.config.server.model.event.RaftDbErrorRecoverEvent;
import com.alibaba.nacos.consistency.ProtocolMetaData;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.cp.MetadataKey;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberMetaDataConstants;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.AccessControlException;
import java.util.Arrays;
import java.util.List;

/**
 * If the embedded distributed storage is enabled, all requests are routed to the Leader node for processing, and the
 * maximum number of forwards for a single request cannot exceed three.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class CurcuitFilter implements Filter {
    
    private volatile boolean isDowngrading = false;
    
    private volatile boolean isOpenService = false;
    
    @Autowired
    private ServerMemberManager memberManager;
    
    @Autowired
    private CPProtocol protocol;
    
    @Autowired
    private ControllerMethodsCache controllerMethodsCache;
    
    @PostConstruct
    protected void init() {
        listenerSelfInCluster();
        registerSubscribe();
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        
        if (!isOpenService) {
            resp.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "In the node initialization, unable to process any requests at this time");
            return;
        }
        
        try {
            // If an unrecoverable exception occurs on this node, the write request operation shall not be processed
            // This is a very important warning message !!!
            if (isDowngrading) {
                resp.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "Unable to process the request at this time: System triggered degradation");
                return;
            }
            
            chain.doFilter(req, response);
        } catch (AccessControlException e) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "access denied: " + ExceptionUtil.getAllExceptionMsg(e));
        } catch (Throwable e) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Server failed," + e.toString());
        }
    }
    
    @Override
    public void destroy() {
    
    }
    
    private void listenerSelfInCluster() {
        protocol.protocolMetaData().subscribe(Constants.CONFIG_MODEL_RAFT_GROUP, MetadataKey.RAFT_GROUP_MEMBER, o -> {
            if (!(o instanceof ProtocolMetaData.ValueItem)) {
                return;
            }
            final List<String> peers = (List<String>) ((ProtocolMetaData.ValueItem) o).getData();
            if (CollectionUtils.isEmpty(peers)) {
                isOpenService = false;
                return;
            }
            final Member self = memberManager.getSelf();
            final String raftAddress = self.getIp() + ":" + self.getExtendVal(MemberMetaDataConstants.RAFT_PORT);
            // Only when you are in the cluster and the current Leader is
            // elected can you provide external services
            isOpenService = peers.contains(raftAddress);
        });
    }
    
    private void registerSubscribe() {
        NotifyCenter.registerSubscriber(new SmartSubscriber() {
            
            @Override
            public void onEvent(Event event) {
                // @JustForTest
                // This event only happens in the case of unit tests
                if (event instanceof RaftDbErrorRecoverEvent) {
                    isDowngrading = false;
                    return;
                }
                if (event instanceof RaftDbErrorEvent) {
                    isDowngrading = true;
                }
            }
            
            @Override
            public List<Class<? extends Event>> subscribeTypes() {
                return Arrays.asList(RaftDbErrorRecoverEvent.class, RaftDbErrorEvent.class);
            }
        });
    }
    
}
