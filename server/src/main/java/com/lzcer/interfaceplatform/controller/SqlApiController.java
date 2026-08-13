package com.lzcer.interfaceplatform.controller;

import com.lzcer.interfaceplatform.common.api.ApiResponse;
import com.lzcer.interfaceplatform.service.SqlApiService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sql-apis")
public class SqlApiController {

    private final SqlApiService service;

    public SqlApiController(SqlApiService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<SqlApiService.SqlApiView>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<SqlApiService.SqlApiView> get(@PathVariable long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SqlApiService.SqlApiView> create(
            @Valid @RequestBody SqlApiService.SqlApiCommand command) {
        return ApiResponse.ok(service.create(command));
    }

    @PutMapping("/{id}")
    public ApiResponse<SqlApiService.SqlApiView> update(
            @PathVariable long id, @Valid @RequestBody SqlApiService.SqlApiCommand command) {
        return ApiResponse.ok(service.update(id, command));
    }

    @PatchMapping("/{id}/enabled")
    public ApiResponse<SqlApiService.SqlApiView> setEnabled(
            @PathVariable long id, @RequestBody EnabledCommand command) {
        return ApiResponse.ok(service.setEnabled(id, command.enabled()));
    }

    @PostMapping("/{id}/test")
    public ApiResponse<SqlApiService.QueryResult> test(
            @PathVariable long id, @RequestBody(required = false) Map<String, Object> parameters) {
        return ApiResponse.ok(service.test(id, parameters == null ? Collections.emptyMap() : parameters));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }

    public record EnabledCommand(boolean enabled) {}
}
