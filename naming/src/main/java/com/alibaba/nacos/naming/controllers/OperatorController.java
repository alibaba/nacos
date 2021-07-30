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

package com.alibaba.nacos.naming.controllers;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.common.ActionTypes;
import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.cluster.ServerListManager;
import com.alibaba.nacos.naming.cluster.ServerStatusManager;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftCore;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.SwitchEntry;
import com.alibaba.nacos.naming.misc.SwitchManager;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * Operation for operators.
 *
 * @author nkorange
 */
@RestController
@RequestMapping({UtilsAndCommons.NACOS_NAMING_CONTEXT + UtilsAndCommons.NACOS_NAMING_OPERATOR_CONTEXT,
        UtilsAndCommons.NACOS_NAMING_CONTEXT + "/ops"})
public class OperatorController {
    
    private final SwitchManager switchManager;
    
    private final ServerListManager serverListManager;
    
    private final ServiceManager serviceManager;
    
    private final ServerMemberManager memberManager;
    
    private final ServerStatusManager serverStatusManager;
    
    private final SwitchDomain switchDomain;
    
    private final DistroMapper distroMapper;
    
    private final RaftCore raftCore;
    
    public OperatorController(SwitchManager switchManager, ServerListManager serverListManager,
            ServiceManager serviceManager, ServerMemberManager memberManager, ServerStatusManager serverStatusManager,
            SwitchDomain switchDomain, DistroMapper distroMapper, RaftCore raftCore) {
        this.switchManager = switchManager;
        this.serverListManager = serverListManager;
        this.serviceManager = serviceManager;
        this.memberManager = memberManager;
        this.serverStatusManager = serverStatusManager;
        this.switchDomain = switchDomain;
        this.distroMapper = distroMapper;
        this.raftCore = raftCore;
    }
    
    /**
     * Get push metric status.
     *
     * @param detail whether return detail information
     * @param reset  whether reset metric information after return information
     * @return push metric status
     */
    @RequestMapping("/push/state")
    public ObjectNode pushState(@RequestParam(required = false) boolean detail,
            @RequestParam(required = false) boolean reset) {
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        int failedPushCount = MetricsMonitor.getFailedPushMonitor().get();
        int totalPushCount = MetricsMonitor.getTotalPushMonitor().get();
        result.put("succeed", totalPushCount - failedPushCount);
        result.put("total", totalPushCount);
        if (totalPushCount > 0) {
            result.put("ratio", ((float) totalPushCount - failedPushCount) / totalPushCount);
        } else {
            result.put("ratio", 0);
        }
        if (detail) {
            ObjectNode detailNode = JacksonUtils.createEmptyJsonNode();
            detailNode.put("avgPushCost", MetricsMonitor.getAvgPushCostMonitor().get());
            detailNode.put("maxPushCost", MetricsMonitor.getMaxPushCostMonitor().get());
            result.replace("detail", detailNode);
        }
        if (reset) {
            MetricsMonitor.resetPush();
        }
        result.put("reset", reset);
        return result;
    }
    
    /**
     * Get switch information.
     *
     * @param request no used
     * @return switchDomain
     */
    @GetMapping("/switches")
    public SwitchDomain switches(HttpServletRequest request) {
        return switchDomain;
    }
    
    /**
     * Update switch information.
     *
     * @param debug whether debug
     * @param entry item entry of switch, {@link SwitchEntry}
     * @param value switch value
     * @return 'ok' if success
     * @throws Exception exception
     */
    @Secured(resource = "naming/switches", action = ActionTypes.WRITE)
    @PutMapping("/switches")
    public String updateSwitch(@RequestParam(required = false) boolean debug, @RequestParam String entry,
            @RequestParam String value) throws Exception {
        
        switchManager.update(entry, value, debug);
        
        return "ok";
    }
    
    /**
     * Get metrics information.
     *
     * @param request request
     * @return metrics information
     */
    @GetMapping("/metrics")
    public ObjectNode metrics(HttpServletRequest request) {
        boolean onlyStatus = Boolean.parseBoolean(WebUtils.optional(request, "onlyStatus", "true"));
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        result.put("status", serverStatusManager.getServerStatus().name());
        if (onlyStatus) {
            return result;
        }
        int responsibleDomCount = serviceManager.getResponsibleServiceCount();
        int responsibleIpCount = serviceManager.getResponsibleInstanceCount();
        result.put("serviceCount", MetricsMonitor.getDomCountMonitor().get());
        result.put("instanceCount", MetricsMonitor.getIpCountMonitor().get());
        result.put("subscribeCount", MetricsMonitor.getSubscriberCount().get());
        result.put("raftNotifyTaskCount", raftCore.getNotifyTaskCount());
        result.put("responsibleServiceCount", responsibleDomCount);
        result.put("responsibleInstanceCount", responsibleIpCount);
        result.put("cpu", EnvUtil.getCPU());
        result.put("load", EnvUtil.getLoad());
        result.put("mem", EnvUtil.getMem());
        return result;
    }
    
    @GetMapping("/distro/server")
    public ObjectNode getResponsibleServer4Service(
            @RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @RequestParam String serviceName) throws NacosException {
        Service service = serviceManager.getService(namespaceId, serviceName);
        
        serviceManager.checkServiceIsNull(service, namespaceId, serviceName);
        
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        result.put("responsibleServer", distroMapper.mapSrv(serviceName));
        return result;
    }
    
    @GetMapping("/distro/client")
    public ObjectNode getResponsibleServer4Client(@RequestParam String ip, @RequestParam String port) {
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        String tag = ip + InternetAddressUtil.IP_PORT_SPLITER + port;
        result.put("responsibleServer", distroMapper.mapSrv(tag));
        return result;
    }
    
    /**
     * This interface will be removed in a future release.
     *
     * @param healthy whether only query health server.
     * @return "ok"
     * @deprecated 1.3.0 This function will be deleted sometime after version 1.3.0
     */
    @GetMapping("/servers")
    public ObjectNode getHealthyServerList(@RequestParam(required = false) boolean healthy) {
        
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        if (healthy) {
            List<Member> healthyMember = memberManager.allMembers().stream()
                    .filter(member -> member.getState() == NodeState.UP)
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            result.replace("servers", JacksonUtils.transferToJsonNode(healthyMember));
        } else {
            result.replace("servers", JacksonUtils.transferToJsonNode(memberManager.allMembers()));
        }
        
        return result;
    }
    
    /**
     * This interface will be removed in a future release.
     *
     * @param serverStatus server status
     * @return "ok"
     * @deprecated 1.3.0 This function will be deleted sometime after version 1.3.0
     */
    @Deprecated
    @RequestMapping("/server/status")
    public String serverStatus(@RequestParam String serverStatus) {
        serverListManager.onReceiveServerStatus(serverStatus);
        return "ok";
    }
    
    @PutMapping("/log")
    public String setLogLevel(@RequestParam String logName, @RequestParam String logLevel) {
        Loggers.setLogLevel(logName, logLevel);
        return "ok";
    }
    
    /**
     * This interface will be removed in a future release.
     *
     * @return {@link JsonNode}
     * @deprecated 1.3.0 This function will be deleted sometime after version 1.3.0
     */
    @Deprecated
    @RequestMapping(value = "/cluster/state", method = RequestMethod.GET)
    public JsonNode getClusterStates() {
        return JacksonUtils.transferToJsonNode(serviceManager.getMySelfClusterState());
    }
}
