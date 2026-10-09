package engine;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Separate personal bests; never mixes survival scores with campaign scores. */
public final class EndlessRecords {
    private final Path path;
    private long score, seconds, kills;
    private String status = "";

    public EndlessRecords(Path path) {
        this.path = path.toAbsolutePath();
        if (!Files.exists(path)) return;
        Properties data = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            data.load(input);
            long loadedScore = read(data, "score");
            long loadedSeconds = read(data, "seconds");
            long loadedKills = read(data, "kills");
            score = loadedScore;
            seconds = loadedSeconds;
            kills = loadedKills;
        } catch (IOException | IllegalArgumentException ex) {
            status = "Records unavailable; new run is safe.";
            Core.getLogger().warning("Cannot read endless records: " + ex.getMessage());
        }
    }

    private long read(Properties data, String key) {
        long value = Long.parseLong(data.getProperty(key, "0"));
        if (value < 0) throw new IllegalArgumentException("Negative record");
        return value;
    }

    public boolean save(EndlessRun run) {
        score = Math.max(score, run.getScore());
        seconds = Math.max(seconds, run.getSeconds());
        kills = Math.max(kills, run.getKills());
        Properties data = new Properties();
        data.setProperty("score", Long.toString(score));
        data.setProperty("seconds", Long.toString(seconds));
        data.setProperty("kills", Long.toString(kills));
        Path temporary = null;
        try {
            temporary = Files.createTempFile(path.getParent(), "endless-", ".tmp");
            try (OutputStream output = Files.newOutputStream(temporary)) {
                data.store(output, "Endless mode personal bests");
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            status = "Personal bests saved";
            return true;
        } catch (IOException ex) {
            status = "Save failed; records kept this session.";
            Core.getLogger().warning("Cannot save endless records: " + ex.getMessage());
            return false;
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException ex) { Core.getLogger().fine(ex.getMessage()); }
            }
        }
    }

    public long getScore() { return score; }
    public long getSeconds() { return seconds; }
    public long getKills() { return kills; }
    public String getStatus() { return status; }
}
