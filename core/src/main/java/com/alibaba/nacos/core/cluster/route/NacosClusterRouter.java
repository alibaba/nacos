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

package com.alibaba.nacos.core.cluster.route;

import com.alibaba.nacos.common.model.ResResult;
import com.alibaba.nacos.core.cluster.Node;
import com.alibaba.nacos.core.cluster.NodeManager;
import com.alibaba.nacos.core.cluster.ServerNode;
import com.alibaba.nacos.core.utils.Commons;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.utils.ResResultUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@RestController
@RequestMapping(Commons.NACOS_CORE_CONTEXT + "/cluster")
public class NacosClusterRouter {

    @Autowired
    private NodeManager nodeManager;

    @GetMapping(value = "/self")
    public ResResult<Node> self() {
        return ResResultUtils.success(nodeManager.self());
    }

    @GetMapping(value = "/nodes")
    public ResResult<Collection<Node>> listAllNode() {
        return ResResultUtils.success(nodeManager.allNodes());
    }

    // The client can get all the nacos node information in the current
    // cluster according to this interface

    @GetMapping(value = "/simple/nodes")
    public ResResult<Collection<String>> listSimpleNodes() {
        List<String> ips = nodeManager.allNodes().stream()
                .map(Node::address)
                .collect(Collectors.toList());
        return ResResultUtils.success(ips);
    }

    @GetMapping("/server/health")
    public ResResult<String> getHealth() {
        return ResResultUtils.success("");
    }

    @PostMapping("/server/report")
    public ResResult<Boolean> report(@RequestBody ResResult<ServerNode> resResult) {

        final ServerNode node = resResult.getData();

        if (!node.check()) {
            return ResResultUtils.failed("Node information is illegal");
        }

        Loggers.CORE.debug("node state report, receive info : {}", node);
        nodeManager.update(node);
        return ResResultUtils.success(true);
    }


}
