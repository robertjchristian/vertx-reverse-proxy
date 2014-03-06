package com.mycompany.myproject.configuration;

/**
 * Created with IntelliJ IDEA.
 * User: rob
 * Date: 3/5/14
 * Time: 12:52 PM
 * To change this template use File | Settings | File Templates.
 */
public
class RewriteRule {

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
        return null == port ? 80 : port;
    }

}