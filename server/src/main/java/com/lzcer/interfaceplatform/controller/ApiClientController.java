package com.lzcer.interfaceplatform.controller;

import com.lzcer.interfaceplatform.common.api.ApiResponse;
import com.lzcer.interfaceplatform.service.ApiClientService;
import com.lzcer.interfaceplatform.model.client.ClientModels;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class ApiClientController {

    private final ApiClientService service;

    @GetMapping
    public ApiResponse<List<ClientModels.ClientView>> list() { return ApiResponse.ok(service.list()); }

    @GetMapping("/{id}")
    public ApiResponse<ClientModels.ClientView> get(@PathVariable long id) { return ApiResponse.ok(service.get(id)); }

    @GetMapping("/{id}/permissions")
    public ApiResponse<List<ClientModels.Permission>> permissions(@PathVariable long id) {
        return ApiResponse.ok(service.permissions(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ClientModels.ClientSecretView> create(
            @Valid @RequestBody ClientModels.CreateClientCommand command) {
        return ApiResponse.ok(service.create(command));
    }

    @PutMapping("/{id}")
    public ApiResponse<ClientModels.ClientView> update(@PathVariable long id,
            @Valid @RequestBody ClientModels.UpdateClientCommand command) {
        return ApiResponse.ok(service.update(id, command));
    }

    @PutMapping("/{id}/permissions")
    public ApiResponse<ClientModels.ClientView> updatePermissions(@PathVariable long id,
            @Valid @RequestBody PermissionCommand command) {
        return ApiResponse.ok(service.updatePermissions(id, command.permissions()));
    }

    @PostMapping("/{id}/rotate-secret")
    public ApiResponse<ClientModels.ClientSecretView> rotate(@PathVariable long id) {
        return ApiResponse.ok(service.rotateSecret(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) { service.delete(id); }

    public record PermissionCommand(@Valid List<ClientModels.Permission> permissions) {}
}
