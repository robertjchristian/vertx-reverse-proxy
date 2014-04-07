package com.mycompany.myproject.verticles.reverseproxy.model;

import java.util.Date;

/**
 * @author hpark
 */
public class SessionToken {

    private String username;
    private String authToken;
    private Date sessionDate;

    public SessionToken(String username, String authToken, Date sessionDate) {
        this.username = username;
        this.authToken = authToken;
        this.sessionDate = sessionDate;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public Date getSessionDate() {
        return sessionDate;
    }

    public void setSessionDate(Date sessionDate) {
        this.sessionDate = sessionDate;
    }
}
