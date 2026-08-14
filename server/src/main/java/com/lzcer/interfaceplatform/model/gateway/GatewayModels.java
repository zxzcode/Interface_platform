package com.lzcer.interfaceplatform.model.gateway;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Internal gateway transport models. */
public final class GatewayModels {
    private GatewayModels() { }
    @Getter @Setter @NoArgsConstructor @Accessors(fluent = true, chain = true)
    public static class GatewayRequest {
        private String method; private String path; private String rawQuery; private Map<String, List<String>> queryParameters;
        private Map<String, List<String>> headers; private byte[] body; private String remoteAddress;
        public GatewayRequest(String method, String path, String rawQuery, Map<String, List<String>> queryParameters,
                              Map<String, List<String>> headers, byte[] body, String remoteAddress) {
            this.method = method; this.path = path; this.rawQuery = rawQuery;
            this.queryParameters = queryParameters == null ? Collections.emptyMap() : queryParameters;
            this.headers = headers == null ? Collections.emptyMap() : headers;
            this.body = body == null ? new byte[0] : body; this.remoteAddress = remoteAddress;
        }
    }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class GatewayResponse { private int status; private Map<String, List<String>> headers; private byte[] body; }
}
