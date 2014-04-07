package com.mycompany.myproject.verticles.reverseproxy.configuration;

import java.util.Map;

/**
 * @author hpark
 */
public class ServiceDescriptor {

    public String host;
    public Integer port;
    public Map<String, String> requestPaths;

    public ServiceDescriptor(String host, int port, Map<String, String> requestPaths) {
        this.host = host;
        this.port = port;
        this.requestPaths = requestPaths;
    }
}
