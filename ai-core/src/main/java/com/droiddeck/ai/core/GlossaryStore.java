package com.droiddeck.ai.core;

import java.io.*;
import java.nio.file.*;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.function.BooleanSupplier;

/** Host-private, single-process state. One atomic file contains active and rollback bundles. */
public final class GlossaryStore {
    private static final int LEGACY_MAGIC = 0x44445347, MAGIC = 0x44445348;
    private static final int DIGEST_BYTES = 32;
    private static final int MAX_STATE = 16 + 2 * SignedGlossary.MAX_BUNDLE + DIGEST_BYTES;
    private GlossaryStore() {}

    private static final class State {
        final int highest;
        final byte[] active, previous;
        State(int highest, byte[] active, byte[] previous) {
            this.highest = highest; this.active = active; this.previous = previous;
        }
    }

    private static byte[] readBundle(DataInputStream in, boolean optional) throws IOException {
        int length = in.readInt();
        if (optional && length == 0) return null;
        if (length <= 0 || length > SignedGlossary.MAX_BUNDLE) throw new IOException("State bundle length");
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return bytes;
    }

    private static State read(Path directory, PublicKey key) throws IOException, GeneralSecurityException {
        if (!Files.notExists(directory, LinkOption.NOFOLLOW_LINKS) &&
                !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unsafe directory");
        Path path = directory.resolve("glossary.state");
        if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) return null;
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > MAX_STATE) throw new IOException("Invalid state file");
        byte[] bytes;
        try (InputStream in = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] block = new byte[8192];
            int n;
            while ((n = in.read(block)) != -1) {
                if (n <= 0 || buffer.size() + n > MAX_STATE) throw new IOException("State size");
                buffer.write(block, 0, n);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length < 16) throw new IOException("Truncated state");
        int schema = new DataInputStream(new ByteArrayInputStream(bytes)).readInt();
        if (schema == MAGIC) {
            if (bytes.length < 16 + DIGEST_BYTES) throw new IOException("Truncated state checksum");
            int end = bytes.length - DIGEST_BYTES;
            byte[] body = java.util.Arrays.copyOf(bytes, end);
            if (!java.security.MessageDigest.isEqual(digest(body),
                    java.util.Arrays.copyOfRange(bytes, end, bytes.length))) throw new IOException("State checksum");
            bytes = body;
        } else if (schema != LEGACY_MAGIC) throw new IOException("State schema");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != schema) throw new IOException("State schema");
            int highest = in.readInt();
            byte[] active = readBundle(in, false), previous = readBundle(in, true);
            int activeRevision = SignedGlossary.verify(active, key).revision;
            int previousRevision = previous == null ? 0 : SignedGlossary.verify(previous, key).revision;
            if (in.read() != -1 || highest < Math.max(activeRevision, previousRevision) || activeRevision == previousRevision) {
                throw new IOException("Invalid state revisions");
            }
            return new State(highest, active, previous);
        }
    }

    private static byte[] digest(byte[] bytes) throws IOException {
        try { return java.security.MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IOException("SHA-256 unavailable", e); }
    }

    private static void write(Path directory, State state, BooleanSupplier allowed) throws IOException {
        if (!allowed.getAsBoolean()) throw new IOException("Update blocked");
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory) || !allowed.getAsBoolean()) throw new IOException("Update blocked");
        Path temp = Files.createTempFile(directory, ".glossary-", ".tmp");
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(buffer)) {
                out.writeInt(MAGIC);
                out.writeInt(state.highest);
                out.writeInt(state.active.length);
                out.write(state.active);
                out.writeInt(state.previous == null ? 0 : state.previous.length);
                if (state.previous != null) out.write(state.previous);
                out.flush();
            }
            byte[] body = buffer.toByteArray();
            if (body.length + DIGEST_BYTES > MAX_STATE) throw new IOException("State size");
            try (FileOutputStream file = new FileOutputStream(temp.toFile())) {
                file.write(body);
                file.write(digest(body));
                file.getFD().sync();
            }
            if (!allowed.getAsBoolean()) throw new IOException("Update blocked");
            Files.move(temp, directory.resolve("glossary.state"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static synchronized SignedGlossary.Bundle active(Path directory, PublicKey key, int host)
            throws IOException, GeneralSecurityException {
        State state = read(directory, key);
        if (state == null) return null;
        SignedGlossary.Bundle bundle = SignedGlossary.verify(state.active, key);
        return bundle.compatible(host) ? bundle : null;
    }

    public static synchronized SignedGlossary.Bundle install(Path directory, byte[] encoded, PublicKey key,
            int host, BooleanSupplier allowed) throws IOException, GeneralSecurityException {
        if (!allowed.getAsBoolean()) throw new IOException("Update blocked");
        if (encoded.length > SignedGlossary.MAX_BUNDLE) throw new IOException("Bundle size");
        byte[] bytes = encoded.clone();
        SignedGlossary.Bundle next = SignedGlossary.verify(bytes, key);
        if (!next.compatible(host)) throw new IOException("Host API mismatch");
        State old = read(directory, key);
        if (old != null && next.revision <= old.highest) throw new IOException("Stale revision");
        write(directory, new State(next.revision, bytes, old == null ? null : old.active), allowed);
        return next;
    }

    public static synchronized SignedGlossary.Bundle rollback(Path directory, PublicKey key, int host,
            BooleanSupplier allowed) throws IOException, GeneralSecurityException {
        if (!allowed.getAsBoolean()) throw new IOException("Rollback blocked");
        State old = read(directory, key);
        if (old == null || old.previous == null) throw new IOException("No rollback bundle");
        SignedGlossary.Bundle prior = SignedGlossary.verify(old.previous, key);
        if (!prior.compatible(host)) throw new IOException("Rollback host API mismatch");
        write(directory, new State(old.highest, old.previous, old.active), allowed);
        return prior;
    }
}
