package com.kass.vocalanalysistool.model;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sqlite.SQLiteDataSource;

/**
 * Stores and retrieves user acoustic data (formants, plots, and model outputs).
 *
 * <p>This class is responsible for:
 * <ul>
 *   <li>Choosing a stable DB location (dev vs packaged)</li>
 *   <li>Ensuring required tables exist (CREATE TABLE IF NOT EXISTS)</li>
 *   <li>Providing query helpers for the UI</li>
 * </ul>
 *
 * @author Kassie Whitney
 * @version 12/31/2025
 */
public class UserSampleDatabase {

    /**
     * The data source object.
     */
    private SQLiteDataSource myDs;

    /**
     * The resolved DB path on disk.
     */
    private Path myDbPath;

    /**
     * Logger used for debugging.
     */
    private static final Logger MY_LOGGER = Logger.getLogger("User Formant Database");

    /**
     * The DB filename.
     */
    private static final String DB_FILE_NAME = "Vocal_Analysis.db";

    /**
     * Creates the user_formants table if it does not exist.
     *
     * <p>IMPORTANT: Keep this schema aligned with what your Python script writes and
     * what your queries read.
     */
    private static final String CREATE_USER_FORMANTS_TABLE = """
            CREATE TABLE IF NOT EXISTS user_formants (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp TEXT NOT NULL,

                f0_json TEXT,
                f1_json TEXT,
                f2_json TEXT,
                f3_json TEXT,
                f4_json TEXT,

                formant_avg_json TEXT,

                scatter_plot BLOB,

                gender_label TEXT,
                gender_score REAL
            );
            """;

    /**
     * Constructor for the SQL database.
     *
     * @param theDebugger Sets the debugger status flag.
     */
    public UserSampleDatabase(final boolean theDebugger) {
        setDebugger(theDebugger);
        initializeDatabase();
    }

    /**
     * Turns the debugger on or off.
     *
     * @param theDubberStatus The debugger status flag.
     */
    private void setDebugger(final boolean theDubberStatus) {
        if (!theDubberStatus) {
            MY_LOGGER.setLevel(Level.OFF);
        } else {
            MY_LOGGER.setLevel(Level.INFO);
        }
    }

    /**
     * Initializes the database connection and ensures schema exists.
     */
    private void initializeDatabase() {
        try {
            myDbPath = resolveDbPath();

            // Make sure directory exists
            Files.createDirectories(Objects.requireNonNull(myDbPath.getParent()));

            final String dbUrl = "jdbc:sqlite:" + myDbPath.toAbsolutePath();

            myDs = new SQLiteDataSource();
            myDs.setUrl(dbUrl);

            MY_LOGGER.info("Database path: " + myDbPath.toAbsolutePath());
            MY_LOGGER.info("Database connection established successfully");

            ensureSchema();

        } catch (final Exception theException) {
            MY_LOGGER.log(Level.SEVERE, "Failed to initialize database", theException);
            throw new RuntimeException("Failed to initialize database: ", theException);
        }
    }

    /**
     * Resolves a stable DB location:
     *
     * <ul>
     *   <li>Packaged (jpackage): %LOCALAPPDATA%\\VocalAnalysisTool\\Vocal_Analysis.db</li>
     *   <li>Dev (IntelliJ): user.dir\\Vocal_Analysis.db</li>
     * </ul>
     *
     * @return the resolved DB file path.
     */
    private Path resolveDbPath() {
        final String jpackageAppPath = System.getProperty("jpackage.app-path");

        // Packaged EXE: store in LocalAppData (stable + writable)
        if (jpackageAppPath != null && !jpackageAppPath.isBlank()) {
            final String localAppData = System.getenv("LOCALAPPDATA");
            final Path base = (localAppData != null && !localAppData.isBlank())
                    ? Path.of(localAppData)
                    : Path.of(System.getProperty("user.home"));

            return base.resolve("VocalAnalysisTool").resolve(DB_FILE_NAME);
        }

        // Dev mode: keep in project directory (where you expect it)
        return Path.of(System.getProperty("user.dir")).resolve(DB_FILE_NAME);
    }

    /**
     * Ensures required tables exist.
     */
    private void ensureSchema() {
        try (final Connection conn = myDs.getConnection();
             final Statement stmt = conn.createStatement()) {

            stmt.execute(CREATE_USER_FORMANTS_TABLE);

        } catch (final SQLException theEvent) {
            MY_LOGGER.log(Level.SEVERE, "Failed to ensure schema", theEvent);
            throw new RuntimeException("Failed to ensure schema: " + theEvent.getMessage(), theEvent);
        }
    }

