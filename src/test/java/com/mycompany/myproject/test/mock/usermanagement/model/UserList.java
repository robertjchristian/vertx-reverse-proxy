package com.mycompany.myproject.test.mock.usermanagement.model;

import java.util.ArrayList;
import java.util.List;

public class UserList {

	private List<User> userList;

	public UserList() {
		userList = new ArrayList<User>();
	}

	public UserList(List<User> userList) {
		this.userList = userList;
	}

	public List<User> getUserList() {
		return userList;
	}

	public void setUserList(List<User> userList) {
		this.userList = userList;
	}

}
