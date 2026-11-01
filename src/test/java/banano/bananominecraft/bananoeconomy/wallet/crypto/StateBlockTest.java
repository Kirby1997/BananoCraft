package banano.bananominecraft.bananoeconomy.wallet.crypto;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

class StateBlockTest
{
    /**
     * private key, previous, representative, balance, link, work, hash, signature. Produced by the
     * jNano 2.20.1 differential run (296 random blocks: hash, signature, cross-verification and
     * RPC JSON all equal). Rows: open, send to balance 0, change (balance 2^127 - 1), open.
     */
    private static final String[][] BLOCK_VECTORS = {
            {"30203966DDD89A3AA7BD68A3F4759B0D7500A194CF926E184EB404FB7B637670",
                    "0000000000000000000000000000000000000000000000000000000000000000",
                    "ban_38i8jdbdk67w7iserxy74e3jbkefa1pbi9c8gax83y1ngyz5wfgwhmnsn6de", "5488255762",
                    "79EEC25C5B872EC74B8A22CE5511230DCA99130D6DD9D313D0943D6D19CEA74E", "eed9c85be6240936",
                    "86B0ACB9D132C059B652C01B4176DCFA9A81987004D8C5D65586ABC27A5F462B",
                    "714ED2355AD4931C870D8B933CD4C2D4D8E46212E8885CBD2C97B98B207F48E8"
                            + "A1E04108047AB31ED97BA4957D58DE0C22966CF297969405ED8676DF31E38E00"},
            {"E3B114591F9367A2B64895EB4D83FB1AE7C7886B00131488B195451163667A9C",
                    "479C6ABC2AC4125059A9D4EAA433F258C1E2E9AA4AA736D1BDBD83192EEDE451",
                    "ban_1fo43rs84nh5w1rnpb6c6u38i6e3mo76a5rmbwrbn4j5y7j8bujyii8dxusk", "0",
                    "FEB36C674E64BC98EC00D858FAA02E5F73FB7E298FCDC9F205FE28380E9CED04", "6cd58ab4c89e4457",
                    "769BE65BE43F2FFA6E436C8E2257235A2FF7C3828E3503EB5C69DFC23BA2B50A",
                    "F4096CE50CEC23D12016260BA17E930795C4D90C7E3F09C8B25FA0B7D0588C08"
                            + "B0BD681606085C74B15DB66CEB0A1F7DC7DACF13BF01904998C2EDCFA9659502"},
            {"7A04BB67D65870847D79966DAF346C873CF51966D4478FB20CF3D4498EC3A6D1",
                    "78165EE55197C63B241E18BE43E5EF12C97D814E7F10D9AEDFE7A3FEBAE0B9D3",
                    "ban_35k46e4141yqukag93aonpinnmu43o4cn8y8oxqrotkcigr5q7i4c4u3qrne",
                    "170141183460469231731687303715884105727",
                    "0000000000000000000000000000000000000000000000000000000000000000", "07d421ba4d938766",
                    "8CE4F6C3FD95D7D8ED125B38241E6143D3DC4B9C4A3D2CADDA704306E3474714",
                    "459D48BF0C71F9BD7AEB2B4AD30105A33ED9389DC4EBF21145543182435B59A0"
                            + "318D038195603EA7180AD24DC21B569D57A8144E1B257669276CA6BC614CB904"},
            {"4E9608A60B536B8C91197430B035D79B99E673B7238A24218C23379078C65B37",
                    "0000000000000000000000000000000000000000000000000000000000000000",
                    "ban_15kumpgey9xw6uq55xgmjwyjfb7y7wsnnnpgpggixrp49j1dd1y94eyjiwtt",
                    "159121561201211140071189314485442685549",
                    "58268BC95F1DAFDB9851E9CAD0CE5259B33FAC93BE97D3FB41CBF33DEBB2F2CE", "0f976fd028f701e4",
                    "1785D987E87F20D57338CC8210141644255831DE224716FD05ED656B0D85AC8B",
                    "A08AE38C05659A864BD3952A8C5B6C1AEE6D5D3D9A0E80BF4B606D41B470E5CC"
                            + "1E826E0BBA5721F869D8EC2BC63106633D51F95089071F70650B1435D003350F"},
    };

    private static StateBlock unsigned(String[] v)
    {
        byte[] sk = Hex.decode(v[0]);
        String account = NanoKeys.address(NanoKeys.publicKey(sk), "ban_");
        return new StateBlock(account, Hex.decode(v[1]), v[2], new BigInteger(v[3]), Hex.decode(v[4]), null, null);
    }

