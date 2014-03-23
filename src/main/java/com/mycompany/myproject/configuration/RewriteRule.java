package com.mycompany.myproject.configuration;

/**
 * Encapsulates an individual rewrite rule.
 *
 * @author <a href="https://github.com/robertjchristian">Robert Christian</a>
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