package com.lzcer.interfaceplatform.controller;

import com.lzcer.interfaceplatform.common.api.ApiResponse;
import com.lzcer.interfaceplatform.service.ApiClientService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/clients")
public class ApiClientController {

    private final ApiClientService service;

    public ApiClientController(ApiClientService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<ApiClientService.ClientView>> list() { return ApiResponse.ok(service.list()); }

    @GetMapping("/{id}")
    public ApiResponse<ApiClientService.ClientView> get(@PathVariable long id) { return ApiResponse.ok(service.get(id)); }

    @GetMapping("/{id}/permissions")
    public ApiResponse<List<ApiClientService.Permission>> permissions(@PathVariable long id) {
        return ApiResponse.ok(service.permissions(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ApiClientService.ClientSecretView> create(
            @Valid @RequestBody ApiClientService.CreateClientCommand command) {
        return ApiResponse.ok(service.create(command));
    }

    @PutMapping("/{id}")
    public ApiResponse<ApiClientService.ClientView> update(@PathVariable long id,
            @Valid @RequestBody ApiClientService.UpdateClientCommand command) {
        return ApiResponse.ok(service.update(id, command));
    }

    @PutMapping("/{id}/permissions")
    public ApiResponse<ApiClientService.ClientView> updatePermissions(@PathVariable long id,
            @Valid @RequestBody PermissionCommand command) {
        return ApiResponse.ok(service.updatePermissions(id, command.permissions()));
    }

    @PostMapping("/{id}/rotate-secret")
    public ApiResponse<ApiClientService.ClientSecretView> rotate(@PathVariable long id) {
        return ApiResponse.ok(service.rotateSecret(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) { service.delete(id); }

    public record PermissionCommand(@Valid List<ApiClientService.Permission> permissions) {}
}
