package com.mycompany.myproject.verticles.reverseproxy.configuration;

/**
 * Used by Configuration
 *
 * @author robertjchristian
 * @see com.mycompany.myproject.verticles.reverseproxy.configuration.ReverseProxyConfiguration
 */
public class SSL {

    // client properties
    public String trustStorePath, trustStorePassword;

    // server properties
    public String keyStorePath, keyStorePassword;
    public String symKeyPath;
    public int proxyHttpsPort;

}
