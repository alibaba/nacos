package com.alibaba.nacos.plugin.discovery;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.plugin.discovery.spi.HttpService;
import com.alibaba.nacos.plugin.discovery.wapper.HttpServletWapper;
import org.springframework.beans.BeansException;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HttpServiceManager implements ServletContextInitializer, ApplicationContextAware {
    
    private Map<String, HttpService> httpServiceContainer = new ConcurrentHashMap<>(4);
    
    private ServletContext servletContext;
    
    @Override
    public void onStartup(ServletContext servletContext) throws ServletException {
        this.servletContext = servletContext;
        httpServiceContainer.forEach((uri, httpService) -> {
            ServletRegistration.Dynamic dynamic = servletContext.addServlet(httpService.getClass().getSimpleName(),
                    new HttpServletWapper(httpService));
            dynamic.addMapping(httpService.getRequestUri());
        });
        
    }
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        Collection<HttpService> httpServiceCollection = NacosServiceLoader.load(HttpService.class);
        for (HttpService httpService : httpServiceCollection) {
            if (httpService.enable()) {
                httpService.init(applicationContext);
                httpServiceContainer.put(httpService.getRequestUri(), httpService);
            }
            
        }
        
    }
}
