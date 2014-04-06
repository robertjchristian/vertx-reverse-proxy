package com.mycompany.myproject.verticles.reverseproxy.model;

/**
 * @author hpark
 */
public class AuthRequest {

    private String type;
    private String loginId;
    private String token;

    public AuthRequest(String type, String loginId, String token) {
        this.type = type;
        this.loginId = loginId;
        this.token = token;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLoginId() {
        return loginId;
    }

    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}