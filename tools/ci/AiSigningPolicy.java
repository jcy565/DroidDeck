import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;

/** Validates only an externally managed AI signing key; never exports private material. */
public class AiSigningPolicy {
    private static final Set<String> DENIED = Set.of(
        "a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc",
        "b241ea7d77822d39b89d3227e06f3ecf62adec6cbfd38ad06196787991b5d3f0"
    );

    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException();
        return value;
    }

    static void verify(Path repo, Map<String, String> env) throws Exception {
        Path supplied = Path.of(required(env, "DD_AI_KEYSTORE"));
        if (!supplied.isAbsolute() || !Files.isRegularFile(supplied, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException();
        Path store = supplied.toRealPath();
        if (store.startsWith(repo.toRealPath())) throw new IllegalArgumentException();
        String alias = required(env, "DD_AI_KEY_ALIAS");
        String expected = required(env, "DD_AI_CERT_SHA256").toLowerCase(Locale.ROOT);
        if (!expected.matches("[0-9a-f]{64}") || DENIED.contains(expected))
            throw new IllegalArgumentException();
        char[] storePassword = required(env, "DD_AI_STORE_PASSWORD").toCharArray();
        char[] keyPassword = required(env, "DD_AI_KEY_PASSWORD").toCharArray();
        try {
            KeyStore keys = KeyStore.getInstance("PKCS12");
            try (var input = Files.newInputStream(store)) { keys.load(input, storePassword); }
            if (!(keys.getKey(alias, keyPassword) instanceof PrivateKey)) throw new IllegalArgumentException();
            if (!(keys.getCertificate(alias) instanceof X509Certificate cert)) throw new IllegalArgumentException();
            cert.checkValidity();
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
            if (!expected.equals(actual) || DENIED.contains(actual)) throw new IllegalArgumentException();
        } finally {
            Arrays.fill(storePassword, '\0');
            Arrays.fill(keyPassword, '\0');
        }
    }

    public static void main(String[] args) {
        try {
            if (args.length != 1) throw new IllegalArgumentException();
            verify(Path.of(args[0]), System.getenv());
            System.out.println("AI signing configuration validated; APK not verified.");
        } catch (Exception e) {
            System.err.println("AI signing configuration rejected; check external key, credentials and certificate pin.");
            System.exit(1);
        }
    }
}
