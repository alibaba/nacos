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
import com.alibaba.nacos.common.util.IoUtils;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.consistency.DataListener;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.persistent.raft.*;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.misc.NetUtils;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.web.NeedAuth;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * HTTP interfaces for Raft consistency protocol. These interfaces should only be invoked by Nacos server itself.
 *
 * @author nkorange
 * @since 1.0.0
 */
@RestController
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/raft")
public class RaftController {

    @Autowired
    private RaftConsistencyServiceImpl raftConsistencyService;

    @Autowired
    private ServiceManager domainsManager;

    @Autowired
    private RaftCore raftCore;

    @NeedAuth
    @RequestMapping("/vote")
    public JSONObject vote(HttpServletRequest request, HttpServletResponse response) throws Exception {

        RaftPeer peer = raftCore.receivedVote(
            JSON.parseObject(WebUtils.required(request, "vote"), RaftPeer.class));

        return JSON.parseObject(JSON.toJSONString(peer));
    }

    @NeedAuth
    @RequestMapping("/beat")
    public JSONObject beat(HttpServletRequest request, HttpServletResponse response) throws Exception {

        String entity = new String(IoUtils.tryDecompress(request.getInputStream()), "UTF-8");

        String value = Arrays.asList(entity).toArray(new String[1])[0];

        JSONObject json = JSON.parseObject(value);
        JSONObject beat = JSON.parseObject(json.getString("beat"));

        RaftPeer peer = raftCore.receivedBeat(beat);

        return JSON.parseObject(JSON.toJSONString(peer));
    }

    @NeedAuth
    @RequestMapping("/getPeer")
    public JSONObject getPeer(HttpServletRequest request, HttpServletResponse response) {
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
    @RequestMapping("/reloadDatum")
    public String reloadDatum(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String key = WebUtils.required(request, "key");
        RaftStore.load(key);
        return "ok";
    }

    @NeedAuth
    @RequestMapping("/publish")
    public String publish(HttpServletRequest request, HttpServletResponse response) throws Exception {

        response.setHeader("Content-Type", "application/json; charset=" + getAcceptEncoding(request));
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Content-Encode", "gzip");

        String entity = IOUtils.toString(request.getInputStream(), "UTF-8");

        String value = Arrays.asList(entity).toArray(new String[1])[0];
        JSONObject json = JSON.parseObject(value);

        raftConsistencyService.put(json.getString("key"), json.getString("value"));

        return "ok";
    }

    @NeedAuth
    @RequestMapping("/delete")
    public String delete(HttpServletRequest request, HttpServletResponse response) throws Exception {

        response.setHeader("Content-Type", "application/json; charset=" + getAcceptEncoding(request));
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Content-Encode", "gzip");
        raftConsistencyService.remove(WebUtils.required(request, "key"));
        return "ok";
    }

    @NeedAuth
    @RequestMapping("/get")
    public String get(HttpServletRequest request, HttpServletResponse response) throws Exception {

        response.setHeader("Content-Type", "application/json; charset=" + getAcceptEncoding(request));
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Content-Encode", "gzip");
        String keysString = WebUtils.required(request, "keys");
        String[] keys = keysString.split(",");
        List<Datum> datums = new ArrayList<Datum>();

        for (String key : keys) {
            Datum datum = raftCore.getDatum(key);
            datums.add(datum);
        }

        return JSON.toJSONString(datums);
    }

    @RequestMapping("/state")
    public JSONObject state(HttpServletRequest request, HttpServletResponse response) throws Exception {

        response.setHeader("Content-Type", "application/json; charset=" + getAcceptEncoding(request));
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Content-Encode", "gzip");

        JSONObject result = new JSONObject();
        result.put("doms", domainsManager.getDomCount());
        result.put("peers", raftCore.getPeers());

        return result;
    }

    @NeedAuth
    @RequestMapping("/onPublish")
    public String onPublish(HttpServletRequest request, HttpServletResponse response) throws Exception {

        response.setHeader("Content-Type", "application/json; charset=" + getAcceptEncoding(request));
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Content-Encode", "gzip");

        String entity = IOUtils.toString(request.getInputStream(), "UTF-8");

        String value = Arrays.asList(entity).toArray(new String[1])[0];
        JSONObject jsonObject = JSON.parseObject(value);

        Datum datum = JSON.parseObject(jsonObject.getString("datum"), Datum.class);
        RaftPeer source = JSON.parseObject(jsonObject.getString("source"), RaftPeer.class);

        raftConsistencyService.onPut(datum, source);
        return "ok";
    }

    @NeedAuth
    @RequestMapping("/onDelete")
    public String onDelete(HttpServletRequest request, HttpServletResponse response) throws Exception {

        response.setHeader("Content-Type", "application/json; charset=" + getAcceptEncoding(request));
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Content-Encode", "gzip");

        String entity = IOUtils.toString(request.getInputStream(), "UTF-8");
        String value = Arrays.asList(entity).toArray(new String[1])[0];
        JSONObject jsonObject = JSON.parseObject(value);

        Datum datum = JSON.parseObject(jsonObject.getString("datum"), Datum.class);
        RaftPeer source = JSON.parseObject(jsonObject.getString("source"), RaftPeer.class);

        raftConsistencyService.onRemove(datum, source);
        return "ok";
    }

    @RequestMapping("/getLeader")
    public JSONObject getLeader(HttpServletRequest request, HttpServletResponse response) {

        JSONObject result = new JSONObject();
        result.put("leader", JSONObject.toJSONString(raftCore.getLeader()));
        return result;
    }

    @RequestMapping("/getAllListeners")
    public JSONObject getAllListeners(HttpServletRequest request, HttpServletResponse response) {

        JSONObject result = new JSONObject();
        Map<String, List<DataListener>> listeners = raftCore.getListeners();

        JSONArray listenerArray = new JSONArray();
        for (String key : listeners.keySet()) {
                listenerArray.add(key);
        }
        result.put("listeners", listenerArray);

        return result;
    }

    public static String getAcceptEncoding(HttpServletRequest req) {
        String encode = StringUtils.defaultIfEmpty(req.getHeader("Accept-Charset"), "UTF-8");
        encode = encode.contains(",") ? encode.substring(0, encode.indexOf(",")) : encode;
        return encode.contains(";") ? encode.substring(0, encode.indexOf(";")) : encode;
    }
}
