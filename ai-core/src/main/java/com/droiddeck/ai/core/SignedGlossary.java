package com.droiddeck.ai.core;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.util.*;

/** Signed, non-executable menu dictionary. Only the host supplies the trusted key. */
public final class SignedGlossary {
    private static final int MAGIC = 0x44444147; // DDAG
    public static final int MAX_PAYLOAD = 65536;
    public static final int MAX_BUNDLE = MAX_PAYLOAD + 1024;
    private SignedGlossary() {}

    public static final class Bundle {
        public final int revision, minHost, maxHost;
        public final Map<String, String> entries;
        private Bundle(int revision, int minHost, int maxHost, Map<String, String> entries) {
            this.revision = revision;
            this.minHost = minHost;
            this.maxHost = maxHost;
            this.entries = Collections.unmodifiableMap(entries);
        }
        public boolean compatible(int host) { return host >= minHost && host <= maxHost; }
    }

    public static Bundle verify(byte[] encoded, PublicKey trustedKey) throws IOException, GeneralSecurityException {
        if (!(trustedKey instanceof RSAPublicKey) || ((RSAPublicKey) trustedKey).getModulus().bitLength() < 2048
                || ((RSAPublicKey) trustedKey).getModulus().bitLength() > 4096) {
            throw new GeneralSecurityException("Trusted RSA key required");
        }
        if (encoded.length > MAX_BUNDLE || encoded.length < 28) throw new IOException("Bundle size");
        byte[] bytes = encoded.clone();
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        if (in.readInt() != MAGIC || in.readInt() != 1) throw new IOException("Bundle schema");
        int revision = in.readInt(), min = in.readInt(), max = in.readInt(), length = in.readInt();
        if (revision <= 0 || min <= 0 || min > max || length <= 0 || length > MAX_PAYLOAD) {
            throw new IOException("Invalid manifest");
        }
        byte[] payload = new byte[length];
        in.readFully(payload);
        int signedLength = bytes.length - in.available();
        int sigLength = in.readInt();
        if (sigLength != (((RSAPublicKey) trustedKey).getModulus().bitLength() + 7) / 8 || sigLength != in.available()) {
            throw new IOException("Signature length or trailing data");
        }
        byte[] sig = new byte[sigLength];
        in.readFully(sig);
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(trustedKey);
        verifier.update(bytes, 0, signedLength);
        if (!verifier.verify(sig)) throw new SignatureException("Untrusted glossary signature");
        String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(payload)).toString();
        Map<String, String> entries = new LinkedHashMap<>();
        for (String line : text.split("\n", -1)) {
            int tab = line.indexOf('\t');
            if (tab <= 0 || tab != line.lastIndexOf('\t')) throw new IOException("Invalid dictionary row");
            String source = line.substring(0, tab), translated = line.substring(tab + 1);
            if (!source.matches("[a-z0-9][a-z0-9 .!?'-]{0,79}") || !source.equals(source.trim())
                    || translated.isEmpty() || translated.length() > 160 || !translated.equals(translated.trim())
                    || translated.chars().anyMatch(c -> Character.isISOControl(c) || "{}%\\<>".indexOf(c) >= 0)
                    || entries.size() >= 500 || entries.putIfAbsent(source, translated) != null) {
                throw new IOException("Invalid or duplicate dictionary entry");
            }
        }
        return new Bundle(revision, min, max, entries);
    }
}
