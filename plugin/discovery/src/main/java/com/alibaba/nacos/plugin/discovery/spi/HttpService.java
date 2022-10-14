package com.alibaba.nacos.plugin.discovery.spi;

import com.alibaba.nacos.plugin.discovery.wapper.HttpServletWapper;
import org.springframework.context.ApplicationContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public interface HttpService {
    
    String getRequestUri();
    
    void init(ApplicationContext applicationContext);
    
    void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException;
    
    void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException;
    
    void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException;
    
    void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException;
    
    boolean enable();
    
    void bind(HttpServletWapper servletWapper);
    
}
