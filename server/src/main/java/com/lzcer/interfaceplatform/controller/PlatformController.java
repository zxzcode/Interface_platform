package com.lzcer.interfaceplatform.controller;

import com.lzcer.interfaceplatform.common.api.ApiResponse;
import com.lzcer.interfaceplatform.service.PlatformReadService;
import com.lzcer.interfaceplatform.model.platform.PlatformModels;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PlatformController {

    private final PlatformReadService readService;

    @GetMapping("/dashboard")
    public ApiResponse<PlatformModels.DashboardSummary> dashboard() {
        return ApiResponse.ok(readService.dashboard());
    }

}
