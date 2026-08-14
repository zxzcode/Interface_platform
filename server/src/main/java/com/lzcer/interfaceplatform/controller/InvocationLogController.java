package com.lzcer.interfaceplatform.controller;

import com.lzcer.interfaceplatform.common.api.ApiResponse;
import com.lzcer.interfaceplatform.service.InvocationLogService;
import com.lzcer.interfaceplatform.model.invocation.InvocationLogModels;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class InvocationLogController {

    private final InvocationLogService service;

    @GetMapping
    public ApiResponse<List<InvocationLogModels.LogSummary>> list(
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return ApiResponse.ok(service.list(limit));
    }

    @GetMapping("/{traceId}")
    public ApiResponse<InvocationLogModels.LogDetail> detail(@PathVariable String traceId) {
        return ApiResponse.ok(service.detail(traceId));
    }
}
