package com.chenfeng.ssoclient.model;

public class LoginUser {

	private String loginName;

	public void setLoginName(String loginName) {
		this.loginName = loginName;
	}

	public String getLoginName() {
		return loginName;
	}

	@Override
	public String toString() {
		return loginName;
	}
}
