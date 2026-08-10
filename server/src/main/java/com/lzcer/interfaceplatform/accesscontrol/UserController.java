package com.lzcer.interfaceplatform.accesscontrol;

import com.lzcer.interfaceplatform.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<List<UserService.UserView>> list() { return ApiResponse.ok(userService.list()); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserService.UserView> create(@Valid @RequestBody UserService.CreateUserCommand command) {
        return ApiResponse.ok(userService.create(command));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserService.UserView> update(@PathVariable long id,
                                                    @Valid @RequestBody UserService.UpdateUserCommand command,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(userService.update(id, command, principal.id()));
    }

    @PatchMapping("/{id}/password")
    public ApiResponse<Void> resetPassword(@PathVariable long id,
                                           @Valid @RequestBody UserService.PasswordCommand command) {
        userService.resetPassword(id, command);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id, @AuthenticationPrincipal UserPrincipal principal) {
        userService.delete(id, principal.id());
    }
}
