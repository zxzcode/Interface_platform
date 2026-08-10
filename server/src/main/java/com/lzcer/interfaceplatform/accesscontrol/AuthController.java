package com.lzcer.interfaceplatform.accesscontrol;

import com.lzcer.interfaceplatform.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public ApiResponse<UserService.LoginView> login(@Valid @RequestBody UserService.LoginCommand command) {
        return ApiResponse.ok(userService.login(command));
    }

    @GetMapping("/me")
    public ApiResponse<UserService.UserView> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(userService.get(principal.id()));
    }

    @PostMapping("/password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal UserPrincipal principal,
                                            @Valid @RequestBody UserService.ChangePasswordCommand command) {
        userService.changePassword(principal.id(), command);
        return ApiResponse.ok(null);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal UserPrincipal principal) {
        userService.logout(principal.id());
        return ApiResponse.ok(null);
    }
}
