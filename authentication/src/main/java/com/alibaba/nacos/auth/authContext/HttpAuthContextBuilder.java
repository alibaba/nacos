package com.alibaba.nacos.auth.authContext;
import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;

public class HttpAuthContextBuilder implements AuthContextBuilder {
    
    @Override
    public AuthContext build(Object request) {
        AuthContext authContext = new AuthContext();
        HttpServletRequest req = (HttpServletRequest) request;
        Enumeration enu = req.getParameterNames();
        while(enu.hasMoreElements()){
            String paraName=(String)enu.nextElement();
            authContext.setParameter(paraName,req.getParamenter(paraName));
        }
        return authContext;
    }
}
