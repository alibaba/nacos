package com.alibaba.nacos.common.paramcheck;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * Extract param from http-request.
 *
 * @author sunrisea
 */
public abstract class HttpParamExtractor implements ParamExtractor<HttpServletRequest> {

    private static final String SPLITTER = "@@";
    private static final String NACOS_SERVER_CONTEXT = "/nacos";
    private final List<String> targetRequestList;

    public HttpParamExtractor() {
        targetRequestList = new ArrayList<>();
        init();
    }

    public abstract void init();

    @Override
    public List<String> getTargetRequestList() {
        return targetRequestList;
    }

    @Override
    public abstract void extractParamAndCheck(HttpServletRequest request) throws Exception;

    public void addTargetRequest(String uri, String method) {
        targetRequestList.add(NACOS_SERVER_CONTEXT + uri + SPLITTER + method);
    }

    public void addDefaultTargetRequest(String module) {
        targetRequestList.add("default" + SPLITTER + module);
    }
}
