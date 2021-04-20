package com.alibaba.nacos.naming.remote.rpc.filter;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.core.remote.AbstractRequestFilter;
import com.alibaba.nacos.naming.core.v2.upgrade.UpgradeJudgement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GrpcRequestFilter extends AbstractRequestFilter {
    
    @Autowired
    private UpgradeJudgement upgradeJudgement;
    
    @Override
    protected Response filter(Request request, RequestMeta meta, Class handlerClazz) throws NacosException {
        if (!upgradeJudgement.isUseGrpcFeatures()) {
            Response response = getDefaultResponseInstance(handlerClazz);
            response.setErrorInfo(NacosException.SERVER_ERROR, "Nacos cluster is running with 1.X mode, can't accept gRPC request temporarily. Please check the server status or close Double write to force open 2.0 mode. Detail https://nacos.io/en-us/docs/2.0.0-upgrading.html.");
            return response;
        }
        return null;
    }
}
