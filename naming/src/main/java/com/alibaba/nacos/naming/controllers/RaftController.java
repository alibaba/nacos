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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.RecordListener;
import com.alibaba.nacos.naming.consistency.persistent.ClusterVersionJudgement;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftConsistencyServiceImpl;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftCore;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftPeer;
import com.alibaba.nacos.naming.core.Instances;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.misc.NetUtils;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Methods for Raft consistency protocol. These methods should only be invoked by Nacos server itself.
 *
 * @author nkorange
 * @since 1.0.0
 * @deprecated will remove in 1.4.x
 */
@Deprecated
@RestController
@RequestMapping({UtilsAndCommons.NACOS_NAMING_CONTEXT + UtilsAndCommons.NACOS_NAMING_RAFT_CONTEXT,
        UtilsAndCommons.NACOS_SERVER_CONTEXT + UtilsAndCommons.NACOS_NAMING_CONTEXT
                + UtilsAndCommons.NACOS_NAMING_RAFT_CONTEXT})
public class RaftController {
    
    private final RaftConsistencyServiceImpl raftConsistencyService;
    
    private final ServiceManager serviceManager;
    
    private final RaftCore raftCore;
    
    private final ClusterVersionJudgement versionJudgement;
    
    public RaftController(RaftConsistencyServiceImpl raftConsistencyService, ServiceManager serviceManager,
            RaftCore raftCore, ClusterVersionJudgement versionJudgement) {
        this.raftConsistencyService = raftConsistencyService;
        this.serviceManager = serviceManager;
        this.raftCore = raftCore;
        this.versionJudgement = versionJudgement;
    }
    
    /**
     * Raft vote api.
     *
     * @param request  http request
     * @param response http response
     * @return peer information
     * @throws Exception exception
     */
    @PostMapping("/vote")
    public JsonNode vote(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (versionJudgement.allMemberIsNewVersion()) {
            throw new IllegalStateException("old raft protocol already stop");
        }
        RaftPeer peer = raftCore.receivedVote(JacksonUtils.toObj(WebUtils.required(request, "vote"), RaftPeer.class));
        
        return JacksonUtils.transferToJsonNode(peer);
    }
    
    /**
     * Beat api.
     *
     * @param request  http request
     * @param response http response
     * @return peer information
     * @throws Exception exception
     */
    @PostMapping("/beat")
    public JsonNode beat(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (versionJudgement.allMemberIsNewVersion()) {
            throw new IllegalStateException("old raft protocol already stop");
        }
        String entity = new String(IoUtils.tryDecompress(request.getInputStream()), StandardCharsets.UTF_8);
        String value = URLDecoder.decode(entity, "UTF-8");
        value = URLDecoder.decode(value, "UTF-8");
        
        JsonNode json = JacksonUtils.toObj(value);
        
        RaftPeer peer = raftCore.receivedBeat(JacksonUtils.toObj(json.get("beat").asText()));
        
        return JacksonUtils.transferToJsonNode(peer);
    }
    
    /**
     * Get peer information.
     *
     * @param request  http request
     * @param response http response
     * @return peer information
     */
    @GetMapping("/peer")
    public JsonNode getPeer(HttpServletRequest request, HttpServletResponse response) {
        if (versionJudgement.allMemberIsNewVersion()) {
            throw new IllegalStateException("old raft protocol already stop");
        }
        List<RaftPeer> peers = raftCore.getPeers();
        RaftPeer peer = null;
        
        for (RaftPeer peer1 : peers) {
            if (StringUtils.equals(peer1.ip, NetUtils.localServer())) {
                peer = peer1;
            }
        }
        
        if (peer == null) {
            peer = new RaftPeer();
            peer.ip = NetUtils.localServer();
        }
        
        return JacksonUtils.transferToJsonNode(peer);
    }
    
