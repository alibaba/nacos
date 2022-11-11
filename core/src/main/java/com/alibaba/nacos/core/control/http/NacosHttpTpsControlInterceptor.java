package com.alibaba.nacos.core.control.http;

import com.alibaba.nacos.core.control.TpsControl;
import com.alibaba.nacos.core.control.TpsControlConfig;
import com.alibaba.nacos.plugin.control.ControlManagerFactory;
import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;

public class NacosHttpTpsControlInterceptor implements HandlerInterceptor {
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        try {
            if (handler instanceof HandlerMethod) {
                Method method = ((HandlerMethod) handler).getMethod();
                if (method.isAnnotationPresent(TpsControl.class) && TpsControlConfig.isTpsControlEnabled()) {
                    
                    TpsControl tpsControl = method.getAnnotation(TpsControl.class);
                    String pointName = tpsControl.pointName();
                    HttpTpsCheckRequestParser parser = HttpTpsCheckRequestParserRegistry.getParser(pointName);
                    if (parser != null) {
                        TpsCheckRequest httpTpsCheckRequest = parser.parse(request);
                        TpsCheckResponse checkResponse = ControlManagerFactory.getInstance().getTpsControlManager()
                                .check(httpTpsCheckRequest);
                        if (!checkResponse.isSuccess()) {
                            generate503Response(request, response, checkResponse.getMessage());
                            return false;
                        }
                    }
                    
                }
            }
            
        } catch (Throwable throwable) {
            Loggers.TPS.error("Error to check tps control", throwable);
        }
        
        return true;
    }
    
    void generate503Response(HttpServletRequest request, HttpServletResponse response, String message) {
        
        try {
            // Disable cache.
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 0);
            response.setHeader("Cache-Control", "no-cache,no-store");
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.getWriter().println(message);
        } catch (Exception ex) {
            Loggers.TPS.error("Error to generate tps 503 response", ex);
        }
    }
}
