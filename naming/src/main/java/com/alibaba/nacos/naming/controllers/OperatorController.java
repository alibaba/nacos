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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.core.auth.ActionTypes;
import com.alibaba.nacos.core.auth.Secured;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.naming.cluster.ServerStatusManager;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftCore;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftPeer;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.misc.*;
import com.alibaba.nacos.naming.push.PushService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private PushService pushService;

    @Autowired
    private SwitchManager switchManager;

    @Autowired
    private ServiceManager serviceManager;

    @Autowired
    private ServerMemberManager memberManager;

    @Autowired
    private ServerStatusManager serverStatusManager;

    @Autowired
    private SwitchDomain switchDomain;

    @Autowired
    private DistroMapper distroMapper;

    @Autowired
    private RaftCore raftCore;

    @RequestMapping("/push/state")
    public JSONObject pushState(@RequestParam(required = false) boolean detail, @RequestParam(required = false) boolean reset) {

        JSONObject result = new JSONObject();

        List<PushService.Receiver.AckEntry> failedPushes = PushService.getFailedPushes();
        int failedPushCount = pushService.getFailedPushCount();
        result.put("succeed", pushService.getTotalPush() - failedPushCount);
        result.put("total", pushService.getTotalPush());

        if (pushService.getTotalPush() > 0) {
            result.put("ratio", ((float) pushService.getTotalPush() - failedPushCount) / pushService.getTotalPush());
        } else {
            result.put("ratio", 0);
        }

        JSONArray dataArray = new JSONArray();
        if (detail) {
            for (PushService.Receiver.AckEntry entry : failedPushes) {
                try {
                    dataArray.add(new String(entry.origin.getData(), "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    dataArray.add("[encoding failure]");
                }
            }
            result.put("data", dataArray);
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
    public JSONObject metrics(HttpServletRequest request) {

        JSONObject result = new JSONObject();

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
    public JSONObject getResponsibleServer4Service(@RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
                                                   @RequestParam String serviceName) {

        Service service = serviceManager.getService(namespaceId, serviceName);

        if (service == null) {
            throw new IllegalArgumentException("service not found");
        }

        JSONObject result = new JSONObject();

        result.put("responsibleServer", distroMapper.mapSrv(serviceName));

        return result;
    }

    @GetMapping("/distro/status")
    public JSONObject distroStatus(@RequestParam(defaultValue = "view") String action) {

        JSONObject result = new JSONObject();

        if (StringUtils.equals(SwitchEntry.ACTION_VIEW, action)) {
            result.put("status", memberManager.allMembers());
            return result;
        }

        return result;
    }

    @GetMapping("/servers")
    public JSONObject getHealthyServerList(@RequestParam(required = false) boolean healthy) {

        JSONObject result = new JSONObject();
        if (healthy) {
            result.put("servers", memberManager.allMembers().stream()
            .filter(member -> member.getState() == NodeState.UP).collect(ArrayList::new,
                            ArrayList::add, ArrayList::addAll));
        } else {
            result.put("servers", memberManager.allMembers());
        }

        return result;
    }

    @PutMapping("/log")
    public String setLogLevel(@RequestParam String logName, @RequestParam String logLevel) {
        Loggers.setLogLevel(logName, logLevel);
        return "ok";
    }

    @RequestMapping(value = "/cluster/state", method = RequestMethod.GET)
    public JSONObject getClusterStates() {

        RaftPeer peer = serviceManager.getMySelfClusterState();

        return JSON.parseObject(JSON.toJSONString(peer));

    }
}
