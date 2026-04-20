package top.codejava.aiops.infrastructure.workflow.probe;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Socket;

@Component
@Order(20)
public class PortProbeNode implements ProbeNode {

    @Override
    public void apply(ProbeContext context) {
        for (int offset = 0; offset <= context.maxProbeSpan(); offset++) {
            int candidate = context.requestedPort() + offset;
            context.triedPorts().add(candidate);
            boolean occupied = isPortOccupied(context.request().credential().host(), candidate, context.timeoutMillis());
            if (offset == 0) {
                context.requestedPortOccupied(occupied);
            }
            if (!occupied) {
                context.recommendedPort(candidate);
                return;
            }
        }
    }

    private boolean isPortOccupied(String host, int port, int timeoutMillis) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
