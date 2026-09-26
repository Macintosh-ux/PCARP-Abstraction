package tools.abstraction;

import teetime.framework.Execution;

public class AbstractionMain{

    public static void main(String[] args) {

        Settings settings = new Settings(args);

        AbstractionConfiguration configuration = new AbstractionConfiguration(settings);
        Execution<AbstractionConfiguration> execution = new Execution<>(configuration);

        execution.executeBlocking();
    }
}
