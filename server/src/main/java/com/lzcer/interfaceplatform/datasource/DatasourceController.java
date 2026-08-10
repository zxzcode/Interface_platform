package com.lzcer.interfaceplatform.datasource;

import com.lzcer.interfaceplatform.common.api.ApiResponse;
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

import java.util.List;

@RestController
@RequestMapping("/api/datasources")
public class DatasourceController {

    private final DatasourceService service;

    public DatasourceController(DatasourceService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<DatasourceService.DatasourceView>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<DatasourceService.DatasourceView> get(@PathVariable long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DatasourceService.DatasourceView> create(
            @Valid @RequestBody DatasourceService.DatasourceCommand command) {
        return ApiResponse.ok(service.create(command));
    }

    @PutMapping("/{id}")
    public ApiResponse<DatasourceService.DatasourceView> update(
            @PathVariable long id, @Valid @RequestBody DatasourceService.DatasourceCommand command) {
        return ApiResponse.ok(service.update(id, command));
    }

    @PatchMapping("/{id}/enabled")
    public ApiResponse<DatasourceService.DatasourceView> setEnabled(
            @PathVariable long id, @RequestBody EnabledCommand command) {
        return ApiResponse.ok(service.setEnabled(id, command.enabled()));
    }

    @PostMapping("/{id}/test")
    public ApiResponse<DatasourceService.ConnectionTestResult> test(@PathVariable long id) {
        return ApiResponse.ok(service.test(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }

    public record EnabledCommand(boolean enabled) {}
}
