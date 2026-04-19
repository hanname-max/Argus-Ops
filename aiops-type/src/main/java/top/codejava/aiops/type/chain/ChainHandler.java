package top.codejava.aiops.type.chain;

public interface ChainHandler<T extends ChainContext> {

    boolean supports(T context);

    void handle(T context);
}
