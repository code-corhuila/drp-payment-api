package co.corhuila.drp.payment.adapters.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public final class StripSpoofedIdentityFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    filterChain.doFilter(new StrippedRequest(request), response);
  }

  private static final class StrippedRequest extends HttpServletRequestWrapper {
    StrippedRequest(HttpServletRequest request) {
      super(request);
    }

    @Override
    public String getHeader(String name) {
      if (spoofed(name)) {
        return null;
      }
      return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      if (spoofed(name)) {
        return Collections.emptyEnumeration();
      }
      return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
      return Collections.enumeration(
          Collections.list(super.getHeaderNames()).stream().filter(h -> !spoofed(h)).toList());
    }

    private static boolean spoofed(String name) {
      return name != null && name.toLowerCase(Locale.ROOT).startsWith("x-user");
    }
  }
}
