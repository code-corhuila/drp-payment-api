package co.corhuila.drp.payment.adapters.http;

import co.corhuila.drp.payment.app.AppException;
import co.corhuila.drp.payment.app.Principal;
import co.corhuila.drp.payment.app.TokenVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public final class AuthFilter extends OncePerRequestFilter {
  static final String PRINCIPAL = "principal";
  static final String BEARER = "accessToken";

  private final TokenVerifier tokens;
  private final ObjectMapper mapper;

  public AuthFilter(TokenVerifier tokens, ObjectMapper mapper) {
    this.tokens = tokens;
    this.mapper = mapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return "/health".equals(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header == null || header.length() < 7 || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
      writeUnauthorized(request, response);
      return;
    }
    String raw = header.substring(7).trim();
    if (raw.isEmpty()) {
      writeUnauthorized(request, response);
      return;
    }
    try {
      Principal principal = tokens.parse(raw);
      if (principal.subject() == null || principal.subject().isBlank()) {
        writeUnauthorized(request, response);
        return;
      }
      request.setAttribute(PRINCIPAL, principal);
      request.setAttribute(BEARER, raw);
      filterChain.doFilter(request, response);
    } catch (AppException ex) {
      writeError(request, response, ex);
    }
  }

  private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
    writeError(request, response, AppException.unauthorized());
  }

  private void writeError(HttpServletRequest request, HttpServletResponse response, AppException ex)
      throws IOException {
    int status =
        switch (ex.kind()) {
          case UNAVAILABLE -> 503;
          default -> 401;
        };
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    mapper.writeValue(
        response.getOutputStream(),
        new HttpDtos.ErrorBody(ex.code(), ex.getMessage(), ex.details(), CorrelationFilter.traceId(request)));
  }

  static Principal principal(HttpServletRequest request) {
    Object value = request.getAttribute(PRINCIPAL);
    return value instanceof Principal p ? p : new Principal("", "USER");
  }

  static String accessToken(HttpServletRequest request) {
    Object value = request.getAttribute(BEARER);
    return value == null ? "" : String.valueOf(value);
  }
}
