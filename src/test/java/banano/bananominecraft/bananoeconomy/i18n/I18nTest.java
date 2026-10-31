package banano.bananominecraft.bananoeconomy.i18n;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link I18n}.
 *
 * <p>Uses the test-class classloader so that the {@code lang/messages.properties}
 * file in {@code src/main/resources} is visible (Maven puts it on the test classpath
 * automatically).</p>
 */
class I18nTest
{
    private I18n i18n;

    @BeforeEach
    void setUp()
    {
        // Use the test classloader — src/main/resources is on the test classpath
        i18n = new I18n(I18nTest.class.getClassLoader());
    }

    // -------------------------------------------------------------------------
    // parseMinecraftLocale
    // -------------------------------------------------------------------------

    @Test
    void parseMinecraftLocale_null_returnsEnglish()
    {
        assertEquals(Locale.ENGLISH, I18n.parseMinecraftLocale(null));
    }

    @Test
    void parseMinecraftLocale_blank_returnsEnglish()
    {
        assertEquals(Locale.ENGLISH, I18n.parseMinecraftLocale(""));
        assertEquals(Locale.ENGLISH, I18n.parseMinecraftLocale("   "));
    }

    @Test
    void parseMinecraftLocale_uppercaseCountry_normalised()
    {
        Locale result = I18n.parseMinecraftLocale("en_US");
        assertEquals("en", result.getLanguage());
        assertEquals("US", result.getCountry());
    }

    @Test
    void parseMinecraftLocale_lowercaseCountry_normalisedToUppercase()
    {
        // Minecraft sometimes sends "de_de" with a lowercase country code
        Locale result = I18n.parseMinecraftLocale("de_de");
        assertEquals("de", result.getLanguage());
        assertEquals("DE", result.getCountry());
    }

    @Test
    void parseMinecraftLocale_mixedCase_normalised()
    {
        Locale result = I18n.parseMinecraftLocale("fr_FR");
        assertEquals("fr", result.getLanguage());
        assertEquals("FR", result.getCountry());
    }

    @Test
    void parseMinecraftLocale_languageOnly_noCountry()
    {
        Locale result = I18n.parseMinecraftLocale("fr");
        assertEquals("fr",  result.getLanguage());
        assertEquals("",    result.getCountry(),
                "Locale created from a bare language tag should have no country");
    }

    @Test
    void parseMinecraftLocale_languageAlwaysLowercase()
    {
        // Defensive: even if Minecraft somehow sends uppercase language
        Locale result = I18n.parseMinecraftLocale("EN_US");
        assertEquals("en", result.getLanguage(),
                "Language component should always be lower-cased");
    }

    // -------------------------------------------------------------------------
    // get — key lookup
    // -------------------------------------------------------------------------

    @Test
    void get_knownKey_returnsValue()
    {
        String result = i18n.get(Locale.ENGLISH, "join.welcome");
        assertEquals("This server is running BananoEconomy!", result);
    }

    @Test
    void get_missingKey_returnsKeyAsLiteral()
    {
        // Missing keys must never throw — they fall back to the key string itself
        String result = i18n.get(Locale.ENGLISH, "nonexistent.key.xyz");
        assertEquals("nonexistent.key.xyz", result,
                "Missing key should be returned verbatim so the plugin never crashes");
    }

    @Test
    void get_withSingleArg_substituted()
    {
        // balance.current=Your current balance is: {0} bans
        String result = i18n.get(Locale.ENGLISH, "balance.current", "42.5");
        assertTrue(result.contains("42.5"),
                "Formatted value '42.5' should appear in the result");
        assertTrue(result.contains("bans"),
                "Static text 'bans' should still appear in the result");
    }

    @Test
    void get_withMultipleArgs_allSubstituted()
    {
        // tip.sending=Tipping {0} with {1} bans.
        String result = i18n.get(Locale.ENGLISH, "tip.sending", "Steve", "10");
        assertTrue(result.contains("Steve"), "Player name should be substituted");
        assertTrue(result.contains("10"),    "Amount should be substituted");
    }

    @Test
    void get_apostropheInValue_renderedCorrectly()
    {
        // tip.amount_not_positive=Amount (''{0}'') has to be greater than 0
        // MessageFormat requires '' for a literal apostrophe — verify it round-trips
        String result = i18n.get(Locale.ENGLISH, "tip.amount_not_positive", "-5");
        assertTrue(result.contains("'"),  "Literal apostrophe should appear in the output");
        assertTrue(result.contains("-5"), "Argument should be substituted");
    }

    @Test
    void get_noArgs_overload_returnsValueWithoutFormatting()
    {
        // Convenience overload with no varargs
        String result = i18n.get(Locale.ENGLISH, "history.no_records");
        assertEquals("No records found!", result);
    }

    // -------------------------------------------------------------------------
    // get — locale fallback
    // -------------------------------------------------------------------------

    @Test
    void get_unknownLocale_fallsBackToDefaultBundle()
    {
        // Klingon is not a supported locale — should fall back to messages.properties
        Locale klingon = new Locale("tlh", "KL");
        String result = i18n.get(klingon, "join.welcome");
        assertEquals("This server is running BananoEconomy!", result,
                "Unknown locale should fall back to the default English bundle");
    }

    @Test
    void get_germanLocale_returnsGermanTranslation()
    {
        // Verify that messages_de_DE.properties is picked up
        Locale de = new Locale("de", "DE");
        String result = i18n.get(de, "join.welcome");
        assertFalse(result.equals("This server is running BananoEconomy!"),
                "German locale should not return the English string");
        assertTrue(result.contains("BananoEconomy"),
                "BananoEconomy should still appear in the German translation");
    }
}
