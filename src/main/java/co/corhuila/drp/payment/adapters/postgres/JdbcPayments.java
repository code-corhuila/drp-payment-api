package co.corhuila.drp.payment.adapters.postgres;

import co.corhuila.drp.payment.app.PaymentRepository;
import co.corhuila.drp.payment.app.StoredPayment;
import co.corhuila.drp.payment.domain.Money;
import co.corhuila.drp.payment.domain.Payment;
import co.corhuila.drp.payment.domain.PaymentState;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

public final class JdbcPayments implements PaymentRepository {
  private final DataSource ds;

  public JdbcPayments(DataSource ds) {
    this.ds = ds;
  }

  @Override
  public List<StoredPayment> list() {
    try (var c = ds.getConnection();
        var ps = c.prepareStatement(SELECT);
        var rs = ps.executeQuery()) {
      List<StoredPayment> out = new ArrayList<>();
      while (rs.next()) {
        out.add(map(rs));
      }
      return out;
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public Optional<StoredPayment> findById(String id) {
    try (var c = ds.getConnection();
        var ps = c.prepareStatement(SELECT + " WHERE id = ?")) {
      ps.setString(1, id);
      try (var rs = ps.executeQuery()) {
        if (!rs.next()) {
          return Optional.empty();
        }
        return Optional.of(map(rs));
      }
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public Optional<StoredPayment> findOpenByReservation(String reservationId) {
    try (var c = ds.getConnection();
        var ps =
            c.prepareStatement(
                SELECT + " WHERE reservation_id = ? AND state IN ('PENDING','CONFIRMED')")) {
      ps.setString(1, reservationId);
      try (var rs = ps.executeQuery()) {
        if (!rs.next()) {
          return Optional.empty();
        }
        return Optional.of(map(rs));
      }
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void save(StoredPayment row) {
    Payment p = row.payment();
    String sql =
        """
        INSERT INTO payments (
          id, reservation_id, amount_cents, currency, state, idempotency_key,
          provider_reference, created_at, updated_at, user_id)
        VALUES (?,?,?,?,?,?,?,?,?,?)
        ON CONFLICT (id) DO UPDATE SET
          state = EXCLUDED.state,
          provider_reference = EXCLUDED.provider_reference,
          updated_at = EXCLUDED.updated_at,
          user_id = EXCLUDED.user_id
        """;
    try (var c = ds.getConnection();
        var ps = c.prepareStatement(sql)) {
      ps.setObject(1, java.util.UUID.fromString(p.id()));
      ps.setObject(2, java.util.UUID.fromString(p.reservationId()));
      ps.setInt(3, p.amountCents());
      ps.setString(4, p.currency());
      ps.setString(5, p.state().name());
      ps.setString(6, p.idempotencyKey());
      ps.setString(7, p.providerReference());
      ps.setTimestamp(8, Timestamp.from(p.createdAt()));
      ps.setTimestamp(9, Timestamp.from(p.updatedAt()));
      ps.setObject(10, java.util.UUID.fromString(row.ownerUserId()));
      ps.executeUpdate();
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  private static final String SELECT =
      "SELECT id, reservation_id, amount_cents, currency, state, idempotency_key, provider_reference, created_at, updated_at, user_id FROM payments";

  private static StoredPayment map(ResultSet rs) throws SQLException {
    Instant created = rs.getTimestamp("created_at").toInstant();
    Instant updated = rs.getTimestamp("updated_at").toInstant();
    Payment pending =
        Payment.pending(
            rs.getString("id"),
            rs.getString("reservation_id"),
            Money.cop(rs.getInt("amount_cents")),
            rs.getString("idempotency_key"),
            created);
    PaymentState state = PaymentState.valueOf(rs.getString("state"));
    Payment got = pending;
    if (state == PaymentState.CONFIRMED) {
      got = pending.confirm(rs.getString("provider_reference"), updated);
    } else if (state == PaymentState.FAILED) {
      got = pending.fail(updated);
    }
    String owner = rs.getString("user_id");
    return new StoredPayment(got, owner);
  }
}
