package top.codejava.aiops.infrastructure.ops;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.springframework.stereotype.Component;
import top.codejava.aiops.type.exception.SshConnectionException;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

@Component
public class SshSessionFactory {

    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 8_000;

    public Session openSession(SshAccessRequest request) {
        try {
            JSch jsch = new JSch();
            if (request.privateKeyPem() != null && !request.privateKeyPem().isBlank()) {
                jsch.addIdentity(
                        "aiops-inline-key",
                        request.privateKeyPem().getBytes(StandardCharsets.UTF_8),
                        null,
                        null
                );
            }

            Session session = jsch.getSession(request.username(), request.host(), normalizePort(request.port()));
            if (request.password() != null && !request.password().isBlank()) {
                session.setPassword(request.password());
            }

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            config.put("PreferredAuthentications", buildPreferredAuthentications(request));
            config.put("ServerAliveInterval", "5000");
            config.put("ServerAliveCountMax", "3");
            session.setConfig(config);
            session.connect(normalizeTimeout(request.connectTimeoutMillis()));
            return session;
        } catch (JSchException ex) {
            throw new SshConnectionException(describeConnectionFailure(request, ex), ex);
        }
    }

    public int normalizePort(Integer port) {
        return port == null || port <= 0 ? 22 : port;
    }

    public int normalizeTimeout(Integer timeoutMillis) {
        return timeoutMillis == null || timeoutMillis <= 0
                ? DEFAULT_CONNECT_TIMEOUT_MILLIS
                : timeoutMillis;
    }

    private String buildPreferredAuthentications(SshAccessRequest request) {
        if (request.privateKeyPem() != null && !request.privateKeyPem().isBlank()
                && request.password() != null && !request.password().isBlank()) {
            return "publickey,password,keyboard-interactive";
        }
        if (request.privateKeyPem() != null && !request.privateKeyPem().isBlank()) {
            return "publickey";
        }
        return "password,keyboard-interactive";
    }

    private String describeConnectionFailure(SshAccessRequest request, JSchException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (message.contains("auth fail")) {
            return "SSH authentication failed for "
                    + request.username()
                    + "@"
                    + request.host()
                    + ":"
                    + normalizePort(request.port());
        }
        if (message.contains("timeout") || message.contains("session is down")) {
            return "SSH connection timed out for "
                    + request.host()
                    + ":"
                    + normalizePort(request.port());
        }
        return "SSH connection failed for "
                + request.host()
                + ":"
                + normalizePort(request.port())
                + ": "
                + ex.getMessage();
    }

    public record SshAccessRequest(
            String host,
            Integer port,
            String username,
            String password,
            String privateKeyPem,
            Integer connectTimeoutMillis
    ) {
    }
}
