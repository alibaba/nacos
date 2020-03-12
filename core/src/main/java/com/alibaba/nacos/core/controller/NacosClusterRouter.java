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

package com.alibaba.nacos.core.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberManager;
import com.alibaba.nacos.core.distributed.id.IdGeneratorManager;
import com.alibaba.nacos.core.utils.Commons;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.utils.RestResultUtils;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@RestController
@RequestMapping(Commons.NACOS_CORE_CONTEXT + "/cluster")
public class NacosClusterRouter {

    @Autowired
    private MemberManager memberManager;

    @Autowired
    private IdGeneratorManager idGeneratorManager;

    @GetMapping(value = "/self")
    public RestResult<Member> self() {
        return RestResultUtils.success(memberManager.self());
    }

    @GetMapping(value = "/nodes")
    public RestResult<Collection<Member>> listAllNode() {
        return RestResultUtils.success(memberManager.allMembers());
    }

    // The client can get all the nacos node information in the current
    // cluster according to this interface

    @GetMapping(value = "/simple/nodes")
    public RestResult<Collection<String>> listSimpleNodes() {
        List<String> ips = memberManager.allMembers().stream()
                .map(Member::address)
                .collect(Collectors.toList());
        return RestResultUtils.success(ips);
    }

    @GetMapping("/server/health")
    public RestResult<String> getHealth() {
        return RestResultUtils.success("");
    }

    @PostMapping("/server/report")
    public RestResult<String> report(@RequestBody RestResult<Member> restResult, @RequestParam(value = "sync") boolean sync) {

        final Member node = restResult.getData();

        if (!node.check()) {
            return RestResultUtils.failedWithData("Node information is illegal");
        }

        Loggers.CLUSTER.debug("node state report, receive info : {}", node);
        memberManager.update(node);

        String data = "";

        if (sync) {
            data = JSON.toJSONString(memberManager.allMembers());
        }

        return RestResultUtils.success(data);
    }

    @PostMapping("/server/leave")
    public RestResult<Boolean> memberLeave(@RequestBody RestResult<Collection<Member>> params) {
        memberManager.memberLeave(params.getData());
        return RestResultUtils.success();
    }

    @GetMapping("/sys/idGeneratorInfo")
    public RestResult<Map<String, Map<Object, Object>>> idGeneratorInfo() {
        return RestResultUtils.success(idGeneratorManager.idGeneratorInfo());
    }

}
