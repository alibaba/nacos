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
package com.alibaba.nacos.naming.web;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.common.util.IoUtils;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.core.DomainsManager;
import com.alibaba.nacos.naming.core.VirtualClusterDomain;
import com.alibaba.nacos.naming.misc.NetUtils;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.raft.*;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author nacos
 */
@RestController
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/raft")
public class RaftCommands {

    @Autowired
    protected DomainsManager domainsManager;

    @NeedAuth
    @RequestMapping("/vote")
    public JSONObject vote(HttpServletRequest request, HttpServletResponse response) throws Exception {

        RaftPeer peer = RaftCore.MasterElection.receivedVote(
                JSON.parseObject(WebUtils.required(request, "vote"), RaftPeer.class));

        return JSON.parseObject(JSON.toJSONString(peer));
    }

    @NeedAuth
    @RequestMapping("/beat")
    public JSONObject beat(HttpServletRequest request, HttpServletResponse response) throws Exception {

        String entity = new String(IoUtils.tryDecompress(request.getInputStream()), "UTF-8");

        String value = Arrays.asList(entity).toArray(new String[1])[0];
        value = URLDecoder.decode(value, "UTF-8");

        JSONObject json = JSON.parseObject(value);
        JSONObject beat = JSON.parseObject(json.getString("beat"));

        RaftPeer peer = RaftCore.HeartBeat.receivedBeat(beat);

        return JSON.parseObject(JSON.toJSONString(peer));
    }

    @NeedAuth
    @RequestMapping("/getPeer")
    public JSONObject getPeer(HttpServletRequest request, HttpServletResponse response) {
        List<RaftPeer> peers = RaftCore.getPeers();
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
        value = URLDecoder.decode(value, "UTF-8");
        JSONObject json = JSON.parseObject(value);

        RaftCore.doSignalPublish(json.getString("key"), json.getString("value"), json.getBooleanValue("locked"));

        return "ok";
    }

    @NeedAuth
    @RequestMapping("/delete")
    public String delete(HttpServletRequest request, HttpServletResponse response) throws Exception {

        response.setHeader("Content-Type", "application/json; charset=" + getAcceptEncoding(request));
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Content-Encode", "gzip");
        RaftCore.signalDelete(WebUtils.required(request, "key"));
        return "ok";
    }

    @NeedAuth
    @RequestMapping("/get")
    public String get(HttpServletRequest request, HttpServletResponse response) throws Exception {

        response.setHeader("Content-Type", "application/json; charset=" + getAcceptEncoding(request));
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Content-Encode", "gzip");
        String keysString = WebUtils.required(request, "keys");
        keysString = URLDecoder.decode(keysString, "UTF-8");
        String[] keys = keysString.split(",");
        List<Datum> datums = new ArrayList<Datum>();

        for (String key : keys) {
            Datum datum = RaftCore.getDatum(key);
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
        result.put("peers", RaftCore.getPeers());

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
        value = URLDecoder.decode(value, "UTF-8");
        JSONObject jsonObject = JSON.parseObject(value);

        RaftCore.onPublish(jsonObject, jsonObject.getBoolean("increaseTerm"));
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
        value = URLDecoder.decode(value, "UTF-8");
        RaftCore.onDelete(JSON.parseObject(value));
        return "ok";
    }

    public void setDomainsManager(DomainsManager domainsManager) {
        this.domainsManager = domainsManager;
    }

    @RequestMapping("/getLeader")
    public JSONObject getLeader(HttpServletRequest request, HttpServletResponse response) {

        JSONObject result = new JSONObject();
        result.put("leader", JSONObject.toJSONString(RaftCore.getLeader()));
        return result;
    }

    @RequestMapping("/getAllListeners")
    public JSONObject getAllListeners(HttpServletRequest request, HttpServletResponse response) {

        JSONObject result = new JSONObject();
        List<RaftListener> listeners = RaftCore.getListeners();

        JSONArray listenerArray = new JSONArray();
        for (RaftListener listener : listeners) {
            if (listener instanceof VirtualClusterDomain) {
                listenerArray.add(((VirtualClusterDomain) listener).getName());
            }
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
