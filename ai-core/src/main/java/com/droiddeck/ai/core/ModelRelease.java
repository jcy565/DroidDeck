package com.droiddeck.ai.core;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/** Signed metadata and bounded, host-private model data; no inference or executable loading. */
public final class ModelRelease {
    private ModelRelease() {}
    private static final int MAGIC = 0x4444414d, STATE = 0x44444d53, HEADER = 64;
    public static final int MAX_MANIFEST = 1024;
    public static final long MAX_BYTES = 1024L * 1024 * 1024;

    public static final class Manifest {
        public final int revision, minHost, maxHost, runtimeApi;
        public final long bytes;
        public final String sha256;
        private final byte[] signed;
        private Manifest(int revision, int minHost, int maxHost, int runtimeApi, long bytes, byte[] digest, byte[] signed) {
            this.revision = revision; this.minHost = minHost; this.maxHost = maxHost; this.runtimeApi = runtimeApi;
            this.bytes = bytes; this.sha256 = hex(digest); this.signed = signed;
        }
        public boolean compatible(int host, int runtime) { return host >= minHost && host <= maxHost && runtimeApi == runtime; }
    }

    private static String hex(byte[] data) {
        StringBuilder result = new StringBuilder(data.length * 2);
        for (byte b : data) result.append(String.format(Locale.ROOT, "%02x", b & 255));
        return result.toString();
    }

    public static Manifest verify(byte[] encoded, PublicKey key) throws IOException, GeneralSecurityException {
        if (!(key instanceof RSAPublicKey)) throw new GeneralSecurityException("Trusted RSA key required");
        int bits = ((RSAPublicKey) key).getModulus().bitLength();
        if (bits < 2048 || bits > 4096) throw new GeneralSecurityException("RSA key size");
        if (encoded.length > MAX_MANIFEST || encoded.length != HEADER + 4 + (bits + 7) / 8) throw new IOException("Manifest length");
        byte[] data = encoded.clone();
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        if (in.readInt() != MAGIC || in.readInt() != 1) throw new IOException("Model manifest schema");
        int revision = in.readInt(), min = in.readInt(), max = in.readInt(), runtime = in.readInt();
        long size = in.readLong();
        byte[] hash = new byte[32]; in.readFully(hash);
        if (revision <= 0 || min <= 0 || min > max || runtime <= 0 || size <= 0 || size > MAX_BYTES) throw new IOException("Invalid manifest fields");
        int n = in.readInt();
        if (n != in.available()) throw new IOException("Signature length");
        byte[] sig = new byte[n]; in.readFully(sig);
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(key); verifier.update(data, 0, HEADER);
        if (!verifier.verify(sig)) throw new SignatureException("Untrusted model manifest");
        return new Manifest(revision, min, max, runtime, size, hash, data);
    }

