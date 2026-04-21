package top.codejava.aiops.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class DesktopBrowserLauncher {

    private final Environment environment;
    private final boolean autoOpenBrowser;
    private final AtomicBoolean launched = new AtomicBoolean(false);

    public DesktopBrowserLauncher(Environment environment,
                                  @Value("${aiops.desktop.auto-open-browser:true}") boolean autoOpenBrowser) {
        this.environment = environment;
        this.autoOpenBrowser = autoOpenBrowser;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void launchBrowser() {
        if (!autoOpenBrowser || launched.getAndSet(true)) {
            return;
        }
        if (GraphicsEnvironment.isHeadless() || !Desktop.isDesktopSupported()) {
            return;
        }
        int port = environment.getProperty("local.server.port", Integer.class,
                environment.getProperty("server.port", Integer.class, 8080));
        String url = "http://127.0.0.1:" + port + "/";
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(350L);
                Desktop.getDesktop().browse(URI.create(url));
            } catch (Exception ignored) {
            }
        });
    }
}
