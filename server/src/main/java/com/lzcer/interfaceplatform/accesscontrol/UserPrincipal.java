package com.lzcer.interfaceplatform.accesscontrol;

public record UserPrincipal(long id, String username, String displayName, String role, long tokenVersion) {
}
