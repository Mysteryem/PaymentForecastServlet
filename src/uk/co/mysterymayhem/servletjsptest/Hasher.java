package uk.co.mysterymayhem.servletjsptest;

import uk.co.mysterymayhem.servletjsptest.DataParser.ParseException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by Mysteryem on 24/04/2017.
 */
public class Hasher {
    private static final MessageDigest SHA_256;
    static {
        try {
            SHA_256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Unable to find SHA-256 hashing algorithm", e);
        }
    }

    // Note, caching byte arrays of frequently occurring strings may provide a performance benefit
    // Also, using a single byte array of say, size 512 (or dynamic) to prevent unnecessary object creation may help
    public static byte[] hash(String merchantPubKey, String payerPubKey, String debitPermissionId, String dueEpoc, String amount) {
        return SHA_256.digest((merchantPubKey + payerPubKey + debitPermissionId + dueEpoc + amount).getBytes());
    }

    private static final int HASH_STRING_LENGTH = 64;
    private static final int HASH_BYTE_LENGTH = 32;

    // Each pair of hex characters is one byte
    public static byte[] fromPreComputedString(String precomputedHash) throws ParseException {
        if (precomputedHash.length() != HASH_STRING_LENGTH) {
            //throw new ParseException("Invalid hash length, got " + precomputedHash.length() + ", expected " + HASH_STRING_LENGTH);
        }
            char[] chars = precomputedHash.toCharArray();
            byte[] bytes = new byte[chars.length / 2];
            for (int i = 0; i < chars.length; i += 2) {
                String twoCharacterString = new String(chars, i, 2);
                // Parse as hex
                // Can't use Byte.parseByte because Java bytes are signed (max value +127, min value -128) and parseByte internally
                // uses Integer.parseInt. In the case of 0xFF, parseByte() -> 255 which is greater than 127 -> NumberFormatException.
                // Thus, I'm using Integer.parseInt manually
                try {
                    byte parsedByte = (byte) (Integer.parseInt(twoCharacterString, 16));
                    // Remember the byte array is half the length
                    bytes[i / 2] = parsedByte;
                } catch (NumberFormatException e) {
                    throw new ParseException("Failed to parse \"" + twoCharacterString + "\" as a hex byte in " + precomputedHash);
                }
            }
            return bytes;
    }

    public static String bytesToNiceString(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte b : bytes) {
            // Java bytes are signed
            // -122 signed = 0x86 = 134 unsigned
            //
            // 0x86 -> 00 00 00 86, but in Java, this is negative so a cast to int (implicit) would produce FF FF FF 86
            // First we bitwise AND 0xFF (00 00 00 FF), this removes any issues with numeric overflow in the next step
            // Next, we add 0x100 (any number > 0xFF should work, we would just have to change the substring start index)
            // 00 00 00 86 -> 00 00 01 86 -> 186
            // We only care about the last 2 digits. In this case, we've ensured the number is always 3 digits. Thus
            // we can create a substring of the last 2 digits
            builder.append(Integer.toHexString((b & 0xFF) + 0x100).substring(1));
        }
        return builder.toString();
    }
}
