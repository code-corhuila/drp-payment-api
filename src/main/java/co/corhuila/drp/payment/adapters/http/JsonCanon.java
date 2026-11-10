package co.corhuila.drp.payment.adapters.http;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

final class JsonCanon {
  private static final ObjectMapper SORTED =
      JsonMapper.builder()
          .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
          .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .build();

  private JsonCanon() {}

  static String normalize(String raw) {
    try {
      Object tree = SORTED.readValue(raw, Object.class);
      return SORTED.writeValueAsString(tree);
    } catch (Exception ex) {
      return raw;
    }
  }
}
