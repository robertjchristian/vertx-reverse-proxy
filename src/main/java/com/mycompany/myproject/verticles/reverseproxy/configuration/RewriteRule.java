package com.mycompany.myproject.verticles.reverseproxy.configuration;

/**
 * Used by Configuration
 * @see com.mycompany.myproject.verticles.reverseproxy.configuration.ReverseProxyConfiguration
 *
 * @author Robert Christian
 */
public class RewriteRule {

    public RewriteRule(String protocol, String host, Integer port) {
        this.protocol = protocol;
        this.host = host;
        this.port = port;
    }

    private String protocol;
    private String host;
    private Integer port;

    public String getProtocol() {
        return null == protocol ? "http" : protocol;
    }

    public String getHost() {
        return host;
    }

    public Integer getPort() {
        return null == port ? ("http".equals(protocol) ? 80 : 443) : port;
    }

}

