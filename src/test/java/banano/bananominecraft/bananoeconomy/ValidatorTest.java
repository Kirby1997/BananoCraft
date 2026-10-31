package banano.bananominecraft.bananoeconomy;

import banano.bananominecraft.bananoeconomy.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidatorTest
{
    // A known-valid ban_ address: prefix + 59 chars from the allowed alphabet
    private static final String VALID_ADDRESS =
            "ban_3t6k35gi95xu6tergt6p69ck76ogmitsa8mnijtpxm9fkcm736xtoncuohr3";

    @Test
    void validAddress_returnsTrue()
    {
        assertTrue(Validator.validateAddress(VALID_ADDRESS));
    }

    @Test
    void wrongPrefix_returnsFalse()
    {
        // nano_ prefix should not match
        String nano = "nano_3t6k35gi95xu6tergt6p69ck76ogmitsa8mnijtpxm9fkcm736xtoncuohr3";
        assertFalse(Validator.validateAddress(nano));
    }

    @Test
    void tooShort_returnsFalse()
    {
        assertFalse(Validator.validateAddress("ban_3t6k35gi95xu6tergt6p69ck76ogmitsa8mnijtpxm9fkcm736xtoncuoh"));
    }

    @Test
    void tooLong_returnsFalse()
    {
        assertFalse(Validator.validateAddress(VALID_ADDRESS + "x"));
    }

    @Test
    void invalidCharInBody_returnsFalse()
    {
        // Replace a character with an invalid one ('0' is not in the Banano alphabet)
        String invalid = "ban_3t6k35gi95xu6tergt6p69ck76ogmitsa8mnijtpxm9fkcm736xtoncuoh0";
        assertFalse(Validator.validateAddress(invalid));
    }

    @Test
    void invalidStartDigit_returnsFalse()
    {
        // Body must start with 1 or 3; '5' is not valid as the first character
        String invalid = "ban_5t6k35gi95xu6tergt6p69ck76ogmitsa8mnijtpxm9fkcm736xtoncuohr3";
        assertFalse(Validator.validateAddress(invalid));
    }

    @Test
    void emptyString_returnsFalse()
    {
        assertFalse(Validator.validateAddress(""));
    }

    @Test
    void nullInput_returnsFalse()
    {
        // validateAddress is null-safe and returns false rather than throwing
        assertFalse(Validator.validateAddress(null));
    }

    @Test
    void validAddressStartingWithOne_returnsTrue()
    {
        // Both '1' and '3' are legal as the first character after ban_
        String address = "ban_1t6k35gi95xu6tergt6p69ck76ogmitsa8mnijtpxm9fkcm736xtoncuohr3";
        assertTrue(Validator.validateAddress(address));
    }

    @Test
    void invalidCharLowercaseL_returnsFalse()
    {
        // 'l' (lowercase L) is absent from the Banano Base32 alphabet
        String invalid = "ban_3t6k35gi95xu6tergt6p69ck76ogmitsa8mnijtpxm9fkcm736xtoncuohl";
        assertFalse(Validator.validateAddress(invalid));
    }

    @Test
    void invalidCharLowercaseV_returnsFalse()
    {
        // 'v' is excluded from the Banano Base32 alphabet
        String invalid = "ban_3t6k35gi95xu6tergt6p69ck76ogmitsa8mnijtpxm9fkcm736xtoncuohv";
        assertFalse(Validator.validateAddress(invalid));
    }
}
