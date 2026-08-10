package com.lzcer.interfaceplatform.interfacecatalog;

import com.lzcer.interfaceplatform.common.api.ApiResponse;
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
@RequestMapping("/api/systems")
public class SystemController {
    private final SystemService service;
    public SystemController(SystemService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<SystemService.SystemView>> list() { return ApiResponse.ok(service.list()); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SystemService.SystemView> create(@Valid @RequestBody SystemService.SystemCommand command) {
        return ApiResponse.ok(service.create(command));
    }

    @PutMapping("/{id}")
    public ApiResponse<SystemService.SystemView> update(@PathVariable long id,
            @Valid @RequestBody SystemService.SystemCommand command) { return ApiResponse.ok(service.update(id, command)); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) { service.delete(id); }
}
