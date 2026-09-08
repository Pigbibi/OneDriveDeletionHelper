package cn.lisiyi.photokeep.core;

import java.io.InputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.function.BooleanSupplier;

public final class ContentHash {
    public static String sha256(InputStream input, long expectedSize, BooleanSupplier stopped) throws Exception {
        if (expectedSize <= 0) throw new IOException("Invalid media size");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[128 * 1024];
        long total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (stopped.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new InterruptedException();
            total += count;
            if (total > expectedSize) throw new IOException("Content size changed");
            digest.update(buffer, 0, count);
        }
        if (total != expectedSize) throw new IOException("Incomplete content");
        StringBuilder result = new StringBuilder(64);
        for (byte b : digest.digest()) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return result.toString();
    }
}
