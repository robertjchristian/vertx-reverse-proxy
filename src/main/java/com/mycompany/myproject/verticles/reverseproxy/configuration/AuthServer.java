package com.mycompany.myproject.verticles.reverseproxy.configuration;

import java.util.Map;

/**
 * @author hpark
 */
public class AuthServer {

    public String host;
    public Integer port;
    public Map<String, String> requestPaths;
}
