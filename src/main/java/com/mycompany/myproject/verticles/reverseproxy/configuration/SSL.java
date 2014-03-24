package com.mycompany.myproject.verticles.reverseproxy.configuration;

/**
 * Used by Configuration
 * @see com.mycompany.myproject.verticles.reverseproxy.configuration.ReverseProxyConfiguration
 *
 * @author Robert Christian
 */
public class SSL {

    // TODO encapsulate

    // client properties
    public String trustStorePath, trustStorePassword;

    // server properties
    public String keyStorePath, keyStorePassword;
    public int proxyHttpsPort;

}

