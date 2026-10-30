import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Release signing for the launcher self-update (Ed25519):
 *   java packaging/SignRelease.java keygen <private key file> <public key file>   (once; keep the private key secret)
 *   java packaging/SignRelease.java sign <private key file> build/release/SHA256SUMS.txt   (writes SHA256SUMS.txt.sig)
 * The private key never belongs in the repository. The public key goes to
 * launcher-core/src/main/resources/lumora/update-key.pub.
 */
public final class SignRelease {
    public static void main(String[] a) throws Exception {
        switch (a[0]) {
            case "keygen" -> {
                Path priv = Path.of(a[1]);
                if (Files.exists(priv)) {
                    throw new IllegalStateException("private key exists already: " + priv);
                }
                KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
                Files.createDirectories(priv.toAbsolutePath().getParent());
                Files.writeString(priv, Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()) + "\n", StandardCharsets.US_ASCII);
                Files.writeString(Path.of(a[2]), Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()) + "\n", StandardCharsets.US_ASCII);
                System.out.println("KEYS WRITTEN");
            }
            case "sign" -> {
                byte[] der = Base64.getDecoder().decode(Files.readString(Path.of(a[1])).strip());
                var key = KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(der));
                Path file = Path.of(a[2]);
                Signature s = Signature.getInstance("Ed25519");
                s.initSign(key);
                s.update(Files.readAllBytes(file));
                Files.writeString(file.resolveSibling(file.getFileName() + ".sig"), Base64.getEncoder().encodeToString(s.sign()) + "\n",
                        StandardCharsets.US_ASCII);
                System.out.println("SIGNED " + file.getFileName());
            }
            default -> throw new IllegalArgumentException("keygen | sign");
        }
    }
}
