package com.alibaba.nacos.plugin.discovery.wapper;

import com.alibaba.nacos.plugin.discovery.spi.HttpService;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class HttpServletWapper extends HttpServlet {
    
    HttpService httpService;
    
    public HttpServletWapper(HttpService httpService) {
        this.httpService = httpService;
        httpService.bind(this);
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        httpService.doGet(req, resp);
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        httpService.doPost(req, resp);
    }
    
    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        httpService.doPut(req, resp);
    }
    
    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        httpService.doDelete(req, resp);
    }
    
}
