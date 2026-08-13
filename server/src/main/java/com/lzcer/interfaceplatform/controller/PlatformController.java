package com.lzcer.interfaceplatform.controller;

import com.lzcer.interfaceplatform.common.api.ApiResponse;
import com.lzcer.interfaceplatform.service.PlatformReadService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PlatformController {

    private final PlatformReadService readService;

    public PlatformController(PlatformReadService readService) {
        this.readService = readService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<PlatformReadService.DashboardSummary> dashboard() {
        return ApiResponse.ok(readService.dashboard());
    }

}
