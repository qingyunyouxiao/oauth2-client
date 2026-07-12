Direct summary: This class is a Spring REST controller ("/api/v1") exposing admin-related endpoints (get current admin, logout, list, get-by-id, create, update) and delegates business logic to AdminService and OAuth2AuthorizationServiceImpl while enforcing role-based access with @PreAuthorize.

What I inspected and found:
- Class-level:
  - @RestController, @RequestMapping("/api/v1"), @AllArgsConstructor.
  - Injects AdminService and OAuth2AuthorizationServiceImpl.

- Security:
  - Uses method-level @PreAuthorize checks:
    - @PreAuthorize("@resourceServerAuthorityChecker.hasAnyAdminRole()") for endpoints that act on the caller (getAdminSelf, logoutAdmin).
    - @PreAuthorize("hasAuthority('SUPER_ADMIN')") for admin management endpoints (list, get by id, create, update).
  - Custom principal injection via @CustomAuthenticationPrincipal EasyPlusUserInfo (wrapping CustomizedUserInfo in some methods).

- Endpoints (paths, methods, behavior):
  1. GET /api/v1/admins/me
     - Security: hasAnyAdminRole (custom checker).
     - Inputs: @CustomAuthenticationPrincipal EasyPlusUserInfo, @RequestHeader("Authorization") String authorizationHeader.
     - Behavior: extracts Bearer token via substring("Bearer ".length()), looks up OAuth2Authorization via authorizationService.findByToken(..., ACCESS_TOKEN), computes remaining access-token lifetime in seconds (Duration.between(now, expiresAt)). Returns AdminDTO.CurrentOneWithSessionRemainingSecondsRes containing the admin (via adminService.findAdminWithRoleIdsByAdminId) and remaining seconds.
     - Throws ResourceNotFoundException.

  2. GET /api/v1/admin/me/logout
     - Security: hasAnyAdminRole.
     - Inputs: HttpServletRequest (uses DefaultBearerTokenResolver to extract token).
     - Behavior: finds OAuth2Authorization by token and removes it via authorizationService.remove(...). Returns Map<String, Boolean> with "logout": true/false; on exception sets false and logs via CustomUtils.createNonStoppableErrorMessage.
     - Safe-guards: catches Exception around removal.

  3. GET /api/v1/admins
     - Security: SUPER_ADMIN required.
     - Inputs: pagination and filter params (skipPagination, pageNum, pageSize, adminSearchFilter, sorterValueFilter, dateRangeFilter) and @CustomAuthenticationPrincipal EasyPlusUserInfo.
     - Behavior: delegates to adminService.findAdminsByPageRequest(...) and returns Page<AdminDTO.OneWithRoleIdsRes>.
     - Throws JsonProcessingException, ResourceNotFoundException.

  4. GET /api/v1/admins/{id}
     - Security: SUPER_ADMIN.
     - Inputs: @PathVariable id, @CustomAuthenticationPrincipal.
     - Behavior: returns Admin entity from adminService.findById(id) wrapped in ResponseEntity.ok.
     - Throws ResourceNotFoundException.

  5. POST /api/v1/admins
     - Security: SUPER_ADMIN.
     - Inputs: @Valid @RequestBody AdminDTO.CreateReq.
     - Behavior: calls adminService.create(dto) and returns AdminDTO.CreateRes.

  6. PUT /api/v1/admins/{id}
     - Security: SUPER_ADMIN.
     - Inputs: path id, @Valid @RequestBody AdminDTO.UpdateReq.
     - Behavior: calls adminService.update(id, dto) and returns AdminDTO.UpdateRes.
     - Throws ResourceNotFoundException.

- Key types used:
  - AdminDTO (several nested request/response/summary DTOs).
  - Admin entity.
  - EasyPlusUserInfo and CustomizedUserInfo (custom principal types).
  - OAuth2Authorization / OAuth2AccessToken (to compute token expiry and to remove authorizations).
  - DefaultBearerTokenResolver (used in logout endpoint).

- Error handling & edge cases worth noting:
  - getAdminSelf directly substrings the Authorization header to remove "Bearer " — will throw if header is missing or malformed.
  - logoutAdmin uses DefaultBearerTokenResolver (safer) and catches exceptions around authorization removal.
  - Token expiry calculation assumes expiresAt != null; code checks the token, but if expiresAt is null Duration.between may throw NPE (the code checks accessToken != null and then uses getExpiresAt(); it does not null-check expiresAt).
  - Methods declare ResourceNotFoundException where appropriate; controller delegates errors to service layer.

If you want, I can:
- Extract a compact list of the endpoints and their request/response DTOs,
- Point out potential bugs and propose small fixes (e.g., safer header handling, null checks),
- Or show the related AdminDTO and AdminService usages for deeper understanding.