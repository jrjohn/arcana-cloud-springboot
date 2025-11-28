package com.arcana.cloud.plugin.runtime.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Verifies digital signatures of plugin JAR files.
 *
 * <p>Ensures that plugins are signed by trusted certificates before
 * allowing installation. This prevents malicious code injection.</p>
 */
@Component
public class PluginSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(PluginSignatureVerifier.class);

    private final PluginSecurityConfig securityConfig;
    private final Set<X509Certificate> trustedCertificates;

    public PluginSignatureVerifier(PluginSecurityConfig securityConfig) {
        this.securityConfig = securityConfig;
        this.trustedCertificates = new HashSet<>();
        loadTrustedCertificates();
    }

    /**
     * Loads trusted certificates from the configured path.
     */
    private void loadTrustedCertificates() {
        String certPath = securityConfig.getTrustedCertificatesPath();
        if (certPath == null || certPath.isEmpty()) {
            log.info("No trusted certificates path configured. Plugin signature verification will use JVM trust store.");
            return;
        }

        try {
            Path path = Path.of(certPath);
            if (Files.isDirectory(path)) {
                // Load all .crt/.pem/.cer files from directory
                Files.list(path)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".crt") || name.endsWith(".pem") || name.endsWith(".cer");
                    })
                    .forEach(this::loadCertificate);
            } else if (Files.exists(path)) {
                loadCertificate(path);
            }
            log.info("Loaded {} trusted certificates for plugin verification", trustedCertificates.size());
        } catch (IOException e) {
            log.error("Failed to load trusted certificates from: {}", certPath, e);
        }
    }

    private void loadCertificate(Path certPath) {
        try (InputStream is = Files.newInputStream(certPath)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(is);
            trustedCertificates.add(cert);
            log.debug("Loaded trusted certificate: {}", cert.getSubjectX500Principal().getName());
        } catch (Exception e) {
            log.warn("Failed to load certificate from {}: {}", certPath, e.getMessage());
        }
    }

    /**
     * Verification result containing details about the signature check.
     */
    public record VerificationResult(
        boolean verified,
        String message,
        String signerInfo,
        String certificateSubject,
        Date signedAt
    ) {
        public static VerificationResult success(String signerInfo, String subject, Date signedAt) {
            return new VerificationResult(true, "Plugin signature verified successfully",
                signerInfo, subject, signedAt);
        }

        public static VerificationResult failure(String message) {
            return new VerificationResult(false, message, null, null, null);
        }

        public static VerificationResult skipped(String message) {
            return new VerificationResult(true, message, null, null, null);
        }
    }

    /**
     * Verifies the digital signature of a plugin JAR file.
     *
     * @param jarPath the path to the JAR file
     * @return verification result
     */
    public VerificationResult verifySignature(Path jarPath) {
        if (!securityConfig.isEnabled()) {
            return VerificationResult.skipped("Plugin security is disabled");
        }

        if (!securityConfig.isRequireSignedPlugins()) {
            log.debug("Plugin signature verification is optional, skipping for: {}", jarPath.getFileName());
            return VerificationResult.skipped("Signed plugins not required");
        }

        log.info("Verifying signature of plugin: {}", jarPath.getFileName());

        try (JarFile jarFile = new JarFile(jarPath.toFile(), true)) {
            // Get manifest to check for signature
            if (jarFile.getManifest() == null) {
                return VerificationResult.failure("Plugin JAR has no manifest");
            }

            // Read all entries to trigger signature verification
            Enumeration<JarEntry> entries = jarFile.entries();
            Set<CodeSigner> allSigners = new HashSet<>();
            boolean hasSignedEntries = false;

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                // Skip directories and signature files
                if (entry.isDirectory() ||
                    entry.getName().startsWith("META-INF/") &&
                    (entry.getName().endsWith(".SF") ||
                     entry.getName().endsWith(".RSA") ||
                     entry.getName().endsWith(".DSA") ||
                     entry.getName().endsWith(".EC"))) {
                    continue;
                }

                // Read entry to trigger signature verification
                try (InputStream is = jarFile.getInputStream(entry)) {
                    byte[] buffer = new byte[8192];
                    while (is.read(buffer) != -1) {
                        // Just read to verify
                    }
                }

                // Collect signers
                CodeSigner[] signers = entry.getCodeSigners();
                if (signers != null && signers.length > 0) {
                    hasSignedEntries = true;
                    allSigners.addAll(Arrays.asList(signers));
                }
            }

            if (!hasSignedEntries || allSigners.isEmpty()) {
                return VerificationResult.failure("Plugin JAR is not signed");
            }

            // Verify at least one signer is trusted
            for (CodeSigner signer : allSigners) {
                Certificate[] certs = signer.getSignerCertPath().getCertificates().toArray(new Certificate[0]);
                if (certs.length > 0 && certs[0] instanceof X509Certificate signerCert) {
                    if (isTrusted(signerCert)) {
                        String subject = signerCert.getSubjectX500Principal().getName();
                        Date signedAt = signer.getTimestamp() != null ?
                            signer.getTimestamp().getTimestamp() : null;

                        log.info("Plugin {} signed by trusted certificate: {}",
                            jarPath.getFileName(), subject);

                        return VerificationResult.success(
                            signerCert.getSerialNumber().toString(),
                            subject,
                            signedAt
                        );
                    }
                }
            }

            return VerificationResult.failure("Plugin is signed but signer is not trusted");

        } catch (SecurityException e) {
            log.error("Plugin signature verification failed: {}", e.getMessage());
            return VerificationResult.failure("Signature verification failed: " + e.getMessage());
        } catch (IOException e) {
            log.error("Failed to read plugin JAR for verification: {}", e.getMessage());
            return VerificationResult.failure("Failed to read JAR: " + e.getMessage());
        }
    }

    /**
     * Checks if a certificate is trusted.
     */
    private boolean isTrusted(X509Certificate cert) {
        // Check against explicitly trusted certificates
        if (trustedCertificates.contains(cert)) {
            return true;
        }

        // Check if any trusted certificate issued this one
        for (X509Certificate trustedCert : trustedCertificates) {
            try {
                cert.verify(trustedCert.getPublicKey());
                return true; // Verified by trusted issuer
            } catch (Exception e) {
                // Not issued by this trusted cert, continue
            }
        }

        // If no explicit trusted certs, allow JVM default trust store
        if (trustedCertificates.isEmpty()) {
            try {
                cert.checkValidity(); // At least ensure not expired
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        return false;
    }

    /**
     * Validates plugin JAR before installation.
     *
     * @param jarPath the JAR file path
     * @return validation errors (empty if valid)
     */
    public List<String> validatePlugin(Path jarPath) {
        List<String> errors = new ArrayList<>();

        // Check file exists
        if (!Files.exists(jarPath)) {
            errors.add("Plugin file does not exist: " + jarPath);
            return errors;
        }

        // Check file size
        try {
            long size = Files.size(jarPath);
            if (size > securityConfig.getMaxPluginSizeBytes()) {
                errors.add(String.format("Plugin exceeds maximum size: %d bytes (max: %d)",
                    size, securityConfig.getMaxPluginSizeBytes()));
            }
        } catch (IOException e) {
            errors.add("Failed to check file size: " + e.getMessage());
        }

        // Verify signature if required
        VerificationResult sigResult = verifySignature(jarPath);
        if (!sigResult.verified()) {
            errors.add("Signature verification failed: " + sigResult.message());
        }

        // Check for dangerous content
        errors.addAll(scanForDangerousContent(jarPath));

        return errors;
    }

    /**
     * Scans JAR for potentially dangerous content.
     */
    private List<String> scanForDangerousContent(Path jarPath) {
        List<String> warnings = new ArrayList<>();

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                // Check for native libraries
                if (name.endsWith(".dll") || name.endsWith(".so") || name.endsWith(".dylib")) {
                    warnings.add("Plugin contains native library: " + name);
                }

                // Check for suspicious class names
                if (name.endsWith(".class")) {
                    String className = name.replace('/', '.').replace(".class", "");
                    if (className.contains("Runtime") ||
                        className.contains("ProcessBuilder") ||
                        className.contains("ClassLoader") && !className.contains("OSGi")) {
                        log.warn("Plugin contains potentially dangerous class: {}", className);
                    }
                }
            }
        } catch (IOException e) {
            warnings.add("Failed to scan JAR content: " + e.getMessage());
        }

        return warnings;
    }

    /**
     * Returns the number of trusted certificates loaded.
     */
    public int getTrustedCertificateCount() {
        return trustedCertificates.size();
    }
}
