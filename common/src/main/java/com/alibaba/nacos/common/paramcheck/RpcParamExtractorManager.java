package com.alibaba.nacos.common.paramcheck;

import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.common.spi.NacosServiceLoader;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HttpParamExtractor Manager.
 *
 * @author sunrisea
 */
public class RpcParamExtractorManager {

    private static final RpcParamExtractorManager INSTANCE = new RpcParamExtractorManager();
    private static final RpcParamExtractor DEFAULT_EXTRACTOR = new RpcParamExtractor() {
        @Override
        public void init() {
        }

        @Override
        public void extractParamAndCheck(Request params) throws Exception {
        }
    };
    private final Map<String, RpcParamExtractor> extractorMap = new ConcurrentHashMap<>(32);

    private RpcParamExtractorManager() {
        Collection<RpcParamExtractor> extractors = NacosServiceLoader.load(RpcParamExtractor.class);
        for (RpcParamExtractor extractor : extractors) {
            List<String> targetrequestlist = extractor.getTargetRequestList();
            for (String targetRequest : targetrequestlist) {
                extractorMap.put(targetRequest, extractor);
            }
        }
    }

    public static RpcParamExtractorManager getInstance() {
        return INSTANCE;
    }

    public RpcParamExtractor getExtractor(String type) {
        RpcParamExtractor extractor = extractorMap.get(type);
        if (extractor == null) {
            extractor = DEFAULT_EXTRACTOR;
        }
        return extractor;
    }


}
