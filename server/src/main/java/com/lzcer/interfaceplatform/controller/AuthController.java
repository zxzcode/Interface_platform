package com.lzcer.interfaceplatform.controller;

import com.lzcer.interfaceplatform.common.api.ApiResponse;
import com.lzcer.interfaceplatform.accesscontrol.UserPrincipal;
import com.lzcer.interfaceplatform.service.UserService;
import com.lzcer.interfaceplatform.model.user.UserModels;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/login")
    public ApiResponse<UserModels.LoginView> login(@Valid @RequestBody UserModels.LoginCommand command) {
        return ApiResponse.ok(userService.login(command));
    }

    @GetMapping("/me")
    public ApiResponse<UserModels.UserView> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(userService.get(principal.id()));
    }

    @PostMapping("/password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal UserPrincipal principal,
                                            @Valid @RequestBody UserModels.ChangePasswordCommand command) {
        userService.changePassword(principal.id(), command);
        return ApiResponse.ok(null);
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePasswordWithPut(@AuthenticationPrincipal UserPrincipal principal,
                                                   @Valid @RequestBody UserModels.ChangePasswordCommand command) {
        userService.changePassword(principal.id(), command);
        return ApiResponse.ok(null);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal UserPrincipal principal) {
        userService.logout(principal.id());
        return ApiResponse.ok(null);
    }
}
