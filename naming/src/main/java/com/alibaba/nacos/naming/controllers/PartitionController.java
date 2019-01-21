package com.alibaba.nacos.naming.controllers;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.util.IoUtils;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.ephemeral.partition.PartitionConsistencyServiceImpl;
import com.alibaba.nacos.naming.consistency.ephemeral.partition.Serializer;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

/**
 * Restful methods for Partition protocol.
 *
 * @author nkorange
 * @since 1.0.0
 */
@RestController
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/partition")
public class PartitionController {

    @Autowired
    private Serializer serializer;

    @Autowired
    private PartitionConsistencyServiceImpl consistencyService;

    @RequestMapping("/onSync")
    public String onSync(HttpServletRequest request, HttpServletResponse response) throws Exception {
        byte[] data = IoUtils.tryDecompress(request.getInputStream());
        Map<String, Object> dataMap = serializer.deserialize(data, Object.class);
        for (String key : dataMap.keySet()) {
            if (KeyBuilder.matchEphemeralInstanceListKey(key)) {
                List<Instance> list = (List<Instance>) dataMap.get(key);
                consistencyService.onPut(key, list);
            }
        }
        return "ok";
    }
}
