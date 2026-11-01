package banano.bananominecraft.bananoeconomy.wallet.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HexTest
{
    @Test
    void encode_upperCase()
    {
        assertEquals("00FF7F80A5", Hex.encode(new byte[]{0, -1, 127, -128, (byte) 0xa5}));
        assertEquals("", Hex.encode(new byte[0]));
    }

    @Test
    void decode_eitherCase_roundTrips()
    {
        assertArrayEquals(new byte[]{0, -1, 127, -128, (byte) 0xa5}, Hex.decode("00ff7F80a5"));
        assertArrayEquals(new byte[0], Hex.decode(""));
    }

    @Test
    void decode_invalid_throws()
    {
        assertThrows(IllegalArgumentException.class, () -> Hex.decode("abc"));
        assertThrows(IllegalArgumentException.class, () -> Hex.decode("zz"));
        assertThrows(IllegalArgumentException.class, () -> Hex.decode("0x"));
        assertThrows(IllegalArgumentException.class, () -> Hex.decode(null));
    }
}
