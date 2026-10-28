import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data access for localized units (translation-table approach) and saved calculations.
 * Missing translations fall back to English.
 */
public class UnitDao {

    public record Unit(String code, String name, String description) {}

    public record HistoryRow(double distance, String distanceUnit,
                             double speed, String speedUnit, double timeHours) {}

    private static final String FALLBACK_LANG = "en";

    private final String url;
    private final String user;
    private final String password;

    public UnitDao(String url, String user, String password) {
        this.url = url;
        this.user = "root";
        this.password = "Test12";
    }

    /** Builds a DAO from DB_HOST / DB_PORT / DB_NAME / DB_USER / DB_PASSWORD (set in the Kubernetes yaml). */
    public static UnitDao fromEnvironment() {
        String host = env("DB_HOST", "host.docker.internal");
        String port = env("DB_PORT", "3306");
        String name = env("DB_NAME", "time_calculator");
        String url = "jdbc:mariadb://" + host + ":" + port + "/" + name
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8";
        return new UnitDao(url, env("DB_USER", "root"), env("DB_PASSWORD", "Test12"));
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /** All units in the given language (English where a translation is missing), keyed by unit code. */
    public Map<String, Unit> findAll(String lang) throws SQLException {
        String sql = """
                SELECT u.code,
                       COALESCE(t.name, d.name)               AS name,
                       COALESCE(t.description, d.description) AS description
                FROM unit u
                LEFT JOIN unit_translation t ON t.unit_id = u.id AND t.lang = ?
                LEFT JOIN unit_translation d ON d.unit_id = u.id AND d.lang = ?
                """;
        Map<String, Unit> result = new HashMap<>();
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, lang);
            ps.setString(2, FALLBACK_LANG);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String code = rs.getString("code");
                    String name = rs.getString("name");
                    result.put(code, new Unit(code, name != null ? name : code, rs.getString("description")));
                }
            }
        }
        return result;
    }

    /** Saves one calculation, linked to the distance and speed units by foreign key. */
    public void saveCalculation(double distance, double speed, double timeHours, String lang,
                                String distanceUnitCode, String speedUnitCode) throws SQLException {
        String sql = """
                INSERT INTO calculation
                    (distance, speed, time_hours, language, distance_unit_id, speed_unit_id)
                VALUES (?, ?, ?, ?,
                    (SELECT id FROM unit WHERE code = ?),
                    (SELECT id FROM unit WHERE code = ?))
                """;
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, distance);
            ps.setDouble(2, speed);
            ps.setDouble(3, timeHours);
            ps.setString(4, lang);
            ps.setString(5, distanceUnitCode);
            ps.setString(6, speedUnitCode);
            ps.executeUpdate();
        }
    }

    /** Latest calculations with unit names in the requested language. */
    public List<HistoryRow> lastCalculations(String lang, int limit) throws SQLException {
        String sql = """
                SELECT c.distance, c.speed, c.time_hours,
                       COALESCE(dt.name, de.name) AS distance_unit,
                       COALESCE(st.name, se.name) AS speed_unit
                FROM calculation c
                LEFT JOIN unit_translation dt ON dt.unit_id = c.distance_unit_id AND dt.lang = ?
                LEFT JOIN unit_translation de ON de.unit_id = c.distance_unit_id AND de.lang = ?
                LEFT JOIN unit_translation st ON st.unit_id = c.speed_unit_id    AND st.lang = ?
                LEFT JOIN unit_translation se ON se.unit_id = c.speed_unit_id    AND se.lang = ?
                ORDER BY c.id DESC
                LIMIT ?
                """;
        List<HistoryRow> rows = new ArrayList<>();
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, lang);
            ps.setString(2, FALLBACK_LANG);
            ps.setString(3, lang);
            ps.setString(4, FALLBACK_LANG);
            ps.setInt(5, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new HistoryRow(rs.getDouble("distance"), rs.getString("distance_unit"),
                            rs.getDouble("speed"), rs.getString("speed_unit"), rs.getDouble("time_hours")));
                }
            }
        }
        return rows;
    }
}
