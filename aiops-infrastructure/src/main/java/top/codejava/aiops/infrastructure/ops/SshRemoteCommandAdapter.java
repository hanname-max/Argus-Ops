package top.codejava.aiops.infrastructure.ops;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.springframework.stereotype.Component;
import top.codejava.aiops.application.dto.RemoteDeployRequest;
import top.codejava.aiops.application.dto.RemoteDeployResult;
import top.codejava.aiops.application.port.RemoteCommandPort;
import top.codejava.aiops.type.exception.RemoteExecutionException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

@Component
public class SshRemoteCommandAdapter implements RemoteCommandPort {

    @Override
    public RemoteDeployResult execute(RemoteDeployRequest request) {
        Session session = null;
        ChannelExec channel = null;
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(request.username(), request.host(), request.port() <= 0 ? 22 : request.port());
            session.setPassword(request.password());
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(8000);

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(request.command());

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ByteArrayOutputStream error = new ByteArrayOutputStream();
            channel.setOutputStream(output);
            channel.setErrStream(error);
            channel.connect(8000);

            while (!channel.isClosed()) {
                Thread.sleep(50);
            }

            boolean success = channel.getExitStatus() == 0;
            return new RemoteDeployResult(
                    success,
                    output.toString(StandardCharsets.UTF_8),
                    error.toString(StandardCharsets.UTF_8)
            );
        } catch (Exception ex) {
            throw new RemoteExecutionException("Failed to execute remote command", ex);
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }
}
