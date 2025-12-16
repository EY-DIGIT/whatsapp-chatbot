package in.indore.whatsappbot.util;

import java.security.SecureRandom;

public final class NanoIdUtils {
    private static final char[] DEFAULT_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private NanoIdUtils() {}

    public static String randomNanoId(int size) {
        if (size <= 0) throw new IllegalArgumentException("size must be > 0");
        char[] id = new char[size];
        for (int i = 0; i < size; i++) {
            id[i] = DEFAULT_ALPHABET[RANDOM.nextInt(DEFAULT_ALPHABET.length)];
        }
        return new String(id);
    }
}

