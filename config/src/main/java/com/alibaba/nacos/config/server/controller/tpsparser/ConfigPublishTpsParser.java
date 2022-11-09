package com.alibaba.nacos.config.server.controller.tpsparser;

import com.alibaba.nacos.config.server.control.ConfigGroupKey;
import com.alibaba.nacos.config.server.control.ConfigGroupMonitorKey;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.core.control.http.HttpTpsCheckRequestParser;
import com.alibaba.nacos.plugin.control.tps.key.MonitorKey;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

@Component
public class ConfigPublishTpsParser extends HttpTpsCheckRequestParser {
    
    @Override
    public TpsCheckRequest parse(HttpServletRequest httpServletRequest) {
        String dataId = httpServletRequest.getParameter("dataId");
        String group = httpServletRequest.getParameter("group");
        String tenant = httpServletRequest.getParameter("tenant");
        String clientIp = RequestUtil.getRemoteIp(httpServletRequest);
        TpsCheckRequest tpsCheckRequest = new TpsCheckRequest();
        tpsCheckRequest.setPointName(getPointName());
        List<MonitorKey> monitorTypeList = new ArrayList();
        monitorTypeList.add(new ConfigGroupKey(dataId, group, tenant));
        monitorTypeList.add(new ConfigGroupMonitorKey(group));
        tpsCheckRequest.setMonitorKeys(monitorTypeList);
        tpsCheckRequest.setClientIp(clientIp);
        return tpsCheckRequest;
    }
    
    @Override
    public String getPointName() {
        return "ConfigPublish";
    }
    
    @Override
    public String getName() {
        return "ConfigPublish";
    }
}
