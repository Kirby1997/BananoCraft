package banano.bananominecraft.bananoeconomy.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lightweight internationalisation helper built on top of Java {@link ResourceBundle}.
 *
 * <p>Property files must live in the {@code lang/} resource directory and follow
 * the standard bundle naming convention:</p>
 * <ul>
 *   <li>{@code lang/messages.properties} — default (English) fallback</li>
 *   <li>{@code lang/messages_de_DE.properties} — German (Germany)</li>
 *   <li>{@code lang/messages_fr_FR.properties} — French (France)</li>
 *   <li>… and so on.</li>
 * </ul>
 *
 * <p>Placeholders use {@link MessageFormat} syntax: {@code {0}}, {@code {1}}, …
 * Literal apostrophes in values must be doubled: {@code it''s}.</p>
 *
 * <p>Use {@link #parseMinecraftLocale(String)} to convert the string returned by
 * {@code Player.getLocale()} (e.g. {@code "en_us"} or {@code "de_DE"}) into a
 * {@link Locale} suitable for passing to {@link #get}.</p>
 */
public class I18n
{
    private static final String BUNDLE_NAME = "lang/messages";
    private static final Logger LOGGER = Logger.getLogger(I18n.class.getName());

    private final ClassLoader classLoader;

    /**
     * @param classLoader the classloader used to locate bundle files — in production
     *                    pass {@code plugin.getClass().getClassLoader()}; in tests
     *                    pass {@code YourTest.class.getClassLoader()}.
     */
    public I18n(ClassLoader classLoader)
    {
        this.classLoader = classLoader;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Look up {@code key} in the bundle for {@code locale} and format it with
     * {@code args} using {@link MessageFormat}.
     *
     * <p>Falls back to the default bundle ({@code messages.properties}) when no
     * locale-specific file is available, and returns the raw {@code key} string
     * if the key cannot be found at all (so a missing translation never causes an
     * exception in production).</p>
     *
     * @param locale the target locale (from {@link #parseMinecraftLocale})
     * @param key    message key, e.g. {@code "join.welcome"}
     * @param args   {@link MessageFormat} arguments ({@code {0}}, {@code {1}}, …)
     * @return the formatted, translated message
     */
    public String get(Locale locale, String key, Object... args)
    {
        try
        {
            ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale, classLoader);
            String pattern = bundle.getString(key);

            if (args == null || args.length == 0)
            {
                return pattern;
            }

            return new MessageFormat(pattern, locale).format(args);
        }
        catch (MissingResourceException ex)
        {
            LOGGER.log(Level.WARNING, "Missing i18n key ''{0}'' for locale {1}", new Object[]{ key, locale });
            return key;
        }
    }

    /**
     * Convenience overload — no format arguments.
     *
     * @see #get(Locale, String, Object...)
     */
    public String get(Locale locale, String key)
    {
        return get(locale, key, (Object[]) null);
    }

    // -------------------------------------------------------------------------
    // Locale helpers
    // -------------------------------------------------------------------------

    /**
     * Convert a Minecraft locale string (from {@code Player.getLocale()}) into a
     * Java {@link Locale}.
     *
     * <p>Minecraft sends values such as {@code "en_US"} or {@code "de_de"} (the
     * country part is inconsistently cased across versions). This method normalises
     * to language-lower / country-upper so the {@link ResourceBundle} lookup always
     * matches the standard Java convention.</p>
     *
     * @param mcLocale the raw string returned by {@code Player.getLocale()},
     *                 may be {@code null} or blank
     * @return a well-formed {@link Locale}; defaults to {@link Locale#ENGLISH}
     */
    public static Locale parseMinecraftLocale(String mcLocale)
    {
        if (mcLocale == null || mcLocale.isBlank())
        {
            return Locale.ENGLISH;
        }

        String[] parts = mcLocale.split("_", 2);

        if (parts.length == 2)
        {
            return new Locale(parts[0].toLowerCase(Locale.ROOT), parts[1].toUpperCase(Locale.ROOT));
        }

        return new Locale(parts[0].toLowerCase(Locale.ROOT));
    }
}