    /**
     * Datum reload request.
     *
     * @param request  http request
     * @param response http response
     * @return 'ok' if success
     * @throws Exception exception
     */
    @PutMapping("/datum/reload")
    public String reloadDatum(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (versionJudgement.allMemberIsNewVersion()) {
            throw new IllegalStateException("old raft protocol already stop");
        }
        String key = WebUtils.required(request, "key");
        raftCore.loadDatum(key);
        return "ok";
    }
    
    /**
     * Publish datum.
     *
     * @param request  http request
     * @param response http response
     * @return 'ok' if success
     * @throws Exception exception
     */
    @PostMapping("/datum")
    public String publish(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (versionJudgement.allMemberIsNewVersion()) {
            throw new IllegalStateException("old raft protocol already stop");
        }
        response.setHeader("Content-Type", "application/json; charset=" + getAcceptEncoding(request));
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Content-Encode", "gzip");
        
        String entity = IoUtils.toString(request.getInputStream(), "UTF-8");
        String value = URLDecoder.decode(entity, "UTF-8");
        JsonNode json = JacksonUtils.toObj(value);
        
        String key = json.get("key").asText();
        if (KeyBuilder.matchInstanceListKey(key)) {
            raftConsistencyService.put(key, JacksonUtils.toObj(json.get("value").toString(), Instances.class));
            return "ok";
        }
        
        if (KeyBuilder.matchSwitchKey(key)) {
            raftConsistencyService.put(key, JacksonUtils.toObj(json.get("value").toString(), SwitchDomain.class));
            return "ok";
        }
        
        if (KeyBuilder.matchServiceMetaKey(key)) {
            raftConsistencyService.put(key, JacksonUtils.toObj(json.get("value").toString(), Service.class));
            return "ok";
        }
        
        throw new NacosException(NacosException.INVALID_PARAM, "unknown type publish key: " + key);
    }
    
    /**
     * Remove datum.
     *
     * @param request  http request
     * @param response http response
     * @return 'ok' if success
     * @throws Exception exception
     */
    @DeleteMapping("/datum")
    public String delete(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (versionJudgement.allMemberIsNewVersion()) {
            throw new IllegalStateException("old raft protocol already stop");
        }
        response.setHeader("Content-Type", "application/json; charset=" + getAcceptEncoding(request));
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Content-Encode", "gzip");
        raftConsistencyService.remove(WebUtils.required(request, "key"));
        return "ok";
    }
    
    /**
     * Get datum.
     *
     * @param request  http request
     * @param response http response
     * @return datum
     * @throws Exception exception
     */
    @GetMapping("/datum")
    public String get(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (versionJudgement.allMemberIsNewVersion()) {
            throw new IllegalStateException("old raft protocol already stop");
        }
        response.setHeader("Content-Type", "application/json; charset=" + getAcceptEncoding(request));
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Content-Encode", "gzip");
        String keysString = WebUtils.required(request, "keys");
        keysString = URLDecoder.decode(keysString, "UTF-8");
        String[] keys = keysString.split(",");
        List<Datum> datums = new ArrayList<Datum>();
        
        for (String key : keys) {
            Datum datum = raftCore.getDatum(key);
            datums.add(datum);
        }
        
        return JacksonUtils.toJson(datums);
    }
    
    /**
     * Get state of raft peer.
     *
     * @param request  http request
     * @param response http response
     * @return datum
     * @throws Exception exception
     */
    @GetMapping("/state")
    public JsonNode state(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (versionJudgement.allMemberIsNewVersion()) {
            throw new IllegalStateException("old raft protocol already stop");
        }
        response.setHeader("Content-Type", "application/json; charset=" + getAcceptEncoding(request));
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Content-Encode", "gzip");
        
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        result.put("services", serviceManager.getServiceCount());
        result.replace("peers", JacksonUtils.transferToJsonNode(raftCore.getPeers()));
        
        return result;
    }
    
