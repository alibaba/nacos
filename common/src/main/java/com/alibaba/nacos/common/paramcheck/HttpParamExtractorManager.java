package com.alibaba.nacos.common.paramcheck;

import com.alibaba.nacos.common.spi.NacosServiceLoader;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HttpParamExtractor Manager.
 *
 * @author sunrisea
 */
public class HttpParamExtractorManager {

    private static final String SPLITTER = "@@";
    private static final HttpParamExtractorManager INSTANCE = new HttpParamExtractorManager();
    private static final HttpParamExtractor DEFAULT_EXTRACTOR = new HttpParamExtractor() {
        @Override
        public void init() {
        }

        @Override
        public void extractParamAndCheck(HttpServletRequest request) throws Exception {
        }
    };
    private final Map<String, HttpParamExtractor> extractorMap = new ConcurrentHashMap<>(32);

    private HttpParamExtractorManager() {
        Collection<HttpParamExtractor> extractors = NacosServiceLoader.load(HttpParamExtractor.class);
        for (HttpParamExtractor extractor : extractors) {
            List<String> targetrequestlist = extractor.getTargetRequestList();
            for (String targetrequest : targetrequestlist) {
                extractorMap.put(targetrequest, extractor);
            }
        }
    }

    public static HttpParamExtractorManager getInstance() {
        return INSTANCE;
    }

    public HttpParamExtractor getExtractor(String uri, String method, String module) {
        HttpParamExtractor extractor = extractorMap.get(uri + SPLITTER + method);
        if (extractor == null) {
            extractor = extractorMap.get("default" + SPLITTER + module);
        }
        return extractor == null ? DEFAULT_EXTRACTOR : extractor;
    }

}
