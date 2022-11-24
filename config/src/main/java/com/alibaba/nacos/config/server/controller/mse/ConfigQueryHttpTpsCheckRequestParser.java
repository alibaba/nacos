package com.alibaba.nacos.config.server.controller.mse;

import com.alibaba.nacos.config.server.control.ConfigGroupKey;
import com.alibaba.nacos.config.server.control.ConfigGroupMonitorKey;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.core.control.http.HttpTpsCheckRequestParser;
import com.alibaba.nacos.plugin.control.tps.key.MonitorKey;
import com.alibaba.nacos.plugin.control.tps.mse.MseTpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

@Component
public class ConfigQueryHttpTpsCheckRequestParser extends HttpTpsCheckRequestParser {
    
    @Override
    public TpsCheckRequest parse(HttpServletRequest httpServletRequest) {
        
        MseTpsCheckRequest tpsCheckRequest = new MseTpsCheckRequest();
        tpsCheckRequest.setPointName(getPointName());
        List<MonitorKey> monitorTypeList = new ArrayList();
        String dataId = httpServletRequest.getParameter("dataId");
        String group = httpServletRequest.getParameter("group");
        String tenant = httpServletRequest.getParameter("tenant");
        monitorTypeList.add(new ConfigGroupKey(dataId, group, tenant));
        monitorTypeList.add(new ConfigGroupMonitorKey(group));
        tpsCheckRequest.setMonitorKeys(monitorTypeList);
        String clientIp = RequestUtil.getRemoteIp(httpServletRequest);
        tpsCheckRequest.setClientIp(clientIp);
        return tpsCheckRequest;
    }
    
    @Override
    public String getPointName() {
        return "ConfigQuery";
    }
    
    @Override
    public String getName() {
        return "ConfigQuery";
    }
}
