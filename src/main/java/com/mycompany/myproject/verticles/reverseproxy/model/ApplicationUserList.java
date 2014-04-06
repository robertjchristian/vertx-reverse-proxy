package com.mycompany.myproject.verticles.reverseproxy.model;

import java.util.List;

/**
 * @author hpark
 */
public class ApplicationUserList {

    List<ApplicationUser> applicationUserList;

    public ApplicationUserList(List<ApplicationUser> applicationUserList) {
        this.applicationUserList = applicationUserList;
    }

    public List<ApplicationUser> getApplicationUserList() {
        return applicationUserList;
    }

    public void setApplicationUserList(List<ApplicationUser> applicationUserList) {
        this.applicationUserList = applicationUserList;
    }
}
