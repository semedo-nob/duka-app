package com.duka.web;

import com.duka.security.CurrentUser;
import com.duka.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AccountController {
    private final AccountService accounts;

    @GetMapping("/account/me")
    Map<String, Object> me() {
        return accounts.me(CurrentUser.require());
    }

    @PutMapping("/account/profile")
    Map<String, Object> profile(@RequestBody Dto.ProfileUpdate update) {
        return accounts.updateProfile(update, CurrentUser.require());
    }

    @PostMapping("/account/password")
    Map<String, Object> password(@RequestBody Map<String, String> body) {
        return accounts.setPassword(body.get("currentPin"), body.get("password"), CurrentUser.require());
    }

    @PostMapping("/account/change-credential")
    Map<String, Object> changeCredential(@Valid @RequestBody Dto.CredentialChange request) {
        return accounts.changeCredential(request, CurrentUser.require());
    }

    @GetMapping("/account/sessions")
    List<Map<String, Object>> sessions() {
        return accounts.mySessions(CurrentUser.require());
    }

    @DeleteMapping("/account/sessions/{id}")
    Map<String, Object> revokeSession(@PathVariable String id) {
        return accounts.revokeMySession(id, CurrentUser.require());
    }

    @PostMapping("/account/logout")
    Map<String, Object> logout() {
        return accounts.logout(CurrentUser.require());
    }

    @PostMapping("/auth/invitations/accept")
    Dto.AuthResponse accept(@Valid @RequestBody Dto.InviteAccept request, HttpServletRequest http) {
        return accounts.acceptInvitation(request, http.getHeader("X-Duka-Device-Id"), http.getHeader("User-Agent"));
    }

    @PostMapping("/auth/recovery/request")
    Map<String, String> recovery(@Valid @RequestBody Dto.RecoveryRequest request) {
        return accounts.requestRecovery(request.phone());
    }

    @PostMapping("/auth/recovery/complete")
    Dto.AuthResponse completeRecovery(@Valid @RequestBody Dto.RecoveryComplete request, HttpServletRequest http) {
        return accounts.completeRecovery(request, http.getHeader("X-Duka-Device-Id"), http.getHeader("User-Agent"));
    }

    @GetMapping("/business")
    @PreAuthorize("hasAuthority('BUSINESS_MANAGE') or hasAuthority('USER_VIEW')")
    Map<String, Object> business() {
        return accounts.business(CurrentUser.require());
    }

    @PutMapping("/business/profile")
    @PreAuthorize("hasAuthority('BUSINESS_MANAGE')")
    Map<String, Object> updateBusiness(@RequestBody Dto.BusinessProfileUpdate update) {
        return accounts.updateBusiness(update, CurrentUser.require());
    }

    @PutMapping("/business/users/{id}/role")
    @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
    Dto.TeamView role(@PathVariable long id, @Valid @RequestBody Dto.RoleUpdate update) {
        return accounts.setRole(id, update.role(), CurrentUser.require());
    }

    @PostMapping("/business/users/{id}/recovery-code")
    @PreAuthorize("hasAuthority('USER_EDIT')")
    Map<String, String> recoveryCode(@PathVariable long id) {
        return accounts.issueRecoveryCode(id, CurrentUser.require());
    }

    @PostMapping("/business/owner-transfer")
    @PreAuthorize("hasRole('OWNER')")
    Map<String, Object> transfer(@Valid @RequestBody Dto.OwnerTransfer request) {
        return accounts.ownerTransfer(request, CurrentUser.require());
    }

    @GetMapping("/business/sessions")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    List<Map<String, Object>> businessSessions() {
        return accounts.businessSessions(CurrentUser.require());
    }

    @DeleteMapping("/business/sessions/{id}")
    @PreAuthorize("hasAuthority('USER_DISABLE')")
    Map<String, Object> revokeBusinessSession(@PathVariable String id) {
        return accounts.revokeBusinessSession(id, CurrentUser.require());
    }

    @GetMapping("/business/devices")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    List<Map<String, Object>> devices() {
        return accounts.businessDevices(CurrentUser.require());
    }

    @PostMapping("/business/devices/{deviceId}/revoke")
    @PreAuthorize("hasAuthority('BUSINESS_MANAGE')")
    Map<String, Object> revokeDevice(@PathVariable String deviceId) {
        return accounts.revokeDevice(deviceId, CurrentUser.require());
    }

    @PostMapping("/business/close")
    @PreAuthorize("hasRole('OWNER')")
    Map<String, Object> close(@Valid @RequestBody Dto.CloseBusiness request) {
        return accounts.closeBusiness(request, CurrentUser.require());
    }

    @GetMapping("/business/export/{kind}")
    @PreAuthorize("hasAuthority('BUSINESS_MANAGE')")
    ResponseEntity<String> export(@PathVariable String kind) {
        String body = accounts.export(kind, CurrentUser.require());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + kind + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(body);
    }
}
