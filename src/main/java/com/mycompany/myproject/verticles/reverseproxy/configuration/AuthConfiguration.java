package com.mycompany.myproject.verticles.reverseproxy.configuration;

import java.util.Map;

/**
 * @author hpark
 */
public class AuthConfiguration {

    public Map<String, AuthServer> authConfigs;


    public String getHost(String service) {
        for (String key : authConfigs.keySet()) {
            if (key.equals(service)) {
                return authConfigs.get(key).host;
            }
        }

        return null;
    }

    public Integer getPort(String service) {
        for (String key : authConfigs.keySet()) {
            if (key.equals(service)) {
                return authConfigs.get(key).port;
            }
        }

        return null;
    }

    public String getRequestPath(String service, String pathKey) {
        for (String key : authConfigs.keySet()) {
            if (key.equals(service)) {
                for (String existingPathKey : authConfigs.get(key).requestPaths.keySet()) {
                    if (existingPathKey.equals(pathKey)) {
                        return authConfigs.get(key).requestPaths.get(existingPathKey);
                    }
                }
            }
        }

        return null;
    }
}
