# OAuth2 客户端

一套基于应用令牌、易于上手的 OAuth2 JPA 实现方案，适配 Spring Boot 项目迭代扩展。

## 解决方案

### Maven 依赖

- Maven 仓库地址


### OIDC JWT 相关资料

- 基于 JWT 授权器与 OIDC 开发鉴权接口

### OAuth2 资源服务器问题

- 基于 Spring Security 搭建 OAuth 2.0 资源服务器
- FeignClient 无法导入问题解决教程

## 简介

*domain/admin/AdminApi.java*

这个类是一个 Spring REST 控制器（路径前缀为 /api/v1），主要暴露与管理员相关的接口（包括获取当前管理员信息、登出、列表查询、按ID获取、创建和更新）。它将具体的业务逻辑委托给 AdminService 和 OAuth2AuthorizationServiceImpl 处理，并通过 @PreAuthorize 注解来强制执行基于角色的访问控制。

我的检查与发现：

类级别：
使用了 @RestController、@RequestMapping("/api/v1") 和 @AllArgsConstructor 注解。
注入了 AdminService 和 OAuth2AuthorizationServiceImpl。

安全性：
在方法级别使用了 @PreAuthorize 进行权限校验：
  针对仅操作当前调用者的接口（如获取自身信息、登出），使用了自定义校验器：@PreAuthorize("@resourceServerAuthorityChecker.hasAnyAdminRole()")。
  针对管理员管理接口（列表、按ID获取、创建、更新），要求必须具有超级管理员权限：@PreAuthorize("hasAuthority('SUPER_ADMIN')")。
通过 @CustomAuthenticationPrincipal EasyPlusUserInfo 注入自定义的用户主体（在某些方法中包装了 CustomizedUserInfo）。

接口详情（路径、请求方法、行为）：
GET /api/v1/admins/me
   安全： 具有任意管理员角色（自定义校验）。
   入参： @CustomAuthenticationPrincipal EasyPlusUserInfo，以及请求头 Authorization。
   行为： 通过字符串截取（substring("Bearer ".length())）提取 Bearer Token，然后调用 authorizationService.findByToken(..., ACCESS_TOKEN) 查找 OAuth2 授权信息，并计算 Access Token 的剩余有效时间（秒）。最后返回 AdminDTO.CurrentOneWithSessionRemainingSecondsRes，包含管理员信息（通过 adminService.findAdminWithRoleIdsByAdminId 获取）和剩余秒数。
   异常： 找不到资源时抛出 ResourceNotFoundException。

GET /api/v1/admin/me/logout
   安全： 具有任意管理员角色。
   入参： HttpServletRequest（使用 DefaultBearerTokenResolver 提取 Token）。
   行为： 根据 Token 查找 OAuth2 授权信息并调用 authorizationService.remove(...) 将其移除。返回一个包含 "logout": true/false 的 Map；如果发生异常，则设为 false 并通过 CustomUtils.createNonStoppableErrorMessage 记录日志。
   安全兜底： 对移除操作进行了 Exception 捕获。

GET /api/v1/admins
   安全： 需要 SUPER_ADMIN 权限。
   入参： 分页与过滤参数（skipPagination, pageNum, pageSize, adminSearchFilter, sorterValueFilter, dateRangeFilter）以及 @CustomAuthenticationPrincipal EasyPlusUserInfo。
   行为： 委托给 adminService.findAdminsByPageRequest(...) 处理，返回 Page<AdminDTO.OneWithRoleIdsRes>。
   异常： 可能抛出 JsonProcessingException 和 ResourceNotFoundException。

GET /api/v1/admins/{id}
   安全： 需要 SUPER_ADMIN 权限。
   入参： 路径参数 id，@CustomAuthenticationPrincipal。
   行为： 调用 adminService.findById(id) 获取 Admin 实体，并用 ResponseEntity.ok 包装返回。
   异常： 抛出 ResourceNotFoundException。

POST /api/v1/admins
   安全： 需要 SUPER_ADMIN 权限。
   入参： @Valid @RequestBody AdminDTO.CreateReq。
   行为： 调用 adminService.create(dto)，返回 AdminDTO.CreateRes。

PUT /api/v1/admins/{id}
   安全： 需要 SUPER_ADMIN 权限。
   入参： 路径参数 id，@Valid @RequestBody AdminDTO.UpdateReq。
   行为： 调用 adminService.update(id, dto)，返回 AdminDTO.UpdateRes。
   异常： 抛出 ResourceNotFoundException。

涉及的关键类型：
AdminDTO（包含多个嵌套的请求/响应/摘要 DTO）。
Admin 实体。
EasyPlusUserInfo 和 CustomizedUserInfo（自定义主体类型）。
OAuth2Authorization / OAuth2AccessToken（用于计算 Token 过期时间和移除授权）。
DefaultBearerTokenResolver（在登出接口中使用）。

值得注意的错误处理与边界情况：
getAdminSelf 方法直接对 Authorization 请求头进行字符串截取以去除 "Bearer " 前缀——如果请求头缺失或格式错误，会直接报错。
logoutAdmin 使用了 DefaultBearerTokenResolver（更安全），并且对授权移除操作进行了异常捕获。
Token 过期时间的计算假设 expiresAt 不为空；代码虽然检查了 Token 本身，但在使用 getExpiresAt() 后，并没有对 expiresAt 进行空值检查，这可能导致 Duration.between 抛出空指针异常（NPE）。
相关方法在适当的地方声明了 ResourceNotFoundException，控制器将错误处理委托给了 Service 层。

如果你需要，我还可以：
提取一份精简的接口列表及其对应的请求/响应 DTO；
指出潜在的 Bug 并提供小修复方案（例如更安全的 Header 处理、增加空值检查等）；
或者展示相关的 AdminDTO 和 AdminService 用法，以便更深入地理解。