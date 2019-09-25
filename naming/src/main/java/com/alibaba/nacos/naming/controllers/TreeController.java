package com.alibaba.nacos.naming.controllers;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.weak.tree.TreeBasedConsistencyServiceImpl;
import com.alibaba.nacos.naming.consistency.weak.tree.TreePeer;
import com.alibaba.nacos.naming.core.Instances;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URLDecoder;

/**
 * @author satjd
 */
@RestController
@RequestMapping({UtilsAndCommons.NACOS_NAMING_CONTEXT + "/tree",
    UtilsAndCommons.NACOS_SERVER_CONTEXT + UtilsAndCommons.NACOS_NAMING_CONTEXT + "/tree"})
public class TreeController {

    @Autowired
    TreeBasedConsistencyServiceImpl treeBasedConsistencyService;

    @RequestMapping(value = "/datum/onPub", method = RequestMethod.POST)
    public String onPublish(HttpServletRequest request, HttpServletResponse response) throws Exception {
        response.setHeader("Content-Type", "application/json; charset=" + getAcceptEncoding(request));
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Content-Encode", "gzip");

        String entity = IOUtils.toString(request.getInputStream(), "UTF-8");
        String value = URLDecoder.decode(entity, "UTF-8");
        JSONObject jsonObject = JSON.parseObject(value);
        String key = "key";

        TreePeer source = JSON.parseObject(jsonObject.getString("source"), TreePeer.class);
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

        treeBasedConsistencyService.onPut(datum, source);
        return "ok";
    }

    @RequestMapping(value = "/datum/onPub/batch", method = RequestMethod.POST)
    public String onPublishBatch(HttpServletRequest request, HttpServletResponse response) throws Exception {
        response.setHeader("Content-Type", "application/json; charset=" + getAcceptEncoding(request));
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Content-Encode", "gzip");

        String entity = IOUtils.toString(request.getInputStream(), "UTF-8");
        String value = URLDecoder.decode(entity, "UTF-8");
        JSONObject jsonObject = JSON.parseObject(value);
        String key = "key";

        TreePeer source = JSON.parseObject(jsonObject.getString("source"), TreePeer.class);
        JSONArray datumArray = jsonObject.getJSONArray("datums");

        for (Object obj : datumArray) {
            JSONObject jObj = (JSONObject) obj;
            Datum datum = null;
            if (KeyBuilder.matchInstanceListKey(jObj.getString(key))) {
                datum = jObj.toJavaObject(new TypeReference<Datum<Instances>>() {
                });
            } else if (KeyBuilder.matchSwitchKey(jObj.getString(key))) {
                datum = jObj.toJavaObject(new TypeReference<Datum<SwitchDomain>>() {
                });
            } else if (KeyBuilder.matchServiceMetaKey(jObj.getString(key))) {
                datum = jObj.toJavaObject(new TypeReference<Datum<Service>>() {
                });
            }
            treeBasedConsistencyService.onPut(datum, source);
        }

        return "ok";
    }



    @RequestMapping(value = "/datum/onDel", method = RequestMethod.POST)
    public String delete(HttpServletRequest request, HttpServletResponse response) throws Exception {

        response.setHeader("Content-Type", "application/json; charset=" + getAcceptEncoding(request));
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Content-Encode", "gzip");

        String entity = IOUtils.toString(request.getInputStream(), "UTF-8");
        String value = URLDecoder.decode(entity, "UTF-8");
        value = URLDecoder.decode(value, "UTF-8");
        JSONObject jsonObject = JSON.parseObject(value);

        Datum datum = JSON.parseObject(jsonObject.getString("datum"), Datum.class);
        TreePeer source = JSON.parseObject(jsonObject.getString("source"), TreePeer.class);

        treeBasedConsistencyService.onRemove(datum,source);
        return "ok";
    }

    private static String getAcceptEncoding(HttpServletRequest req) {
        String encode = StringUtils.defaultIfEmpty(req.getHeader("Accept-Charset"), "UTF-8");
        encode = encode.contains(",") ? encode.substring(0, encode.indexOf(",")) : encode;
        return encode.contains(";") ? encode.substring(0, encode.indexOf(";")) : encode;
    }
}
