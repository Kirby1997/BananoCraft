package banano.bananominecraft.bananoeconomy.wallet.crypto;

import com.google.gson.JsonObject;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Immutable Nano/Banano state block.
 *
 * <p>hash = BLAKE2b-256(preamble || account || previous || representative || balance || link),
 * where the preamble is 32 bytes, all zero except the last (0x06), and the balance is a
 * 128-bit big-endian integer. Send: link = destination public key. Receive/open: link = source
 * send block hash (open: previous all zero). Change: link = 0.</p>
 */
public final class StateBlock
{
    private static final byte[] PREAMBLE = new byte[32];
    private static final BigInteger MAX_BALANCE = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE);

    static
    {
        PREAMBLE[31] = 0x06;
    }

    private final String account;
    private final byte[] accountKey;
    private final byte[] previous;
    private final String representative;
    private final byte[] representativeKey;
    private final BigInteger balance;
    private final byte[] link;
    private final byte[] signature;
    private final Long work;
    private final byte[] hash;

    /**
     * Create a block.
     *
     * @param account           account address (any known prefix)
     * @param previous32        previous block hash, 32 zero bytes for an open block
     * @param representative    representative address (any known prefix)
     * @param balanceRaw        balance after this block, in raw (0..2^128-1)
     * @param link32            link field (see class doc)
     * @param signature64OrNull signature, or null if unsigned
     * @param workOrNull        work nonce, or null if not yet generated
     * @throws IllegalArgumentException if any field is malformed
     */
    public StateBlock(String account, byte[] previous32, String representative, BigInteger balanceRaw,
                      byte[] link32, byte[] signature64OrNull, Long workOrNull)
    {
        this.accountKey = NanoKeys.publicKeyFromAddress(account);
        this.representativeKey = NanoKeys.publicKeyFromAddress(representative);
        NanoKeys.requireLength(previous32, 32, "previous");
        NanoKeys.requireLength(link32, 32, "link");
        if (balanceRaw == null || balanceRaw.signum() < 0 || balanceRaw.compareTo(MAX_BALANCE) > 0)
        {
            throw new IllegalArgumentException("Balance must be 0..2^128-1 raw: " + balanceRaw);
        }
        if (signature64OrNull != null)
        {
            NanoKeys.requireLength(signature64OrNull, 64, "signature");
        }
        this.account = account;
        this.previous = previous32.clone();
        this.representative = representative;
        this.balance = balanceRaw;
        this.link = link32.clone();
        this.signature = signature64OrNull == null ? null : signature64OrNull.clone();
        this.work = workOrNull;
        this.hash = Blake2b.digest(32, PREAMBLE, accountKey, previous, representativeKey,
                balanceBytes(balanceRaw), link);
    }

    /**
     * Rebuild a state block from the JSON form used by {@code process} and returned as {@code contents} by
     * {@code block_info} with {@code json_block: true} ({@code account}, {@code previous}, {@code representative},
     * decimal {@code balance}, {@code link}, {@code signature}, optional hex {@code work}). The hash is recomputed
     * from the fields; nothing is trusted from the source beyond the fields themselves, so callers must still compare
     * {@link #hash()} with the expected hash and check {@link #hasValidSignature()}.
     *
     * @param json the block object
     * @return the block, carrying the signature (and work, if present)
     * @throws IllegalArgumentException if the object is not a well-formed state block
     */
    public static StateBlock fromRpcJson(JsonObject json)
    {
        if (json == null)
        {
            throw new IllegalArgumentException("Block JSON is null");
        }
        if (!"state".equals(field(json, "type", false)))
        {
            throw new IllegalArgumentException("Not a state block: type " + field(json, "type", false));
        }
        String balanceText = field(json, "balance", true);
        for (int i = 0; i < balanceText.length(); i++)
        {
            if (balanceText.charAt(i) < '0' || balanceText.charAt(i) > '9')
            {
                throw new IllegalArgumentException("Balance is not a decimal raw amount: " + balanceText);
            }
        }
        if (balanceText.isEmpty() || balanceText.length() > 39)
        {
            throw new IllegalArgumentException("Balance is not a decimal raw amount: " + balanceText);
        }
        byte[] signature = hexField(json, "signature", 64);
        String workText = field(json, "work", false);
        Long work = null;
        if (workText != null)
        {
            if (workText.length() != 16)
            {
                throw new IllegalArgumentException("Work must be 16 hex digits: " + workText);
            }
            work = Long.parseUnsignedLong(workText, 16);
        }
        return new StateBlock(field(json, "account", true), hexField(json, "previous", 32),
                field(json, "representative", true), new BigInteger(balanceText), hexField(json, "link", 32),
                signature, work);
    }

    private static String field(JsonObject json, String name, boolean required)
    {
        com.google.gson.JsonElement e = json.get(name);
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive())
        {
            if (required)
            {
                throw new IllegalArgumentException("Missing field " + name);
            }
            return null;
        }
        return e.getAsString();
    }

    private static byte[] hexField(JsonObject json, String name, int length)
    {
        String text = field(json, name, true);
        if (text.length() != length * 2)
        {
            throw new IllegalArgumentException("Field " + name + " must be " + length + " bytes of hex");
        }
        return Hex.decode(text);
    }

    /** @return the 32-byte block hash */
    public byte[] hash()
    {
        return hash.clone();
    }

    /** @return the block hash as upper-case hex */
    public String hashHex()
    {
        return Hex.encode(hash);
    }

    /** @return the work root: previous, or the account public key for an open block */
    public byte[] workRoot()
    {
        return isOpen() ? accountKey.clone() : previous.clone();
    }

    /** @return true if previous is all zero (an open block) */
    public boolean isOpen()
    {
        for (byte b : previous)
        {
            if (b != 0)
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Sign this block. The key must belong to the block's account; the signature is verified
     * before it is returned.
     *
     * @param privateKey32 the account's 32-byte private key
     * @return a copy of this block carrying the signature (work is kept)
     * @throws IllegalArgumentException if the key does not belong to the account
     * @throws IllegalStateException    if the fresh signature fails verification
     */
    public StateBlock signed(byte[] privateKey32)
    {
        if (!Arrays.equals(NanoKeys.publicKey(privateKey32), accountKey))
        {
            throw new IllegalArgumentException("Private key does not belong to account " + account);
        }
        byte[] sig = NanoKeys.sign(privateKey32, hash);
        if (!NanoKeys.verify(accountKey, hash, sig))
        {
            throw new IllegalStateException("Signature self-check failed for block " + hashHex());
        }
        return new StateBlock(account, previous, representative, balance, link, sig, work);
    }

    /**
     * @param work the work nonce (not validated here; see {@link Work#isValid})
     * @return a copy of this block carrying {@code work} (signature is kept)
     */
    public StateBlock withWork(long work)
    {
        return new StateBlock(account, previous, representative, balance, link, signature, work);
    }

    /** @return true if the block carries a signature that verifies against its account */
    public boolean hasValidSignature()
    {
        return signature != null && NanoKeys.verify(accountKey, hash, signature);
    }

    /**
     * The block in the JSON form taken by the {@code process} RPC with {@code json_block: true}.
     *
     * @param prefix the address prefix for account fields, e.g. {@code "ban_"}
     * @return the block object
     * @throws IllegalStateException if the block is not signed or has no work
     */
    public JsonObject toRpcJson(String prefix)
    {
        if (signature == null || work == null)
        {
            throw new IllegalStateException("Block " + hashHex() + " must be signed and have work");
        }
        JsonObject o = new JsonObject();
        o.addProperty("type", "state");
        o.addProperty("account", NanoKeys.address(accountKey, prefix));
        o.addProperty("previous", Hex.encode(previous));
        o.addProperty("representative", NanoKeys.address(representativeKey, prefix));
        o.addProperty("balance", balance.toString());
        o.addProperty("link", Hex.encode(link));
        o.addProperty("link_as_account", NanoKeys.address(link, prefix));
        o.addProperty("signature", Hex.encode(signature));
        o.addProperty("work", String.format("%016x", work));
        return o;
    }

    /** @return the account address as given */
    public String getAccount()
    {
        return account;
    }

    /** @return the account public key */
    public byte[] getAccountPublicKey()
    {
        return accountKey.clone();
    }

    /** @return the previous block hash (32 bytes) */
    public byte[] getPrevious()
    {
        return previous.clone();
    }

    /** @return the representative address as given */
    public String getRepresentative()
    {
        return representative;
    }

    /** @return the balance after this block, in raw */
    public BigInteger getBalance()
    {
        return balance;
    }

    /** @return the link field (32 bytes) */
    public byte[] getLink()
    {
        return link.clone();
    }

    /** @return the 64-byte signature, or null if unsigned */
    public byte[] getSignature()
    {
        return signature == null ? null : signature.clone();
    }

    /** @return the work nonce, or null if none */
    public Long getWork()
    {
        return work;
    }

    @Override
    public String toString()
    {
        return "StateBlock{hash=" + hashHex() + ", account=" + account + ", previous=" + Hex.encode(previous)
                + ", representative=" + representative + ", balance=" + balance + ", link=" + Hex.encode(link)
                + ", signed=" + (signature != null) + ", work=" + (work == null ? "none" : String.format("%016x", work))
                + "}";
    }

    private static byte[] balanceBytes(BigInteger balance)
    {
        byte[] raw = balance.toByteArray();
        byte[] out = new byte[16];
        // toByteArray may carry a leading sign byte; copy the low 16 bytes right-aligned
        int n = Math.min(raw.length, 16);
        System.arraycopy(raw, raw.length - n, out, 16 - n, n);
        return out;
    }
}