    /**
     * Deletes all records from the 'user_formants' table.
     */
    public final void clearDatabase() {
        final String deleteSQL = "DELETE FROM user_formants";
        try (final Connection conn = myDs.getConnection();
             final Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(deleteSQL);
        } catch (final SQLException theEvent) {
            MY_LOGGER.log(Level.SEVERE, "Error clearing database: " + theEvent.getMessage(), theEvent);
        }
    }

    /**
     * Retrieves the formant data from the sqlite database.
     *
     * @return Returns a matrix where the rows are the formants and the columns are the time.
     */
    public final double[][] getFormants() {
        final double[][] results = new double[5][];
        String f0_str = "";
        String f1_str = "";
        String f2_str = "";
        String f3_str = "";
        String f4_str = "";

        final String query = """
                SELECT f0_json, f1_json, f2_json, f3_json, f4_json
                FROM user_formants
                ORDER BY timestamp DESC, id DESC
                LIMIT 1
                """;

        try (final Connection conn = myDs.getConnection();
             final Statement stmt = conn.createStatement();
             final ResultSet rs = stmt.executeQuery(query)) {

            if (rs.next()) {
                f0_str = rs.getString("f0_json");
                f1_str = rs.getString("f1_json");
                f2_str = rs.getString("f2_json");
                f3_str = rs.getString("f3_json");
                f4_str = rs.getString("f4_json");
            }

            if (f0_str == null || f1_str == null || f2_str == null || f3_str == null || f4_str == null
                    || f0_str.isBlank() || f1_str.isBlank() || f2_str.isBlank() || f3_str.isBlank() || f4_str.isBlank()) {
                MY_LOGGER.severe("Formants are empty. Unable to retrieve them!");
                throw new RuntimeException("Formants are empty.");
            }

            results[0] = new Gson().fromJson(f0_str, double[].class);
            results[1] = new Gson().fromJson(f1_str, double[].class);
            results[2] = new Gson().fromJson(f2_str, double[].class);
            results[3] = new Gson().fromJson(f3_str, double[].class);
            results[4] = new Gson().fromJson(f4_str, double[].class);

        } catch (final SQLException theException) {
            MY_LOGGER.log(Level.SEVERE, "Unable to execute SQL query!", theException);
            throw new RuntimeException(theException.getMessage(), theException);
        }

        return results;
    }

    /**
     * Gets the average formant data.
     *
     * @return Returns an array of average formants from f0-f4.
     */
    public final double[] getAverage() {
        String f_Avg = "";

        final String query = """
                SELECT formant_avg_json
                FROM user_formants
                ORDER BY timestamp DESC, id DESC
                LIMIT 1
                """;

        try (final Connection conn = myDs.getConnection();
             final Statement stmt = conn.createStatement();
             final ResultSet rs = stmt.executeQuery(query)) {

            if (rs.next()) {
                f_Avg = rs.getString("formant_avg_json");
            }

            if (f_Avg == null || f_Avg.isBlank()) {
                throw new RuntimeException("No average formant data found!");
            }

            return new Gson().fromJson(f_Avg, double[].class);

        } catch (final SQLException theEvent) {
            MY_LOGGER.log(Level.SEVERE, "Unable to retrieve the average formant data", theEvent);
            throw new RuntimeException("Unable to retrieve the average formant data: " + theEvent.getMessage(), theEvent);
        }
    }

