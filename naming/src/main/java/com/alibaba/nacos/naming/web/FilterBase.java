package com.alibaba.nacos.naming.web;

import com.alibaba.nacos.naming.controllers.*;
import com.alibaba.nacos.naming.exception.NacosException;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author nkorange
 * @since 1.0.0
 */
@Component
public class FilterBase {

    private ConcurrentMap<String, Method> methodCache = new
        ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        initClassMethod(InstanceController.class);
        initClassMethod(ServiceController.class);
        initClassMethod(ClusterController.class);
        initClassMethod(CatalogController.class);
        initClassMethod(HealthController.class);
        initClassMethod(RaftController.class);
        initClassMethod(PartitionController.class);
        initClassMethod(OperatorController.class);
    }

    public Method getMethod(String httpMethod, String path) {
        String key = httpMethod + "-->" + path;
        return methodCache.get(key);
    }

    private void initClassMethod(Class<?> clazz) {
        RequestMapping requestMapping = clazz.getAnnotation(RequestMapping.class);
        String classPath = requestMapping.value()[0];
        for (Method method : clazz.getMethods()) {
            requestMapping = method.getAnnotation(RequestMapping.class);
            RequestMethod[] requestMethods = requestMapping.method();
            if (requestMethods.length == 0) {
                requestMethods = new RequestMethod[1];
                requestMethods[0] = RequestMethod.GET;
            }
            for (String methodPath : requestMapping.value()) {
                methodCache.put(requestMethods[0].name() + "-->" + classPath + methodPath, method);
            }
        }
    }


    public Class<?> mapClass(String path) throws NacosException {

        if (path.startsWith(UtilsAndCommons.NACOS_NAMING_CONTEXT + UtilsAndCommons.NACOS_NAMING_INSTANCE_CONTEXT)) {
            return InstanceController.class;
        }

        if (path.startsWith(UtilsAndCommons.NACOS_NAMING_CONTEXT + UtilsAndCommons.NACOS_NAMING_SERVICE_CONTEXT)) {
            return ServiceController.class;
        }

        if (path.startsWith(UtilsAndCommons.NACOS_NAMING_CONTEXT + UtilsAndCommons.NACOS_NAMING_CLUSTER_CONTEXT)) {
            return ClusterController.class;
        }

        if (path.startsWith(UtilsAndCommons.NACOS_NAMING_CONTEXT + UtilsAndCommons.NACOS_NAMING_OPERATOR_CONTEXT)) {
            return OperatorController.class;
        }

        if (path.startsWith(UtilsAndCommons.NACOS_NAMING_CONTEXT + UtilsAndCommons.NACOS_NAMING_CATALOG_CONTEXT)) {
            return CatalogController.class;
        }

        if (path.startsWith(UtilsAndCommons.NACOS_NAMING_CONTEXT + UtilsAndCommons.NACOS_NAMING_HEALTH_CONTEXT)) {
            return HealthController.class;
        }

        if (path.startsWith(UtilsAndCommons.NACOS_NAMING_CONTEXT + UtilsAndCommons.NACOS_NAMING_RAFT_CONTEXT)) {
            return RaftController.class;
        }

        if (path.startsWith(UtilsAndCommons.NACOS_NAMING_CONTEXT + UtilsAndCommons.NACOS_NAMING_PARTITION_CONTEXT)) {
            return PartitionController.class;
        }

        throw new NacosException(NacosException.NOT_FOUND, "no matched controller found!");

    }

    public String getMethodName(String path) throws Exception {
        String target = path.substring(path.lastIndexOf("/") + 1).trim();

        if (StringUtils.isEmpty(target)) {
            throw new IllegalArgumentException("URL target required");
        }

        return target;
    }

    public ConcurrentMap<String, Method> getMethodCache() {
        return methodCache;
    }
}
