package com.repoguard.security;

import com.repoguard.config.RepoGuardProperties;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.net.URI;
import java.util.*;

@Component
@ConditionalOnProperty(name = "repoguard.role", havingValue = "api", matchIfMissing = true)
public class BrowserRequestFilter extends OncePerRequestFilter {
    private final Set<String> origins = new HashSet<>();
    private final Set<String> hosts = new HashSet<>();
    public BrowserRequestFilter(RepoGuardProperties props) {
        List<String> values = new ArrayList<>(props.allowedOrigins()); values.add(props.publicBaseUrl());
        for (String value : values) {
            URI uri = URI.create(value);
            if (uri.getHost() == null || uri.getUserInfo() != null || !List.of("http", "https").contains(uri.getScheme()))
                throw new IllegalArgumentException("Invalid public origin configuration.");
            origins.add(value); hosts.add(uri.getRawAuthority());
        }
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "no-store");
        if (request.getRequestURI().startsWith("/internal/")) { response.sendError(404); return; }
        if (request.getRequestURI().equals("/api/health") && request.getMethod().equals("GET")) { chain.doFilter(request, response); return; }
        String origin = request.getHeader("Origin");
        boolean mutation = !Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
        if (!hosts.contains(request.getHeader("Host")) || origin != null && !origins.contains(origin)
                || "cross-site".equals(request.getHeader("Sec-Fetch-Site"))
                || mutation && !"true".equals(request.getHeader("X-RepoGuard-Request"))) {
            response.setStatus(403); response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"ORIGIN_DENIED\",\"message\":\"Request origin or host is not allowed.\"}"); return;
        }
        chain.doFilter(request, response);
    }
}