    /**
     * Gets the scatter plot image from the database.
     *
     * @return Returns a binary byte array of the image.
     */
    public final byte[] getScatterPlot() {
        final String query = """
                SELECT scatter_plot, timestamp, id
                FROM user_formants
                ORDER BY timestamp DESC, id DESC
                LIMIT 1
                """;

        try (final Connection conn = myDs.getConnection();
             final PreparedStatement ps = conn.prepareStatement(query);
             final ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                final byte[] bytes = rs.getBytes("scatter_plot");
                if (bytes == null || bytes.length == 0) {
                    throw new RuntimeException("Scatter plot was null/empty!");
                }
                return bytes;
            } else {
                throw new RuntimeException("There are no images present!");
            }

        } catch (final SQLException theEvent) {
            MY_LOGGER.log(Level.SEVERE, "Unable to retrieve the image", theEvent);
            throw new RuntimeException("Unable to get the image: " + theEvent.getMessage(), theEvent);
        }
    }

    /**
     * Retrieves the users gender perception label.
     *
     * @return The gender perception of the users vocal sample.
     */
    public final String getGenderLabel() {
        final String query = """
                SELECT gender_label, timestamp, id
                FROM user_formants
                ORDER BY timestamp DESC, id DESC
                LIMIT 1
                """;

        try (final Connection conn = myDs.getConnection();
             final PreparedStatement ps = conn.prepareStatement(query);
             final ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                final String label = rs.getString("gender_label");
                if (label == null || label.isBlank()) {
                    throw new RuntimeException("Gender label was null/empty!");
                }
                return label;
            }

            throw new RuntimeException("No gender_labels were found!");

        } catch (final SQLException theEvent) {
            MY_LOGGER.log(Level.SEVERE, "Unable to retrieve the gender label", theEvent);
            throw new RuntimeException("Unable to retrieve the gender label: " + theEvent.getMessage(), theEvent);
        }
    }

    /**
     * Retrieves the latest gender score from the users audio sample.
     *
     * @return The gender score.
     */
    public final String getGenderScore() {
        final String query = """
                SELECT gender_score, timestamp, id
                FROM user_formants
                ORDER BY timestamp DESC, id DESC
                LIMIT 1
                """;

        try (final Connection conn = myDs.getConnection();
             final PreparedStatement ps = conn.prepareStatement(query);
             final ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                return rs.getString("gender_score");
            }

            throw new RuntimeException("No gender_score were found!");

        } catch (final SQLException theEvent) {
            MY_LOGGER.log(Level.SEVERE, "Unable to retrieve the gender score", theEvent);
            throw new RuntimeException("Unable to retrieve the gender score: " + theEvent.getMessage(), theEvent);
        }
    }

    /**
     * Gets the latest time stamps (most recent first).
     *
     * @return the list of timestamps.
     */
    public final List<String> getTimeStamp() {
        final List<String> results = new ArrayList<>();

        final String query = """
                SELECT timestamp
                FROM user_formants
                ORDER BY timestamp DESC, id DESC
                """;

        try (final Connection conn = myDs.getConnection();
             final PreparedStatement ps = conn.prepareStatement(query);
             final ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                results.add(rs.getString("timestamp"));
            }

        } catch (final SQLException theEvent) {
            MY_LOGGER.log(Level.SEVERE, "Unable to retrieve time stamp", theEvent);
            throw new RuntimeException("Unable to retrieve time stamp: " + theEvent.getMessage(), theEvent);
        }

        return results;
    }

    /**
     * Get the latest gender score for the day.
     *
     * @return an OptionalDouble object where the result may return a double or empty.
     */
    public final OptionalDouble getLatestScore() {
        final String query = """
                SELECT gender_score
                FROM user_formants
                WHERE date(timestamp) = date('now', 'localtime')
                ORDER BY timestamp DESC, id DESC
                LIMIT 1
                """;

        try (final Connection conn = myDs.getConnection();
             final PreparedStatement ps = conn.prepareStatement(query);
             final ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                final double score = rs.getDouble("gender_score");
                if (rs.wasNull()) {
                    return OptionalDouble.empty();
                }
                return OptionalDouble.of(score);
            }

            return OptionalDouble.empty();

        } catch (final SQLException theEvent) {
            MY_LOGGER.log(Level.SEVERE, "Unable to retrieve latest query", theEvent);
            throw new RuntimeException("Unable to retrieve latest query: " + theEvent.getMessage(), theEvent);
        }
    }

    /**
     * Record of the daily medians.
     *
     * @param theDate the date.
     * @param theMedianGenderScore the median score.
     */
    public record DailyMedian(LocalDate theDate, double theMedianGenderScore) { }

    /**
     * Gets the daily median gender scores.
     *
     * @return returns a list of median gender scores.
     */
    public List<DailyMedian> getLast7dayMedianScore() {

        final String query = """
            WITH ranked AS (
              SELECT
                date(timestamp) AS day,
                gender_score,
                ROW_NUMBER() OVER (
                  PARTITION BY date(timestamp)
                  ORDER BY gender_score
                ) AS rn,
                COUNT(*) OVER (
                  PARTITION BY date(timestamp)
                ) AS cnt
              FROM user_formants
              WHERE date(timestamp) < date('now', 'localtime')
                AND date(timestamp) >= date('now', '-7 days')
            )
            SELECT
              day,
              AVG(gender_score) AS median_gender_score
            FROM ranked
            WHERE rn IN ((cnt + 1) / 2, (cnt + 2) / 2)
            GROUP BY day
            ORDER BY day
            """;

        final List<DailyMedian> results = new ArrayList<>();

        try (final Connection conn = myDs.getConnection();
             final PreparedStatement ps = conn.prepareStatement(query);
             final ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                final LocalDate date = LocalDate.parse(rs.getString("day"));
                final double median = rs.getDouble("median_gender_score");
                if (!rs.wasNull()) {
                    results.add(new DailyMedian(date, median));
                }
            }

            return results;

        } catch (final SQLException theException) {
            MY_LOGGER.log(Level.SEVERE, "Unable to retrieve daily median gender scores", theException);
            throw new RuntimeException("Unable to retrieve daily median gender scores", theException);
        }
    }
}
