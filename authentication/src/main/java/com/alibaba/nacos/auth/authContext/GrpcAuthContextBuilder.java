package authentication.src.main.java.com.alibaba.nacos.auth.authContext;

import com.alibaba.nacos.api.remote.request.Request;

import java.util.Map;

public class GrpcAuthContextBuilder implements AuthContextBuilder {
    
    @Override
    public AuthContext build(Object request) {
        AuthContext authContext = new AuthContext();
        Request req = (Request) request;
        Map<String,String> map = req.getHeaders();
        for(Map.Entry entry:map.entrySet()){
            authContext.setParameter((String)entry.getKey(),entry.getValue());
        }
        return authContext;
    }
}