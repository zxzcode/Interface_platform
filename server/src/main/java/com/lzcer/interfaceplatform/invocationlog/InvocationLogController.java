package com.lzcer.interfaceplatform.invocationlog;

import com.lzcer.interfaceplatform.common.api.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class InvocationLogController {

    private final InvocationLogService service;

    public InvocationLogController(InvocationLogService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<InvocationLogService.LogSummary>> list(
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return ApiResponse.ok(service.list(limit));
    }

    @GetMapping("/{traceId}")
    public ApiResponse<InvocationLogService.LogDetail> detail(@PathVariable String traceId) {
        return ApiResponse.ok(service.detail(traceId));
    }
}
