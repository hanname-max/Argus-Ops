package top.codejava.aiops.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.codejava.aiops.application.port.LocalAiPort;
import top.codejava.aiops.application.port.RemoteAiAssistPort;
import top.codejava.aiops.application.port.RemoteCommandPort;
import top.codejava.aiops.application.usecase.OpsUseCase;

@Configuration
public class UseCaseConfiguration {

    @Bean
    public OpsUseCase opsUseCase(LocalAiPort localAiPort,
                                 RemoteAiAssistPort remoteAiAssistPort,
                                 RemoteCommandPort remoteCommandPort) {
        return new OpsUseCase(localAiPort, remoteAiAssistPort, remoteCommandPort);
    }
}
