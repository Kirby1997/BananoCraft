package banano.bananominecraft.bananoeconomy.helpers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StringHelperTest
{
    // -------------------------------------------------------------------------
    // padLeft — right-aligns text within a fixed-width field
    // -------------------------------------------------------------------------

    @Test
    void padLeft_shortString_leadsWithSpaces()
    {
        assertEquals("   Hi", StringHelper.padLeft("Hi", 5));
    }

    @Test
    void padLeft_exactLength_unchanged()
    {
        assertEquals("Hello", StringHelper.padLeft("Hello", 5));
    }

    @Test
    void padLeft_longString_truncatedToLength()
    {
        // Overlong input is cut to the requested length (left-most chars kept)
        assertEquals("Hello", StringHelper.padLeft("Hello World", 5));
    }

    @Test
    void padLeft_emptyString_returnsAllSpaces()
    {
        assertEquals("   ", StringHelper.padLeft("", 3));
    }

    @Test
    void padLeft_zeroLength_returnsEmpty()
    {
        assertEquals("", StringHelper.padLeft("", 0));
    }

    // -------------------------------------------------------------------------
    // padRight — left-aligns text within a fixed-width field
    // -------------------------------------------------------------------------

    @Test
    void padRight_shortString_trailsWithSpaces()
    {
        assertEquals("Hi   ", StringHelper.padRight("Hi", 5));
    }

    @Test
    void padRight_exactLength_unchanged()
    {
        assertEquals("Hello", StringHelper.padRight("Hello", 5));
    }

    @Test
    void padRight_longString_truncatedToLength()
    {
        assertEquals("Hello", StringHelper.padRight("Hello World", 5));
    }

    @Test
    void padRight_emptyString_returnsAllSpaces()
    {
        assertEquals("   ", StringHelper.padRight("", 3));
    }

    @Test
    void padRight_zeroLength_returnsEmpty()
    {
        assertEquals("", StringHelper.padRight("", 0));
    }

    // -------------------------------------------------------------------------
    // left — returns the first N characters (no padding)
    // -------------------------------------------------------------------------

    @Test
    void left_longString_returnsFirstNChars()
    {
        assertEquals("Hello", StringHelper.left("Hello World", 5));
    }

    @Test
    void left_shortString_returnedUnchanged()
    {
        assertEquals("Hi", StringHelper.left("Hi", 5));
    }

    @Test
    void left_exactLength_returnedUnchanged()
    {
        assertEquals("Hello", StringHelper.left("Hello", 5));
    }

    // -------------------------------------------------------------------------
    // right — returns the last N characters (no padding)
    // -------------------------------------------------------------------------

    @Test
    void right_longString_returnsLastNChars()
    {
        assertEquals("World", StringHelper.right("Hello World", 5));
    }

    @Test
    void right_shortString_returnedUnchanged()
    {
        assertEquals("Hi", StringHelper.right("Hi", 5));
    }

    @Test
    void right_exactLength_returnedUnchanged()
    {
        assertEquals("Hello", StringHelper.right("Hello", 5));
    }
}