    @Test
    void knownVectors_hashAndSignature()
    {
        for (String[] v : BLOCK_VECTORS)
        {
            StateBlock b = unsigned(v).withWork(Long.parseUnsignedLong(v[5], 16)).signed(Hex.decode(v[0]));
            assertEquals(v[6], b.hashHex());
            assertEquals(v[7], Hex.encode(b.getSignature()));
            assertTrue(b.hasValidSignature());
        }
    }

    @Test
    void balanceAtOrAbove2pow127_hashMatchesSpec()
    {
        // jNano 2.20.1 mis-encodes balances >= 2^127 as 17 bytes, so this vector (maximum balance,
        // 2^128 - 1) was checked against an independent Python hashlib computation of the spec.
        byte[] account = Hex.decode("3690B01491BA148DB351A8DA6729AFACA36193C1D118B89B912692F573A4832F");
        byte[] rep = Hex.decode("435F50BD3516FCA3FBA99BFD63B4993E94A555C557C86A9231868DF08C8CF013");
        StateBlock b = new StateBlock(NanoKeys.address(account, "ban_"),
                Hex.decode("3C1F581D0C3CCC5CD2115D5A6461AAAEA5B23A5A3CC71773736706DCF1FDD967"),
                NanoKeys.address(rep, "ban_"), BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE),
                Hex.decode("528F05F2E95B72E854AEE3767A3A384D56DCCA1F9407395BF82FC9EF9124ECCB"), null, null);
        assertEquals("868B7D640AF5110061D1A012D566603F242F503A7A607BFC6093DDE7E520FDAF", b.hashHex());
    }

    @Test
    void hash_matchesManualPreimage()
    {
        StateBlock b = unsigned(BLOCK_VECTORS[1]);
        byte[] preamble = new byte[32];
        preamble[31] = 6;
        byte[] balance = new byte[16];
        assertArrayEquals(Blake2b.digest(32, preamble, b.getAccountPublicKey(), b.getPrevious(),
                NanoKeys.publicKeyFromAddress(b.getRepresentative()), balance, b.getLink()), b.hash());
    }

    @Test
    void toRpcJson_matchesProcessFormat()
    {
        String[] v = BLOCK_VECTORS[1];
        StateBlock b = unsigned(v).signed(Hex.decode(v[0])).withWork(0x00000000000abcdeL);
        JsonObject j = b.toRpcJson("ban_");
        assertEquals("state", j.get("type").getAsString());
        assertEquals(b.getAccount(), j.get("account").getAsString());
        assertEquals(v[1], j.get("previous").getAsString());
        assertEquals(v[2], j.get("representative").getAsString());
        assertEquals("0", j.get("balance").getAsString());
        assertEquals(v[4], j.get("link").getAsString());
        assertEquals(NanoKeys.address(Hex.decode(v[4]), "ban_"), j.get("link_as_account").getAsString());
        assertEquals(v[7], j.get("signature").getAsString());
        assertEquals("00000000000abcde", j.get("work").getAsString());
        assertEquals(9, j.size());
    }

    @Test
    void toRpcJson_requiresSignatureAndWork()
    {
        String[] v = BLOCK_VECTORS[0];
        assertThrows(IllegalStateException.class, () -> unsigned(v).toRpcJson("ban_"));
        assertThrows(IllegalStateException.class, () -> unsigned(v).withWork(1).toRpcJson("ban_"));
        assertThrows(IllegalStateException.class, () -> unsigned(v).signed(Hex.decode(v[0])).toRpcJson("ban_"));
    }

    @Test
    void signed_withForeignKey_throws()
    {
        StateBlock b = unsigned(BLOCK_VECTORS[0]);
        assertThrows(IllegalArgumentException.class, () -> b.signed(Hex.decode(BLOCK_VECTORS[1][0])));
    }

    @Test
    void workRoot_isAccountKeyForOpenElsePrevious()
    {
        StateBlock open = unsigned(BLOCK_VECTORS[0]);
        assertTrue(open.isOpen());
        assertArrayEquals(open.getAccountPublicKey(), open.workRoot());
        StateBlock send = unsigned(BLOCK_VECTORS[1]);
        assertFalse(send.isOpen());
        assertArrayEquals(Hex.decode(BLOCK_VECTORS[1][1]), send.workRoot());
    }

    @Test
    void constructor_validatesFields()
    {
        String acct = NanoKeys.address(new byte[32], "ban_");
        byte[] z = new byte[32];
        BigInteger max = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE);
        assertDoesNotThrow(() -> new StateBlock(acct, z, acct, max, z, null, null));
        assertThrows(IllegalArgumentException.class, () -> new StateBlock(acct, z, acct, max.add(BigInteger.ONE), z, null, null));
        assertThrows(IllegalArgumentException.class, () -> new StateBlock(acct, z, acct, BigInteger.valueOf(-1), z, null, null));
        assertThrows(IllegalArgumentException.class, () -> new StateBlock(acct, z, acct, null, z, null, null));
        assertThrows(IllegalArgumentException.class, () -> new StateBlock("ban_bad", z, acct, max, z, null, null));
        assertThrows(IllegalArgumentException.class, () -> new StateBlock(acct, z, "ban_bad", max, z, null, null));
        assertThrows(IllegalArgumentException.class, () -> new StateBlock(acct, new byte[31], acct, max, z, null, null));
        assertThrows(IllegalArgumentException.class, () -> new StateBlock(acct, z, acct, max, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new StateBlock(acct, z, acct, max, z, new byte[63], null));
    }

    @Test
    void immutable_defensiveCopies()
    {
        String[] v = BLOCK_VECTORS[1];
        byte[] previous = Hex.decode(v[1]);
        byte[] sk = Hex.decode(v[0]);
        StateBlock b = new StateBlock(NanoKeys.address(NanoKeys.publicKey(sk), "ban_"), previous, v[2],
                BigInteger.ZERO, Hex.decode(v[4]), null, null);
        String hash = b.hashHex();
        previous[0] ^= 1;
        b.getPrevious()[0] ^= 1;
        b.getLink()[0] ^= 1;
        b.hash()[0] ^= 1;
        assertEquals(hash, b.hashHex());
        StateBlock s = b.signed(sk);
        s.getSignature()[0] ^= 1;
        assertTrue(s.hasValidSignature());
        assertNull(b.getSignature());
        assertNull(b.getWork());
        assertEquals(7L, s.withWork(7).getWork());
        assertNotNull(s.withWork(7).getSignature(), "withWork keeps signature");
        assertEquals(7L, b.withWork(7).signed(sk).getWork(), "signed keeps work");
    }

    @Test
    void fromRpcJson_roundTripsVectors()
    {
        for (String[] v : BLOCK_VECTORS)
        {
            byte[] sk = Hex.decode(v[0]);
            StateBlock b = new StateBlock(NanoKeys.address(NanoKeys.publicKey(sk), "ban_"), Hex.decode(v[1]), v[2],
                    new BigInteger(v[3]), Hex.decode(v[4]), null, null).signed(sk)
                    .withWork(Long.parseUnsignedLong(v[5], 16));
            StateBlock parsed = StateBlock.fromRpcJson(b.toRpcJson("ban_"));
            assertEquals(v[6], parsed.hashHex());
            assertEquals(v[7], Hex.encode(parsed.getSignature()));
            assertTrue(parsed.hasValidSignature());
            assertEquals(b.getWork(), parsed.getWork());
            assertEquals(new BigInteger(v[3]), parsed.getBalance());
        }
    }

    @Test
    void fromRpcJson_rejectsMalformedAndDetectsTampering()
    {
        String[] v = BLOCK_VECTORS[1];
        byte[] sk = Hex.decode(v[0]);
        StateBlock b = new StateBlock(NanoKeys.address(NanoKeys.publicKey(sk), "ban_"), Hex.decode(v[1]), v[2],
                new BigInteger(v[3]), Hex.decode(v[4]), null, null).signed(sk).withWork(1L);
        JsonObject json = b.toRpcJson("ban_");

        JsonObject legacy = json.deepCopy();
        legacy.addProperty("type", "send");
        assertThrows(IllegalArgumentException.class, () -> StateBlock.fromRpcJson(legacy));
        JsonObject hexBalance = json.deepCopy();
        hexBalance.addProperty("balance", "0000000000000000000000000000000A");
        assertThrows(IllegalArgumentException.class, () -> StateBlock.fromRpcJson(hexBalance));
        JsonObject negative = json.deepCopy();
        negative.addProperty("balance", "-1");
        assertThrows(IllegalArgumentException.class, () -> StateBlock.fromRpcJson(negative));
        JsonObject noSig = json.deepCopy();
        noSig.remove("signature");
        assertThrows(IllegalArgumentException.class, () -> StateBlock.fromRpcJson(noSig));
        JsonObject shortLink = json.deepCopy();
        shortLink.addProperty("link", "00");
        assertThrows(IllegalArgumentException.class, () -> StateBlock.fromRpcJson(shortLink));
        assertThrows(IllegalArgumentException.class, () -> StateBlock.fromRpcJson(null));

        JsonObject noWork = json.deepCopy();
        noWork.remove("work");
        assertNull(StateBlock.fromRpcJson(noWork).getWork());

        // A tampered balance changes the recomputed hash and breaks the signature.
        JsonObject tampered = json.deepCopy();
        tampered.addProperty("balance", "1000");
        StateBlock t = StateBlock.fromRpcJson(tampered);
        assertNotEquals(v[6], t.hashHex());
        assertFalse(t.hasValidSignature());
    }
}
