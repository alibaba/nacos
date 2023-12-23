package com.alibaba.nacos.core.remote.grpc.negotiator;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.remote.CommunicationType;
import com.alibaba.nacos.core.remote.grpc.negotiator.tls.ClusterDefaultTlsProtocolNegotiatorBuilder;
import com.alibaba.nacos.core.remote.grpc.negotiator.tls.SdkDefaultTlsProtocolNegotiatorBuilder;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.sys.env.EnvUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.alibaba.nacos.core.remote.grpc.negotiator.tls.ClusterDefaultTlsProtocolNegotiatorBuilder.CLUSTER_TYPE_DEFAULT_TLS;
import static com.alibaba.nacos.core.remote.grpc.negotiator.tls.SdkDefaultTlsProtocolNegotiatorBuilder.SDK_TYPE_DEFAULT_TLS;

/**
 * Manager for ProtocolNegotiatorBuilder instances, responsible for loading, managing, and providing
 * ProtocolNegotiatorBuilders.
 *
 * <p>{@code ProtocolNegotiatorBuilderManager} is a singleton class, and it initializes ProtocolNegotiatorBuilders
 * using the SPI mechanism. It also provides default ProtocolNegotiatorBuilders in case loading from SPI fails.
 * </p>
 *
 * <p>Usage:
 * <pre>{@code
 * ProtocolNegotiatorBuilderManager manager = ProtocolNegotiatorBuilderManager.getInstance();
 * NacosGrpcProtocolNegotiator negotiator = manager.get(CommunicationType.SDK);
 * }</pre>
 * </p>
 *
 * @author stone-98
 * @date 2023/12/23
 */
public class ProtocolNegotiatorBuilderManager {
    
    /**
     * Property key for configuring the ProtocolNegotiator type for cluster communication.
     */
    private static final String CLUSTER_TYPE_PROPERTY_KEY = "nacos.remote.cluster.server.rpc.protocol.negotiator.type";
    
    /**
     * Property key for configuring the ProtocolNegotiator type for SDK communication.
     */
    private static final String SDK_TYPE_PROPERTY_KEY = "nacos.remote.sdk.server.rpc.protocol.negotiator.type";
    
    /**
     * Singleton instance of ProtocolNegotiatorBuilderManager.
     */
    private static final ProtocolNegotiatorBuilderManager INSTANCE = new ProtocolNegotiatorBuilderManager();
    
    /**
     * Map to store ProtocolNegotiatorBuilders by their types.
     */
    private static final Map<String, ProtocolNegotiatorBuilder> BUILDER_MAP = new HashMap<>();
    
    /**
     * Map to store the actual ProtocolNegotiator types used for different CommunicationTypes.
     */
    private static final Map<CommunicationType, String> ACTUAL_TYPE_MAP = new HashMap<>();
    
    /**
     * Private constructor to enforce singleton pattern.
     */
    private ProtocolNegotiatorBuilderManager() {
        initActualTypeMap();
        try {
            initAllBuilders();
        } catch (Exception e) {
            Loggers.REMOTE.warn("Load ProtocolNegotiatorBuilder failed, use default ProtocolNegotiatorBuilder", e);
            initDefaultBuilder();
        }
    }
    
    /**
     * Initialize all ProtocolNegotiatorBuilders using the SPI mechanism.
     */
    private void initAllBuilders() {
        for (ProtocolNegotiatorBuilder each : NacosServiceLoader.load(ProtocolNegotiatorBuilder.class)) {
            BUILDER_MAP.put(each.type(), each);
            Loggers.REMOTE.info("Load ProtocolNegotiatorBuilder {} for type {}", each.getClass().getCanonicalName(),
                    each.type());
        }
    }
    
    /**
     * Initialize the mapping of CommunicationType to actual ProtocolNegotiator type from configuration properties.
     */
    private void initActualTypeMap() {
        ACTUAL_TYPE_MAP.put(CommunicationType.SDK, EnvUtil.getProperty(SDK_TYPE_PROPERTY_KEY, SDK_TYPE_DEFAULT_TLS));
        ACTUAL_TYPE_MAP.put(CommunicationType.CLUSTER,
                EnvUtil.getProperty(CLUSTER_TYPE_PROPERTY_KEY, CLUSTER_TYPE_DEFAULT_TLS));
    }
    
    /**
     * Initialize default ProtocolNegotiatorBuilders in case loading from SPI fails.
     */
    private void initDefaultBuilder() {
        BUILDER_MAP.put(SDK_TYPE_DEFAULT_TLS, new SdkDefaultTlsProtocolNegotiatorBuilder());
        BUILDER_MAP.put(CLUSTER_TYPE_PROPERTY_KEY, new ClusterDefaultTlsProtocolNegotiatorBuilder());
    }
    
    /**
     * Get the singleton instance of ProtocolNegotiatorBuilderManager.
     *
     * @return The singleton instance.
     */
    public static ProtocolNegotiatorBuilderManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * Get the ProtocolNegotiator for the specified CommunicationType.
     *
     * @param communicationType The CommunicationType for which the ProtocolNegotiator is requested.
     * @return The ProtocolNegotiator instance.
     */
    public NacosGrpcProtocolNegotiator get(CommunicationType communicationType) {
        String actualType = ACTUAL_TYPE_MAP.get(communicationType);
        if (StringUtils.isBlank(actualType)) {
            Loggers.REMOTE.warn("Not found actualType for communicationType {}.", communicationType);
            return null;
        }
        ProtocolNegotiatorBuilder builder = BUILDER_MAP.get(actualType);
        if (Objects.isNull(builder)) {
            Loggers.REMOTE.warn("Not found ProtocolNegotiatorBuilder for actualType {}.", actualType);
            return null;
        }
        return BUILDER_MAP.get(actualType).build();
    }
}
