package com.oauth2.client.admin.api;

import org.springframework.web.bind.annotation.RequestMapping;

import com.oauth2.client.service.AdminService;

@RequestMapping("api/v1")
public class AdminApi {
        private final AdminService adminService;

        public AdminApi(AdminService adminService) {
                this.adminService = adminService;
        }


}


// *domain/admin/AdminApi.java*

// 这个类是一个 Spring REST 控制器（路径前缀为 /api/v1），
// 主要暴露与管理员相关的接口（包括获取当前管理员信息、登出、列表查询、按ID获取、创建和更新）。
// 它将具体的业务逻辑委托给 AdminService 和 OAuth2AuthorizationServiceImpl 处理，
// 并通过 @PreAuthorize 注解来强制执行基于角色的访问控制。