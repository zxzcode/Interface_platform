package com.lzcer.interfaceplatform.controller;

import com.lzcer.interfaceplatform.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import com.lzcer.interfaceplatform.accesscontrol.UserPrincipal;
import com.lzcer.interfaceplatform.service.GatewayService;
import com.lzcer.interfaceplatform.service.InterfaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;

@RestController
@RequestMapping("/api")
public class InterfaceController {

    private final InterfaceService service;
    private final GatewayService gatewayService;

    public InterfaceController(InterfaceService service, GatewayService gatewayService) {
        this.service = service;
        this.gatewayService = gatewayService;
    }

    @GetMapping("/interfaces")
    public ApiResponse<List<InterfaceService.InterfaceView>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/interfaces/{id}")
    public ApiResponse<InterfaceService.InterfaceView> get(@PathVariable long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping("/interfaces")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InterfaceService.InterfaceView> create(
            @Valid @RequestBody InterfaceService.InterfaceCommand command) {
        return ApiResponse.ok(service.create(command));
    }

    @PutMapping("/interfaces/{id}")
    public ApiResponse<InterfaceService.InterfaceView> update(
            @PathVariable long id, @Valid @RequestBody InterfaceService.InterfaceCommand command) {
        return ApiResponse.ok(service.update(id, command));
    }

    @PatchMapping("/interfaces/{id}/enabled")
    public ApiResponse<InterfaceService.InterfaceView> setEnabled(
            @PathVariable long id, @RequestBody EnabledCommand command) {
        return ApiResponse.ok(service.setEnabled(id, command.enabled()));
    }

    @DeleteMapping("/interfaces/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }

    @PostMapping("/interfaces/{id}/test")
    public ResponseEntity<byte[]> test(@PathVariable long id, HttpServletRequest servletRequest,
                                       @RequestBody(required = false) byte[] body,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        Collections.list(servletRequest.getHeaderNames()).forEach(name ->
                headers.put(name, Collections.list(servletRequest.getHeaders(name))));
        GatewayService.GatewayResponse response = gatewayService.executeManagementHttp(id,
                servletRequest.getQueryString(), headers, body == null ? new byte[0] : body, principal.username());
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.status());
        response.headers().forEach((name, values) -> values.forEach(value -> builder.header(name, value)));
        return builder.body(response.body());
    }

    public record EnabledCommand(boolean enabled) {}
}
