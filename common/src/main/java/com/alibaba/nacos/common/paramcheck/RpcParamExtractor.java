package com.alibaba.nacos.common.paramcheck;

import com.alibaba.nacos.api.remote.request.Request;

import java.util.ArrayList;
import java.util.List;


/**
 * Abstract ParamExtractor class for rpc request.
 *
 * @author sunrisea
 */
public abstract class RpcParamExtractor implements ParamExtractor<Request> {

    private final List<String> targetrequestlist;

    public RpcParamExtractor() {
        targetrequestlist = new ArrayList<>();
        init();
    }

    public abstract void init();

    @Override
    public List<String> getTargetRequestList() {
        return targetrequestlist;
    }

    @Override
    public abstract void extractParamAndCheck(Request request) throws Exception;

    public void addTargetRequest(String type) {
        targetrequestlist.add(type);
    }
}
