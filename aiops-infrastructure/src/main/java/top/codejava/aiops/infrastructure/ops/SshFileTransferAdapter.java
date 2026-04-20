package top.codejava.aiops.infrastructure.ops;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SftpProgressMonitor;
import org.springframework.stereotype.Component;
import top.codejava.aiops.application.dto.WorkflowModels;
import top.codejava.aiops.type.exception.RemoteExecutionException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

@Component
public class SshFileTransferAdapter {

    private final SshSessionFactory sshSessionFactory;

    public SshFileTransferAdapter(SshSessionFactory sshSessionFactory) {
        this.sshSessionFactory = sshSessionFactory;
    }

    public UploadResult upload(WorkflowModels.TargetCredential credential,
                               Path localFile,
                               String remotePath,
                               Consumer<TransferEvent> eventSink) {
        return upload(
                new UploadRequest(
                        credential.host(),
                        credential.sshPort(),
                        credential.username(),
                        credential.password(),
                        credential.privateKeyPem(),
                        credential.connectTimeoutMillis(),
                        localFile,
                        remotePath,
                        true
                ),
                eventSink
        );
    }

    public UploadResult upload(UploadRequest request, Consumer<TransferEvent> eventSink) {
        Objects.requireNonNull(request, "request");
        Consumer<TransferEvent> safeEventSink = eventSink == null ? ignored -> {
        } : eventSink;

        if (request.localFile() == null || !Files.isRegularFile(request.localFile())) {
            throw new RemoteExecutionException("Local upload source does not exist: " + request.localFile(), null);
        }
        if (request.remotePath() == null || request.remotePath().isBlank()) {
            throw new RemoteExecutionException("remotePath is required for SFTP upload", null);
        }

        Session session = null;
        ChannelSftp channelSftp = null;
        try {
            session = sshSessionFactory.openSession(new SshSessionFactory.SshAccessRequest(
                    request.host(),
                    request.port(),
                    request.username(),
                    request.password(),
                    request.privateKeyPem(),
                    request.connectTimeoutMillis()
            ));
            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect(sshSessionFactory.normalizeTimeout(request.connectTimeoutMillis()));

            ensureRemoteDirectoryExists(channelSftp, parentDirectory(request.remotePath()));
            String mode = request.overwrite() ? "overwrite" : "create-only";
            safeEventSink.accept(new TransferEvent("UPLOAD_START", request.localFile().toString(), request.remotePath(), mode));

            int putMode = request.overwrite() ? ChannelSftp.OVERWRITE : ChannelSftp.RESUME;
            channelSftp.put(
                    request.localFile().toString(),
                    request.remotePath(),
                    new SimpleProgressMonitor(request.localFile(), request.remotePath(), safeEventSink),
                    putMode
            );

            safeEventSink.accept(new TransferEvent("UPLOAD_COMPLETE", request.localFile().toString(), request.remotePath(), "done"));
            return new UploadResult(request.localFile(), request.remotePath(), Files.size(request.localFile()));
        } catch (JSchException | SftpException ex) {
            throw new RemoteExecutionException("Failed to upload file over SFTP to " + request.remotePath(), ex);
        } catch (Exception ex) {
            throw new RemoteExecutionException("Unexpected SFTP upload failure for " + request.remotePath(), ex);
        } finally {
            if (channelSftp != null) {
                channelSftp.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }

    private void ensureRemoteDirectoryExists(ChannelSftp channelSftp, String remoteDirectory) throws SftpException {
        if (remoteDirectory == null || remoteDirectory.isBlank() || "/".equals(remoteDirectory)) {
            return;
        }

        String normalized = remoteDirectory.replace('\\', '/');
        String[] segments = normalized.split("/");
        String current = normalized.startsWith("/") ? "/" : "";

        for (String segment : segments) {
            if (segment == null || segment.isBlank()) {
                continue;
            }

            current = current.endsWith("/") || current.isEmpty()
                    ? current + segment
                    : current + "/" + segment;

            try {
                channelSftp.cd(current);
            } catch (SftpException ex) {
                channelSftp.mkdir(current);
                channelSftp.cd(current);
            }
        }
    }

    private String parentDirectory(String remotePath) {
        String normalized = remotePath.replace('\\', '/');
        int separatorIndex = normalized.lastIndexOf('/');
        if (separatorIndex <= 0) {
            return normalized.startsWith("/") ? "/" : "";
        }
        return normalized.substring(0, separatorIndex);
    }

    public record UploadRequest(
            String host,
            Integer port,
            String username,
            String password,
            String privateKeyPem,
            Integer connectTimeoutMillis,
            Path localFile,
            String remotePath,
            boolean overwrite
    ) {
    }

    public record UploadResult(Path localFile, String remotePath, long sizeBytes) {
    }

    public record TransferEvent(String stage, String source, String target, String detail) {
    }

    private static final class SimpleProgressMonitor implements SftpProgressMonitor {

        private final Path localFile;
        private final String remotePath;
        private final Consumer<TransferEvent> eventSink;
        private long transferredBytes;
        private long totalBytes;

        private SimpleProgressMonitor(Path localFile, String remotePath, Consumer<TransferEvent> eventSink) {
            this.localFile = localFile;
            this.remotePath = remotePath;
            this.eventSink = eventSink;
        }

        @Override
        public void init(int op, String src, String dest, long max) {
            this.totalBytes = max;
            this.transferredBytes = 0L;
            eventSink.accept(new TransferEvent("UPLOAD_PROGRESS", localFile.toString(), remotePath, "0/" + max));
        }

        @Override
        public boolean count(long count) {
            transferredBytes += count;
            eventSink.accept(new TransferEvent(
                    "UPLOAD_PROGRESS",
                    localFile.toString(),
                    remotePath,
                    transferredBytes + "/" + totalBytes
            ));
            return true;
        }

        @Override
        public void end() {
            eventSink.accept(new TransferEvent(
                    "UPLOAD_PROGRESS",
                    localFile.toString(),
                    remotePath,
                    transferredBytes + "/" + totalBytes
            ));
        }
    }
}
