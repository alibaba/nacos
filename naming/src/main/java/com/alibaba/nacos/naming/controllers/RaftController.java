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
import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.RecordListener;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftConsistencyServiceImpl;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftCore;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftPeer;
import com.alibaba.nacos.naming.core.Instances;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.naming.misc.NetUtils;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.web.NeedAuth;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Methods for Raft consistency protocol. These methods should only be invoked by Nacos server itself.
 *
 * @author nkorange
 * @since 1.0.0
 */
@RestController
@RequestMapping({UtilsAndCommons.NACOS_NAMING_CONTEXT + "/raft",
    UtilsAndCommons.NACOS_SERVER_CONTEXT + UtilsAndCommons.NACOS_NAMING_CONTEXT + "/raft"})
public class RaftController {

    @Autowired
    private RaftConsistencyServiceImpl raftConsistencyService;

    @Autowired
    private ServiceManager serviceManager;

    @Autowired
    private RaftCore raftCore;

    @NeedAuth
    @PostMapping("/vote")
    public JSONObject vote(@RequestParam String vote) {

        RaftPeer peer = raftCore.receivedVote(
            JSON.parseObject(vote, RaftPeer.class));

        return JSON.parseObject(JSON.toJSONString(peer));
    }

    @NeedAuth
    @PostMapping("/beat")
    public JSONObject beat(@RequestBody JSONObject json) throws Exception {

        JSONObject beat = json.getJSONObject("beat");

        RaftPeer peer = raftCore.receivedBeat(beat);

        return JSON.parseObject(JSON.toJSONString(peer));
    }

    @NeedAuth
    @GetMapping("/peer")
    public JSONObject getPeer() {
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

        return JSON.parseObject(JSON.toJSONString(peer));
    }

    @NeedAuth
    @PutMapping("/datum/reload")
    public String reloadDatum(@RequestParam String key) {
        raftCore.loadDatum(key);
        return "ok";
    }

    @NeedAuth
    @PostMapping("/datum")
    public String publish(@RequestBody JSONObject json, HttpServletRequest request, HttpServletResponse response) throws Exception {

        response.setHeader("Content-Type", "application/json; charset=" + getAcceptEncoding(request));
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Content-Encode", "gzip");

        String key = json.getString("key");
        if (KeyBuilder.matchInstanceListKey(key)) {
            raftConsistencyService.put(key, JSON.parseObject(json.getString("value"), Instances.class));
            return "ok";
        }

        if (KeyBuilder.matchSwitchKey(key)) {
            raftConsistencyService.put(key, JSON.parseObject(json.getString("value"), SwitchDomain.class));
            return "ok";
        }

        if (KeyBuilder.matchServiceMetaKey(key)) {
            raftConsistencyService.put(key, JSON.parseObject(json.getString("value"), Service.class));
            return "ok";
        }

        throw new NacosException(NacosException.INVALID_PARAM, "unknown type publish key: " + key);
    }

    @NeedAuth
    @DeleteMapping("/datum")
    public String delete(@RequestParam String key, HttpServletRequest request, HttpServletResponse response) throws Exception {

        response.setHeader("Content-Type", "application/json; charset=" + getAcceptEncoding(request));
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Content-Encode", "gzip");

        raftConsistencyService.remove(key);
        return "ok";
    }

    @NeedAuth
    @GetMapping("/datum")
    public String get(@RequestParam(name = "keys") String keysString,HttpServletRequest request, HttpServletResponse response) throws Exception {

        response.setHeader("Content-Type", "application/json; charset=" + getAcceptEncoding(request));
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Content-Encode", "gzip");

        keysString = URLDecoder.decode(keysString, "UTF-8");
        String[] keys = keysString.split(",");
        List<Datum> datums = new ArrayList<Datum>();

        for (String key : keys) {
            Datum datum = raftCore.getDatum(key);
            datums.add(datum);
        }

        return JSON.toJSONString(datums);
    }

    @GetMapping("/state")
    public JSONObject state(HttpServletRequest request, HttpServletResponse response) {

        response.setHeader("Content-Type", "application/json; charset=" + getAcceptEncoding(request));
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Content-Encode", "gzip");

        JSONObject result = new JSONObject();
        result.put("services", serviceManager.getServiceCount());
        result.put("peers", raftCore.getPeers());

        return result;
    }

    @NeedAuth
    @PostMapping("/datum/commit")
    public String onPublish(@RequestBody JSONObject jsonObject ,HttpServletRequest request, HttpServletResponse response) throws Exception {

        response.setHeader("Content-Type", "application/json; charset=" + getAcceptEncoding(request));
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Content-Encode", "gzip");

        String key = "key";

        RaftPeer source = jsonObject.getObject("source", RaftPeer.class);
        JSONObject datumJson = jsonObject.getJSONObject("datum");

        Datum datum = null;
        if (KeyBuilder.matchInstanceListKey(datumJson.getString(key))) {
            datum = JSON.parseObject(jsonObject.getString("datum"), new TypeReference<Datum<Instances>>() {
            });
        } else if (KeyBuilder.matchSwitchKey(datumJson.getString(key))) {
            datum = JSON.parseObject(jsonObject.getString("datum"), new TypeReference<Datum<SwitchDomain>>() {
            });
        } else if (KeyBuilder.matchServiceMetaKey(datumJson.getString(key))) {
            datum = JSON.parseObject(jsonObject.getString("datum"), new TypeReference<Datum<Service>>() {
            });
        }

        raftConsistencyService.onPut(datum, source);
        return "ok";
    }

    @NeedAuth
    @DeleteMapping("/datum/commit")
    public String onDelete(@RequestBody JSONObject jsonObject,HttpServletRequest request, HttpServletResponse response) throws Exception {

        response.setHeader("Content-Type", "application/json; charset=" + getAcceptEncoding(request));
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Content-Encode", "gzip");

        Datum datum = jsonObject.getObject("datum", Datum.class);
        RaftPeer source = jsonObject.getObject("source", RaftPeer.class);

        raftConsistencyService.onRemove(datum, source);
        return "ok";
    }

    @GetMapping("/leader")
    public JSONObject getLeader() {

        JSONObject result = new JSONObject();
        result.put("leader", JSONObject.toJSONString(raftCore.getLeader()));
        return result;
    }

    @GetMapping("/listeners")
    public JSONObject getAllListeners() {

        JSONObject result = new JSONObject();
        Map<String, List<RecordListener>> listeners = raftCore.getListeners();

        JSONArray listenerArray = new JSONArray();
        listenerArray.addAll(listeners.keySet());

        result.put("listeners", listenerArray);

        return result;
    }

    private static String getAcceptEncoding(HttpServletRequest req) {
        String encode = StringUtils.defaultIfEmpty(req.getHeader("Accept-Charset"), "UTF-8");
        encode = encode.contains(",") ? encode.substring(0, encode.indexOf(",")) : encode;
        return encode.contains(";") ? encode.substring(0, encode.indexOf(";")) : encode;
    }
}
