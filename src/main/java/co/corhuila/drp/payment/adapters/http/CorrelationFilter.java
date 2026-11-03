package co.corhuila.drp.payment.adapters.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class CorrelationFilter extends OncePerRequestFilter {
  static final String HEADER = "X-Correlation-Id";
  static final String ATTR = "traceId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String id = request.getHeader(HEADER);
    if (id == null || id.isBlank()) {
      id = UUID.randomUUID().toString();
    }
    request.setAttribute(ATTR, id);
    response.setHeader(HEADER, id);
    filterChain.doFilter(request, response);
  }

  static String traceId(HttpServletRequest request) {
    Object value = request.getAttribute(ATTR);
    return value == null ? "" : String.valueOf(value);
  }
}
