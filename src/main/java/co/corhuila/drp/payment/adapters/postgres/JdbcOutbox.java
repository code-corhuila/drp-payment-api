package co.corhuila.drp.payment.adapters.postgres;

import co.corhuila.drp.payment.app.Outbox;
import co.corhuila.drp.payment.app.OutboxEvent;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

/** payment.outbox. No DDL here (Anexo J). */
public final class JdbcOutbox implements Outbox {
  private final DataSource ds;

  public JdbcOutbox(DataSource ds) {
    this.ds = ds;
  }

  @Override
  public void append(OutboxEvent event) {
    String sql =
        """
        INSERT INTO outbox (
          event_id, event_type, aggregate_id, aggregate_type, topic,
          occurred_at, version, payload, correlation_id, published_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, NULL)
        """;
    try (var c = ds.getConnection();
        var ps = c.prepareStatement(sql)) {
      ps.setObject(1, UUID.fromString(event.eventId()));
      ps.setString(2, event.eventType());
      ps.setObject(3, UUID.fromString(event.aggregateId()));
      ps.setString(4, event.aggregateType());
      ps.setString(5, event.topic());
      ps.setTimestamp(6, Timestamp.from(event.occurredAt()));
      ps.setInt(7, event.version());
      ps.setString(8, event.payloadJson());
      ps.setString(9, event.correlationId());
      ps.executeUpdate();
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public List<OutboxEvent> unpublished() {
    String sql =
        """
        SELECT event_id, event_type, aggregate_id, aggregate_type, topic,
               occurred_at, version, payload::text, correlation_id
        FROM outbox
        WHERE published_at IS NULL
        ORDER BY occurred_at
        """;
    try (var c = ds.getConnection();
        var ps = c.prepareStatement(sql);
        var rs = ps.executeQuery()) {
      List<OutboxEvent> out = new ArrayList<>();
      while (rs.next()) {
        Instant at = rs.getTimestamp("occurred_at").toInstant();
        out.add(
            new OutboxEvent(
                rs.getObject("event_id", UUID.class).toString(),
                rs.getString("event_type"),
                rs.getObject("aggregate_id", UUID.class).toString(),
                rs.getString("aggregate_type"),
                rs.getString("topic"),
                at,
                rs.getInt("version"),
                rs.getString("payload"),
                rs.getString("correlation_id"),
                false));
      }
      return out;
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void markPublished(String eventId) {
    try (var c = ds.getConnection();
        var ps = c.prepareStatement("UPDATE outbox SET published_at = NOW() WHERE event_id = ?")) {
      ps.setObject(1, UUID.fromString(eventId));
      ps.executeUpdate();
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }
}
