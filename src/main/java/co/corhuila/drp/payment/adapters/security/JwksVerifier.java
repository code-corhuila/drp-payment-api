package co.corhuila.drp.payment.adapters.security;

import co.corhuila.drp.payment.app.AppException;
import co.corhuila.drp.payment.app.Principal;
import co.corhuila.drp.payment.app.TokenVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class JwksVerifier implements TokenVerifier {
  public static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

  public interface KeySource {
    Map<String, Object> jwks();
  }

  private final KeySource source;
  private final Map<String, RSAPublicKey> keys = new ConcurrentHashMap<>();

  public JwksVerifier(KeySource source) {
    this.source = source;
  }

  public static JwksVerifier http(String url, HttpClient client, ObjectMapper mapper) {
    return new JwksVerifier(new HttpJwks(url, client, mapper));
  }

  @Override
  public Principal parse(String raw) {
    try {
      SignedJWT jwt = SignedJWT.parse(raw);
      if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
        throw AppException.unauthorized();
      }
      String kid = jwt.getHeader().getKeyID();
      RSAPublicKey key = keyFor(kid);
      if (!jwt.verify(new RSASSAVerifier(key))) {
        throw AppException.unauthorized();
      }
      var claims = jwt.getJWTClaimsSet();
      Instant now = Instant.now();
      if (claims.getExpirationTime() == null
          || Instant.ofEpochMilli(claims.getExpirationTime().getTime())
              .plus(CLOCK_SKEW)
              .isBefore(now)) {
        throw AppException.unauthorized();
      }
      if (claims.getNotBeforeTime() != null
          && Instant.ofEpochMilli(claims.getNotBeforeTime().getTime())
              .minus(CLOCK_SKEW)
              .isAfter(now)) {
        throw AppException.unauthorized();
      }
      String sub = claims.getSubject();
      if (sub == null || sub.isBlank()) {
        throw AppException.unauthorized();
      }
      return new Principal(sub, roleOf(claims.getClaim("role"), claims.getClaim("roles")));
    } catch (AppException ex) {
      throw ex;
    } catch (Exception ex) {
      throw AppException.unauthorized();
    }
  }

  private RSAPublicKey keyFor(String kid) {
    RSAPublicKey cached = cached(kid);
    if (cached != null) {
      return cached;
    }
    refresh();
    cached = cached(kid);
    if (cached == null) {
      throw AppException.unauthorized();
    }
    return cached;
  }

  private RSAPublicKey cached(String kid) {
    if (kid != null && keys.containsKey(kid)) {
      return keys.get(kid);
    }
    if ((kid == null || kid.isBlank()) && keys.size() == 1) {
      return keys.values().iterator().next();
    }
    return null;
  }

  private void refresh() {
    Map<String, Object> doc;
    try {
      doc = source.jwks();
    } catch (AppException ex) {
      throw ex;
    } catch (Exception ex) {
      throw AppException.jwksUnavailable();
    }
    Map<String, RSAPublicKey> parsed = parseJwks(doc);
    if (parsed.isEmpty()) {
      throw AppException.jwksUnavailable();
    }
    keys.clear();
    keys.putAll(parsed);
  }

  @SuppressWarnings("unchecked")
  static Map<String, RSAPublicKey> parseJwks(Map<String, Object> doc) {
    Map<String, RSAPublicKey> out = new HashMap<>();
    Object raw = doc.get("keys");
    if (!(raw instanceof Iterable<?> list)) {
      return out;
    }
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> row)) {
        continue;
      }
      Map<String, Object> jwk = (Map<String, Object>) row;
      if (!"RSA".equals(String.valueOf(jwk.get("kty")))) {
        continue;
      }
      Object alg = jwk.get("alg");
      if (alg != null && !"RS256".equals(String.valueOf(alg))) {
        continue;
      }
      String kid = String.valueOf(jwk.getOrDefault("kid", ""));
      if (kid.isBlank()) {
        continue;
      }
      try {
        out.put(kid, rsa(String.valueOf(jwk.get("n")), String.valueOf(jwk.get("e"))));
      } catch (Exception ignored) {
        // skip unusable keys
      }
    }
    return out;
  }

  public static Map<String, Object> publicJwks(RSAPublicKey pub, String kid) {
    return Map.of(
        "keys",
        java.util.List.of(
            Map.of(
                "kty", "RSA",
                "use", "sig",
                "kid", kid,
                "alg", "RS256",
                "n", Base64.getUrlEncoder().withoutPadding().encodeToString(pub.getModulus().toByteArray()),
                "e",
                    Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(BigInteger.valueOf(pub.getPublicExponent().longValue()).toByteArray()))));
  }

  private static RSAPublicKey rsa(String n, String e) throws Exception {
    byte[] nBytes = Base64.getUrlDecoder().decode(n);
    byte[] eBytes = Base64.getUrlDecoder().decode(e);
    BigInteger modulus = new BigInteger(1, nBytes);
    BigInteger exponent = new BigInteger(1, eBytes);
    return (RSAPublicKey)
        KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
  }

  private static String roleOf(Object role, Object roles) {
    if (role instanceof String s && ("USER".equals(s) || "ADMIN".equals(s))) {
      return s;
    }
    if (roles instanceof Iterable<?> list) {
      for (Object item : list) {
        if (item instanceof String s && ("USER".equals(s) || "ADMIN".equals(s))) {
          return s;
        }
      }
    }
    return "USER";
  }

  public static final class StaticJwks implements KeySource {
    private final Map<String, Object> doc;

    public StaticJwks(Map<String, Object> doc) {
      this.doc = doc;
    }

    @Override
    public Map<String, Object> jwks() {
      return doc;
    }
  }

  public static final class HttpJwks implements KeySource {
    private final String url;
    private final HttpClient client;
    private final ObjectMapper mapper;

    public HttpJwks(String url, HttpClient client, ObjectMapper mapper) {
      this.url = url;
      this.client = client;
      this.mapper = mapper;
    }

    @Override
    public Map<String, Object> jwks() {
      if (url == null || url.isBlank()) {
        throw AppException.jwksUnavailable();
      }
      try {
        HttpRequest req =
            HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(3)).GET().build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
          throw AppException.jwksUnavailable();
        }
        JsonNode root = mapper.readTree(res.body());
        return mapper.convertValue(root, Map.class);
      } catch (AppException ex) {
        throw ex;
      } catch (Exception ex) {
        throw AppException.jwksUnavailable();
      }
    }
  }
}
