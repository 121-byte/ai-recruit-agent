package com.example.recruit.dto;

/**
 * 创建用户请求 DTO (复刻对齐清单 §第四部分 dto/CreateUserRequest)。
 * 用于 UserAdminController 创建用户接口。
 */
public class CreateUserRequest {

    private String username;
    private String password;
    private String realName;
    private String email;
    private String phone;
    private String department;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }
}
