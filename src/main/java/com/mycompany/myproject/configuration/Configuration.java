package com.mycompany.myproject.configuration;

import java.util.Map;

/**
 * Serializable Configuration
 * <p/>
 * This is the POJO representation of conf.json. When changing the configuration
 * model, please do so here, then output an example and update the conf.json
 * appropriately.
 * <p/>
 * The application will fail to bootstrap if "Configuration" cannot be built
 * from conf.json.
 *
 * @author <a href="https://github.com/robertjchristian">Robert Christian</a>
 */
public class Configuration {

    private final String configFilePath;
    private final String keyStorePath;
    private final String keyStorePassword;
    private final String trustStorePath;
    private final String trustStorePassword;
    private final int proxyHttpPort;
    private final int proxyHttpsPort;
    private final Map<String, RewriteRule> rewriteRules;


    public Configuration(String configFilePath, String keyStorePath, String keyStorePassword, String trustStorePath, String trustStorePassword,
                         int proxyHttpPort, int proxyHttpsPort, Map<String, RewriteRule> rewriteRules) {
        this.configFilePath = configFilePath;
        this.keyStorePath = keyStorePath;
        this.keyStorePassword = keyStorePassword;
        this.trustStorePath = trustStorePath;
        this.trustStorePassword = trustStorePassword;
        this.proxyHttpPort = proxyHttpPort;
        this.proxyHttpsPort = proxyHttpsPort;
        this.rewriteRules = rewriteRules;
    }

    public Map<String, RewriteRule> getRewriteRules() {
        return rewriteRules;
    }

    public String getConfigFilePath() {
        return configFilePath;
    }

    public String getKeyStorePath() {
        return keyStorePath;
    }

    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    public String getTrustStorePath() {
        return trustStorePath;
    }

    public String getTrustStorePassword() {
        return trustStorePassword;
    }

    public int getProxyHttpPort() {
        return proxyHttpPort;
    }

    public int getProxyHttpsPort() {
        return proxyHttpsPort;
    }
}