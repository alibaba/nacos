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
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.auth.ActionTypes;
import com.alibaba.nacos.core.auth.Secured;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.naming.cluster.ServerListManager;
import com.alibaba.nacos.naming.cluster.ServerStatusManager;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftCore;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.misc.*;
import com.alibaba.nacos.naming.push.PushService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/**
 * Operation for operators
 *
 * @author nkorange
 */
@RestController
@RequestMapping({UtilsAndCommons.NACOS_NAMING_CONTEXT + "/operator", UtilsAndCommons.NACOS_NAMING_CONTEXT + "/ops"})
public class OperatorController {

    private final PushService pushService;
    private final SwitchManager switchManager;
    private final ServerListManager serverListManager;
    private final ServiceManager serviceManager;
    private final ServerMemberManager memberManager;
    private final ServerStatusManager serverStatusManager;
    private final SwitchDomain switchDomain;
    private final DistroMapper distroMapper;
    private final RaftCore raftCore;

    public OperatorController(PushService pushService, SwitchManager switchManager,
            ServerListManager serverListManager, ServiceManager serviceManager, ServerMemberManager memberManager,
            ServerStatusManager serverStatusManager, SwitchDomain switchDomain,
            DistroMapper distroMapper, RaftCore raftCore) {
        this.pushService = pushService;
        this.switchManager = switchManager;
        this.serverListManager = serverListManager;
        this.serviceManager = serviceManager;
        this.memberManager = memberManager;
        this.serverStatusManager = serverStatusManager;
        this.switchDomain = switchDomain;
        this.distroMapper = distroMapper;
        this.raftCore = raftCore;
    }

    @RequestMapping("/push/state")
    public ObjectNode pushState(@RequestParam(required = false) boolean detail, @RequestParam(required = false) boolean reset) {

        ObjectNode result = JacksonUtils.createEmptyJsonNode();

        List<PushService.Receiver.AckEntry> failedPushes = PushService.getFailedPushes();
        int failedPushCount = pushService.getFailedPushCount();
        result.put("succeed", pushService.getTotalPush() - failedPushCount);
        result.put("total", pushService.getTotalPush());

        if (pushService.getTotalPush() > 0) {
            result.put("ratio", ((float) pushService.getTotalPush() - failedPushCount) / pushService.getTotalPush());
        } else {
            result.put("ratio", 0);
        }

        ArrayNode dataArray = JacksonUtils.createEmptyArrayNode();
        if (detail) {
            for (PushService.Receiver.AckEntry entry : failedPushes) {
                try {
                    dataArray.add(new String(entry.origin.getData(), "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    dataArray.add("[encoding failure]");
                }
            }
            result.replace("data", dataArray);
        }

        if (reset) {
            PushService.resetPushState();
        }

        result.put("reset", reset);

        return result;
    }

    @GetMapping("/switches")
    public SwitchDomain switches(HttpServletRequest request) {
        return switchDomain;
    }

    @Secured(resource = "naming/switches", action = ActionTypes.WRITE)
    @PutMapping("/switches")
    public String updateSwitch(@RequestParam(required = false) boolean debug,
                               @RequestParam String entry, @RequestParam String value) throws Exception {

        switchManager.update(entry, value, debug);

        return "ok";
    }

    @Secured(resource = "naming/metrics", action = ActionTypes.READ)
    @GetMapping("/metrics")
    public ObjectNode metrics(HttpServletRequest request) {

        ObjectNode result = JacksonUtils.createEmptyJsonNode();

        int serviceCount = serviceManager.getServiceCount();
        int ipCount = serviceManager.getInstanceCount();

        int responsibleDomCount = serviceManager.getResponsibleServiceCount();
        int responsibleIPCount = serviceManager.getResponsibleInstanceCount();

        result.put("status", serverStatusManager.getServerStatus().name());
        result.put("serviceCount", serviceCount);
        result.put("instanceCount", ipCount);
        result.put("raftNotifyTaskCount", raftCore.getNotifyTaskCount());
        result.put("responsibleServiceCount", responsibleDomCount);
        result.put("responsibleInstanceCount", responsibleIPCount);
        result.put("cpu", ApplicationUtils.getCPU());
        result.put("load", ApplicationUtils.getLoad());
        result.put("mem", ApplicationUtils.getMem());

        return result;
    }

    @GetMapping("/distro/server")
    public ObjectNode getResponsibleServer4Service(@RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
                                                   @RequestParam String serviceName) {

        Service service = serviceManager.getService(namespaceId, serviceName);

        if (service == null) {
            throw new IllegalArgumentException("service not found");
        }

        ObjectNode result = JacksonUtils.createEmptyJsonNode();

        result.put("responsibleServer", distroMapper.mapSrv(serviceName));

        return result;
    }

    @GetMapping("/distro/status")
    public ObjectNode distroStatus(@RequestParam(defaultValue = "view") String action) {

        ObjectNode result = JacksonUtils.createEmptyJsonNode();

        if (StringUtils.equals(SwitchEntry.ACTION_VIEW, action)) {
            result.replace("status", JacksonUtils.transferToJsonNode(memberManager.allMembers()));
            return result;
        }

        return result;
    }

    @GetMapping("/servers")
    public ObjectNode getHealthyServerList(@RequestParam(required = false) boolean healthy) {

        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        if (healthy) {
            List<Member> healthyMember = memberManager.allMembers().stream()
                .filter(member -> member.getState() == NodeState.UP).collect(ArrayList::new,
                    ArrayList::add, ArrayList::addAll);
            result.replace("servers", JacksonUtils.transferToJsonNode(healthyMember));
        } else {
            result.replace("servers", JacksonUtils.transferToJsonNode(memberManager.allMembers()));
        }

        return result;
    }

    /**
     * This interface will be removed in a future release
     *
     * @deprecated 1.3.0 This function will be deleted sometime after version 1.3.0
     * @param serverStatus server status
     * @return "ok"
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
     * This interface will be removed in a future release
     *
     * @deprecated 1.3.0 This function will be deleted sometime after version 1.3.0
     * @return {@link JsonNode}
     */
    @Deprecated
    @RequestMapping(value = "/cluster/state", method = RequestMethod.GET)
    public JsonNode getClusterStates() {
        return JacksonUtils.transferToJsonNode(serviceManager.getMySelfClusterState());
    }
}
