package top.codejava.aiops.infrastructure.deploy;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DeployExecutionRouter {

    private final List<DeployNode> deployNodes;

    public DeployExecutionRouter(List<DeployNode> deployNodes) {
        this.deployNodes = deployNodes;
    }

    public DeployContext execute(DeployContext context) {
        for (DeployNode deployNode : deployNodes) {
            deployNode.apply(context);
        }
        return context;
    }
}
