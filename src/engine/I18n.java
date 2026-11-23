package engine;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToIntFunction;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/** Translates presentation text and keeps the selected language with the local game saves. */
public final class I18n {
    public enum Language {
        ENGLISH("English", Locale.ENGLISH),
        CHINESE("简体中文", Locale.SIMPLIFIED_CHINESE),
        KOREAN("한국어", Locale.KOREAN);

        private final String label;
        private final Locale locale;
        private final Map<Font, Font> localizedFonts = new ConcurrentHashMap<>();

        Language(String label, Locale locale) {
            this.label = label;
            this.locale = locale;
        }

        public String label() {
            return label;
        }
    }

    private record Translation(String chinese, String korean) {}

    private static final Logger LOGGER = Logger.getLogger(I18n.class.getName());
    private static final Path SETTINGS = Path.of("ui-settings.properties").toAbsolutePath();
    private static final Map<String, Translation> CATALOG = loadCatalog();
    private static final Pattern NUMBER = Pattern.compile("[0-9]+(?:\\.[0-9]+)?");
    private static volatile Language language = loadLanguage();

    private I18n() {}

    public static Language language() {
        return language;
    }

    public static synchronized void setLanguage(Language selected) {
        if (language == selected)
            return;
        language = selected;

        Path temporary = null;
        try {
            Properties settings = readSettings();
            settings.setProperty("language", selected.locale.getLanguage());
            temporary = Files.createTempFile(SETTINGS.getParent(), "invaders-ui-", ".tmp");
            try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                settings.store(writer, "Invaders UI settings");
            }
            try {
                Files.move(temporary, SETTINGS, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, SETTINGS, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.warning("Unable to save UI settings: " + e.getMessage());
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException e) {
                    LOGGER.warning("Unable to remove temporary UI settings: " + e.getMessage());
                }
            }
        }
    }

    public static void cycle(int direction) {
        Language[] choices = Language.values();
        setLanguage(choices[Math.floorMod(language.ordinal() + direction, choices.length)]);
    }

    /** Translates a whole label or sentence. English is also the fallback. */
    public static String text(String english) {
        Language selected = language;
        if (selected == Language.ENGLISH || english == null || english.isEmpty())
            return english;
        Translation entry = CATALOG.get(english.toUpperCase(Locale.ROOT));
        if (entry == null)
            return english;
        return selected == Language.CHINESE ? entry.chinese() : entry.korean();
    }

    /** Translates the template before inserting scores, names, or other runtime values. */
    public static String format(String englishTemplate, Object... values) {
        return String.format(Locale.ROOT, text(englishTemplate), values);
    }

    /** Translates descriptions whose numeric values come from the game balance files. */
    public static String numberedText(String english) {
        String exact = text(english);
        if (english == null || language == Language.ENGLISH || !exact.equals(english))
            return exact;

        var matcher = NUMBER.matcher(english.strip().replace("%", "%%"));
        String template = matcher.replaceAll("%s");
        String localized = text(template);
        if (localized.equals(template))
            return english;
        List<String> values = new ArrayList<>();
        matcher.reset();
        while (matcher.find())
            values.add(matcher.group());
        return String.format(Locale.ROOT, localized, values.toArray());
    }

    public static Font font(Font original) {
        return font(original, language);
    }

    public static Font font(Font original, Language selected) {
        if (selected == Language.ENGLISH)
            return original;
        Font base = selected == Language.CHINESE ? CjkFonts.CHINESE : CjkFonts.KOREAN;
        return selected.localizedFonts.computeIfAbsent(original,
                font -> base.deriveFont(font.getStyle(), font.getSize2D()));
    }

    private static final class CjkFonts {
        private static final Set<String> INSTALLED = Set.copyOf(List.of(GraphicsEnvironment
                .getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.ENGLISH)));
        private static final Font CHINESE = firstInstalled("Microsoft YaHei UI", "Noto Sans CJK SC",
                "DengXian", "Noto Sans SC");
        private static final Font KOREAN = firstInstalled("Malgun Gothic", "Noto Sans CJK KR",
                "Noto Sans KR");

        private static Font firstInstalled(String... families) {
            for (String family : families)
                if (INSTALLED.contains(family))
                    return new Font(family, Font.PLAIN, 1);
            return new Font(Font.DIALOG, Font.PLAIN, 1);
        }
    }

    /** Break text at locale-specific boundaries, with a character fallback for long words. */
    public static List<String> wrap(String text, int maxWidth, ToIntFunction<String> measure) {
        BreakIterator breaks = BreakIterator.getLineInstance(language.locale);
        breaks.setText(text);
        List<String> lines = new ArrayList<>();
        String line = "";
        for (int start = breaks.first(), end = breaks.next();
                end != BreakIterator.DONE; start = end, end = breaks.next()) {
            String part = text.substring(start, end);
            if (measure.applyAsInt(line + part.stripTrailing()) <= maxWidth) {
                line += part;
                continue;
            }
            if (!line.isBlank())
                lines.add(line.stripTrailing());
            line = "";
            for (int offset = 0; offset < part.length();) {
                int next = offset + Character.charCount(part.codePointAt(offset));
                String character = part.substring(offset, next);
                if (!line.isBlank() && measure.applyAsInt(line + character) > maxWidth) {
                    lines.add(line.stripTrailing());
                    line = "";
                }
                if (!line.isEmpty() || !character.isBlank())
                    line += character;
                offset = next;
            }
        }
        if (!line.isBlank())
            lines.add(line.stripTrailing());
        return lines;
    }

    private static Map<String, Translation> loadCatalog() {
        Map<String, Translation> catalog = new HashMap<>();
        try (InputStream resource = I18n.class.getResourceAsStream("/i18n/catalog.tsv")) {
            if (resource == null)
                throw new IOException("Missing i18n/catalog.tsv");
            try (BufferedReader lines = new BufferedReader(new InputStreamReader(resource,
                    StandardCharsets.UTF_8))) {
                for (String line; (line = lines.readLine()) != null;) {
                    if (line.isBlank() || line.startsWith("#"))
                        continue;
                    String[] columns = line.split("\t", -1);
                    if (columns.length != 3 || columns[0].isBlank()
                            || columns[1].isBlank() || columns[2].isBlank())
                        throw new IOException("Incomplete translation: " + line);
                    String key = columns[0].toUpperCase(Locale.ROOT);
                    if (catalog.putIfAbsent(key, new Translation(columns[1], columns[2])) != null)
                        throw new IOException("Duplicate translation: " + key);
                }
            }
        } catch (IOException e) {
            LOGGER.warning("Unable to load translations: " + e.getMessage());
        }
        return Map.copyOf(catalog);
    }

    private static Properties readSettings() {
        Properties settings = new Properties();
        if (Files.isRegularFile(SETTINGS)) {
            try (var reader = Files.newBufferedReader(SETTINGS, StandardCharsets.UTF_8)) {
                settings.load(reader);
            } catch (IOException | IllegalArgumentException e) {
                LOGGER.warning("Unable to read UI settings: " + e.getMessage());
                settings.clear();
            }
        }
        return settings;
    }

    private static Language loadLanguage() {
        String code = readSettings().getProperty("language");
        for (Language option : Language.values())
            if (option.locale.getLanguage().equals(code))
                return option;
        return Language.ENGLISH;
    }
}
