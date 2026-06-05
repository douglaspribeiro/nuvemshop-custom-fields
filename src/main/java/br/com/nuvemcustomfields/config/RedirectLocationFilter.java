package br.com.nuvemcustomfields.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

@Component
public class RedirectLocationFilter extends OncePerRequestFilter {

    private static final int HTTP_DEFAULT_PORT = 80;
    private static final int HTTPS_DEFAULT_PORT = 443;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        filterChain.doFilter(request, new RedirectResponseWrapper(request, response));
    }

    private static final class RedirectResponseWrapper extends HttpServletResponseWrapper {

        private final HttpServletRequest request;

        private RedirectResponseWrapper(HttpServletRequest request, HttpServletResponse response) {
            super(response);
            this.request = request;
        }

        @Override
        public void sendRedirect(String location) throws IOException {
            String target = normalizeLocation(request, location);
            if (isRelativeLocation(target)) {
                if (isCommitted()) {
                    super.sendRedirect(target);
                    return;
                }
                resetBuffer();
                setStatus(SC_FOUND);
                setHeader("Location", encodeRedirectURL(target));
                return;
            }
            super.sendRedirect(target);
        }

        @Override
        public void setHeader(String name, String value) {
            super.setHeader(name, isLocationHeader(name) ? normalizeLocation(request, value) : value);
        }

        @Override
        public void addHeader(String name, String value) {
            super.addHeader(name, isLocationHeader(name) ? normalizeLocation(request, value) : value);
        }

        private static boolean isLocationHeader(String name) {
            return "Location".equalsIgnoreCase(name);
        }
    }

    private static String normalizeLocation(HttpServletRequest request, String location) {
        if (location == null || !location.regionMatches(true, 0, "http://", 0, "http://".length())) {
            return location;
        }
        try {
            URI uri = new URI(location);
            if (!isSameHost(request, uri.getHost())) {
                return location;
            }
            if (isLocalhost(uri.getHost()) && !isHttpsRequest(request)) {
                return location;
            }
            int port = uri.getPort() == HTTP_DEFAULT_PORT ? -1 : uri.getPort();
            if (port == HTTPS_DEFAULT_PORT) {
                port = -1;
            }
            return new URI(
                    "https",
                    uri.getUserInfo(),
                    uri.getHost(),
                    port,
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment()
            ).toString();
        } catch (URISyntaxException ex) {
            return location;
        }
    }

    private static boolean isRelativeLocation(String location) {
        if (location == null || location.startsWith("//")) {
            return false;
        }
        return !location.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*");
    }

    private static boolean isSameHost(HttpServletRequest request, String redirectHost) {
        if (redirectHost == null) {
            return false;
        }
        String requestHost = hostWithoutPort(request.getHeader("X-Forwarded-Host"));
        if (requestHost == null || requestHost.isBlank()) {
            requestHost = request.getServerName();
        }
        return redirectHost.equalsIgnoreCase(hostWithoutPort(requestHost));
    }

    private static String hostWithoutPort(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String host = value.split(",", 2)[0].trim();
        if (host.startsWith("[") && host.contains("]")) {
            return host.substring(1, host.indexOf(']'));
        }
        int portSeparator = host.lastIndexOf(':');
        return portSeparator > -1 ? host.substring(0, portSeparator) : host;
    }

    private static boolean isLocalhost(String host) {
        String normalizedHost = host == null ? "" : host.toLowerCase(Locale.ROOT);
        return "localhost".equals(normalizedHost)
                || "127.0.0.1".equals(normalizedHost)
                || "::1".equals(normalizedHost);
    }

    private static boolean isHttpsRequest(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if ("https".equalsIgnoreCase(forwardedProto)) {
            return true;
        }
        String forwarded = request.getHeader("Forwarded");
        return forwarded != null && forwarded.toLowerCase(Locale.ROOT).contains("proto=https");
    }
}