    /**
     * Commit publish datum.
     *
     * @param request  http request
     * @param response http response
     * @return 'ok' if success
     * @throws Exception exception
     */
    @PostMapping("/datum/commit")
    public String onPublish(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (versionJudgement.allMemberIsNewVersion()) {
            throw new IllegalStateException("old raft protocol already stop");
        }
        response.setHeader("Content-Type", "application/json; charset=" + getAcceptEncoding(request));
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Content-Encode", "gzip");
        
        String entity = IoUtils.toString(request.getInputStream(), "UTF-8");
        String value = URLDecoder.decode(entity, "UTF-8");
        
        JsonNode jsonObject = JacksonUtils.toObj(value);
        String key = "key";
        
        RaftPeer source = JacksonUtils.toObj(jsonObject.get("source").toString(), RaftPeer.class);
        JsonNode datumJson = jsonObject.get("datum");
        
        Datum datum = null;
        if (KeyBuilder.matchInstanceListKey(datumJson.get(key).asText())) {
            datum = JacksonUtils.toObj(jsonObject.get("datum").toString(), new TypeReference<Datum<Instances>>() {
            });
        } else if (KeyBuilder.matchSwitchKey(datumJson.get(key).asText())) {
            datum = JacksonUtils.toObj(jsonObject.get("datum").toString(), new TypeReference<Datum<SwitchDomain>>() {
            });
        } else if (KeyBuilder.matchServiceMetaKey(datumJson.get(key).asText())) {
            datum = JacksonUtils.toObj(jsonObject.get("datum").toString(), new TypeReference<Datum<Service>>() {
            });
        }
        
        raftConsistencyService.onPut(datum, source);
        return "ok";
    }
    
    /**
     * Commit delete datum.
     *
     * @param request  http request
     * @param response http response
     * @return 'ok' if success
     * @throws Exception exception
     */
    @DeleteMapping("/datum/commit")
    public String onDelete(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (versionJudgement.allMemberIsNewVersion()) {
            throw new IllegalStateException("old raft protocol already stop");
        }
        response.setHeader("Content-Type", "application/json; charset=" + getAcceptEncoding(request));
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Content-Encode", "gzip");
        
        String entity = IoUtils.toString(request.getInputStream(), "UTF-8");
        String value = URLDecoder.decode(entity, "UTF-8");
        value = URLDecoder.decode(value, "UTF-8");
        
        JsonNode jsonObject = JacksonUtils.toObj(value);
        
        Datum datum = JacksonUtils.toObj(jsonObject.get("datum").toString(), Datum.class);
        RaftPeer source = JacksonUtils.toObj(jsonObject.get("source").toString(), RaftPeer.class);
        
        raftConsistencyService.onRemove(datum, source);
        return "ok";
    }
    
    /**
     * Elect leader api.
     *
     * @param request  http request
     * @param response http response
     * @return leader peer information
     */
    @GetMapping("/leader")
    public JsonNode getLeader(HttpServletRequest request, HttpServletResponse response) {
        if (versionJudgement.allMemberIsNewVersion()) {
            throw new IllegalStateException("old raft protocol already stop");
        }
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        result.put("leader", JacksonUtils.toJson(raftCore.getLeader()));
        return result;
    }
    
    /**
     * Get all listeners.
     *
     * @param request  http request
     * @param response http response
     * @return all listener information
     */
    @GetMapping("/listeners")
    public JsonNode getAllListeners(HttpServletRequest request, HttpServletResponse response) {
        if (versionJudgement.allMemberIsNewVersion()) {
            throw new IllegalStateException("old raft protocol already stop");
        }
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        Map<String, ConcurrentHashSet<RecordListener>> listeners = raftCore.getListeners();
        
        ArrayNode listenerArray = JacksonUtils.createEmptyArrayNode();
        for (String key : listeners.keySet()) {
            listenerArray.add(key);
        }
        result.replace("listeners", listenerArray);
        
        return result;
    }
    
    public static String getAcceptEncoding(HttpServletRequest req) {
        String encode = StringUtils.defaultIfEmpty(req.getHeader("Accept-Charset"), "UTF-8");
        encode = encode.contains(",") ? encode.substring(0, encode.indexOf(",")) : encode;
        return encode.contains(";") ? encode.substring(0, encode.indexOf(";")) : encode;
    }
}