    private static final class State {
        final int highest; final Manifest active, previous;
        State(int highest, Manifest active, Manifest previous) { this.highest = highest; this.active = active; this.previous = previous; }
    }
    private static Manifest readManifest(DataInputStream in, PublicKey key, boolean optional) throws IOException, GeneralSecurityException {
        int n = in.readInt();
        if (optional && n == 0) return null;
        if (n <= 0 || n > MAX_MANIFEST) throw new IOException("State manifest length");
        byte[] data = new byte[n]; in.readFully(data); return verify(data, key);
    }
    private static State read(Path dir, PublicKey key) throws IOException, GeneralSecurityException {
        if (!Files.notExists(dir, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Unsafe model directory");
        Path state = dir.resolve("models.state");
        if (Files.notExists(state, LinkOption.NOFOLLOW_LINKS)) return null;
        if (!Files.isRegularFile(state, LinkOption.NOFOLLOW_LINKS) || Files.size(state) > 16 + 2 * MAX_MANIFEST) throw new IOException("Invalid state");
        try (DataInputStream in = new DataInputStream(Files.newInputStream(state, LinkOption.NOFOLLOW_LINKS))) {
            if (in.readInt() != STATE) throw new IOException("State schema");
            int highest = in.readInt(); Manifest active = readManifest(in, key, false), previous = readManifest(in, key, true);
            if (in.read() != -1 || highest < active.revision || (previous != null && (highest < previous.revision || active.revision == previous.revision))) throw new IOException("State revisions");
            return new State(highest, active, previous);
        }
    }
    private static void writeManifest(DataOutputStream out, Manifest m) throws IOException {
        out.writeInt(m == null ? 0 : m.signed.length); if (m != null) out.write(m.signed);
    }
    private static void check(BooleanSupplier allowed) throws IOException { if (!allowed.getAsBoolean()) throw new IOException("Model work blocked"); }
    private static void write(Path dir, State state, BooleanSupplier allowed) throws IOException {
        check(allowed);
        Path temp = Files.createTempFile(dir, ".state-", ".tmp");
        try {
            try (FileOutputStream file = new FileOutputStream(temp.toFile()); DataOutputStream out = new DataOutputStream(file)) {
                out.writeInt(STATE); out.writeInt(state.highest); writeManifest(out, state.active); writeManifest(out, state.previous);
                out.flush(); file.getFD().sync();
            }
            check(allowed);
            Files.move(temp, dir.resolve("models.state"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
    private static Path blob(Path dir, Manifest m) { return dir.resolve(m.sha256 + ".task"); }
    private static void hash(InputStream input, OutputStream output, Manifest m, BooleanSupplier allowed) throws IOException, GeneralSecurityException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long total = 0; byte[] buffer = new byte[128 * 1024];
        while (true) {
            check(allowed); int n = input.read(buffer); check(allowed); if (n < 0) break;
            if (n == 0 || total + n > m.bytes) throw new IOException("Model size mismatch");
            total += n; digest.update(buffer, 0, n); if (output != null) output.write(buffer, 0, n);
        }
        if (total != m.bytes || !hex(digest.digest()).equals(m.sha256)) throw new IOException("Model checksum or size mismatch");
        check(allowed);
    }
    private static Path verified(Path dir, Manifest m, BooleanSupplier allowed) throws IOException, GeneralSecurityException {
        Path path = blob(dir, m);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) != m.bytes) throw new IOException("Missing or invalid model");
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) { hash(input, null, m, allowed); }
        check(allowed);
        return path;
    }
    private static void compatible(Manifest m, int host, int runtime) throws IOException {
        if (!m.compatible(host, runtime)) throw new IOException("Host or model runtime API mismatch");
    }
    public static synchronized Manifest active(Path dir, PublicKey key, int host, int runtime) throws IOException, GeneralSecurityException {
        State state = read(dir, key); return state == null || !state.active.compatible(host, runtime) ? null : state.active;
    }
    public static synchronized Path verifiedActive(Path dir, PublicKey key, int host, int runtime, BooleanSupplier allowed) throws IOException, GeneralSecurityException {
        check(allowed); Manifest m = active(dir, key, host, runtime);
        if (m == null) throw new IOException("No compatible signed model");
        return verified(dir, m, allowed);
    }
    public static synchronized Manifest install(Path dir, byte[] manifest, InputStream input, PublicKey key, int host, int runtime, BooleanSupplier allowed) throws IOException, GeneralSecurityException {
        check(allowed); Manifest m = verify(manifest, key); compatible(m, host, runtime);
        State old = read(dir, key);
        if (old != null && m.revision <= old.highest) throw new IOException("Stale model revision");
        Files.createDirectories(dir); if (Files.isSymbolicLink(dir)) throw new IOException("Unsafe model directory");
        Path dest = blob(dir, m); boolean created = false, committed = false;
        try {
            if (Files.exists(dest, LinkOption.NOFOLLOW_LINKS)) {
                verified(dir, m, allowed); hash(input, null, m, allowed);
            } else {
                // Never delete an older model automatically. Bound storage to two distinct blobs.
                try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.task")) {
                    int count = 0; for (Path ignored : files) if (++count >= 2) throw new IOException("Two model slots are occupied");
                }
                Path temp = Files.createTempFile(dir, ".model-", ".tmp");
                try {
                    try (FileOutputStream out = new FileOutputStream(temp.toFile())) { hash(input, out, m, allowed); out.getFD().sync(); }
                    check(allowed); Files.move(temp, dest, StandardCopyOption.ATOMIC_MOVE); created = true;
                } finally { Files.deleteIfExists(temp); }
            }
            write(dir, new State(m.revision, m, old == null ? null : old.active), allowed); committed = true;
            return m;
        } finally { if (created && !committed) Files.deleteIfExists(dest); }
    }
    public static synchronized Manifest rollback(Path dir, PublicKey key, int host, int runtime, BooleanSupplier allowed) throws IOException, GeneralSecurityException {
        check(allowed); State state = read(dir, key);
        if (state == null || state.previous == null) throw new IOException("No previous model");
        compatible(state.previous, host, runtime); verified(dir, state.previous, allowed);
        write(dir, new State(state.highest, state.previous, state.active), allowed); return state.previous;
    }

    /** A reviewed, revision-bound candidate. The active model is never a candidate. */
    public static final class Retirement {
        public final String sha256;
        public final long bytes;
        public final int previousRevision; // 0 for an unreferenced blob
        private final int highest, activeRevision, rollbackRevision;
        private final java.nio.file.attribute.FileTime modified, created;
        private final Object fileKey;
        private Retirement(String sha256, java.nio.file.attribute.BasicFileAttributes attrs, int previousRevision, State state) {
            this.sha256 = sha256; this.bytes = attrs.size(); this.previousRevision = previousRevision;
            modified = attrs.lastModifiedTime(); created = attrs.creationTime(); fileKey = attrs.fileKey();
            highest = state == null ? 0 : state.highest;
            activeRevision = state == null ? 0 : state.active.revision;
            rollbackRevision = state == null || state.previous == null ? 0 : state.previous.revision;
        }
        private boolean same(Retirement other) {
            return sha256.equals(other.sha256) && bytes == other.bytes && previousRevision == other.previousRevision
                && highest == other.highest && activeRevision == other.activeRevision && rollbackRevision == other.rollbackRevision
                && modified.equals(other.modified) && created.equals(other.created)
                && java.util.Objects.equals(fileKey, other.fileKey);
        }
        private boolean matchesFile(java.nio.file.attribute.BasicFileAttributes attrs) {
            return attrs.isRegularFile() && bytes == attrs.size() && modified.equals(attrs.lastModifiedTime())
                && created.equals(attrs.creationTime()) && java.util.Objects.equals(fileKey, attrs.fileKey());
        }
    }
    public static final class RetirementResult {
        public final boolean rollbackRemoved, fileDeleted;
        private RetirementResult(boolean rollbackRemoved, boolean fileDeleted) {
            this.rollbackRemoved = rollbackRemoved; this.fileDeleted = fileDeleted;
        }
    }
    private static java.util.List<Retirement> candidates(Path dir, State state) throws IOException {
        java.util.List<Retirement> result = new java.util.ArrayList<>();
        if (Files.notExists(dir, LinkOption.NOFOLLOW_LINKS)) return result;
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unsafe model directory");
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.task")) {
            int count = 0;
            for (Path file : files) {
                if (++count > 32) throw new IOException("Unexpected model directory contents");
                String name = file.getFileName().toString();
                if (!name.matches("[a-f0-9]{64}\\.task") || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                String digest = name.substring(0, 64);
                if (state != null && state.active.sha256.equals(digest)) continue;
                int prior = state != null && state.previous != null && state.previous.sha256.equals(digest) ? state.previous.revision : 0;
                java.nio.file.attribute.BasicFileAttributes attrs = Files.readAttributes(file,
                    java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (attrs.isRegularFile()) result.add(new Retirement(digest, attrs, prior, state));
            }
        }
        result.sort(java.util.Comparator.comparing(r -> r.sha256));
        return java.util.Collections.unmodifiableList(result);
    }
    public static synchronized java.util.List<Retirement> retirementCandidates(Path dir, PublicKey key, BooleanSupplier allowed)
            throws IOException, GeneralSecurityException {
        check(allowed);
        java.util.List<Retirement> result = candidates(dir, read(dir, key));
        check(allowed);
        return result;
    }
    public static synchronized RetirementResult retire(Path dir, Retirement reviewed, PublicKey key, BooleanSupplier allowed)
            throws IOException, GeneralSecurityException {
        check(allowed);
        State state = read(dir, key);
        Retirement current = null;
        for (Retirement candidate : candidates(dir, state)) if (candidate.same(reviewed)) { current = candidate; break; }
        if (current == null) throw new IOException("Model selection changed; review again");
        boolean removeRollback = current.previousRevision != 0;
        // Remove the rollback reference first. Never leave a signed active state pointing at deleted bytes.
        // If deletion fails after this commit, the orphan can be reviewed again; highest revision remains intact.
        if (removeRollback) write(dir, new State(state.highest, state.active, null), allowed);
        if (!allowed.getAsBoolean()) return new RetirementResult(removeRollback, false);
        Path file = dir.resolve(current.sha256 + ".task");
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return new RetirementResult(removeRollback, false);
        try {
            if (!current.matchesFile(Files.readAttributes(file, java.nio.file.attribute.BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS))) return new RetirementResult(removeRollback, false);
            return new RetirementResult(removeRollback, Files.deleteIfExists(file));
        } catch (IOException e) {
            return new RetirementResult(removeRollback, false);
        }
    }
}
