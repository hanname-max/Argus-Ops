package top.codejava.aiops.infrastructure.deploy;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class PackageProjectNode implements DeployNode {

    private final ProjectBundleBuilder projectBundleBuilder;

    public PackageProjectNode(ProjectBundleBuilder projectBundleBuilder) {
        this.projectBundleBuilder = projectBundleBuilder;
    }

    @Override
    public void apply(DeployContext context) {
        context.progressMessages().add("Packaging local project bundle.");
        context.localBundlePath(projectBundleBuilder.buildBundle(context.request().projectPath(), context.workflowId()));
    }
}
