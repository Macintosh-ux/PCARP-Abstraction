package tools.abstraction;

import org.oceandsl.analysis.architecture.stages.ModelRepositoryProducerStage;
import org.oceandsl.analysis.architecture.stages.ModelSink;
import teetime.framework.*;

import java.nio.file.Path;

public class AbstractionConfiguration extends Configuration {

    public AbstractionConfiguration(Settings settings) {
        ModelRepositoryProducerStage reader = new ModelRepositoryProducerStage(Path.of(settings.getInput_dir()));

        ModelAbstraction abstraction = new ModelAbstraction(
                settings.getLevel(),
                settings.getFrst_opt_arg(),
                settings.getScnd_opt_arg(),
                settings.getThrd_opt_arg(),
                settings.getFrth_opt_arg()
        );

        ModelSink writer = new ModelSink(Path.of(settings.getOutput_dir()));

        this.connectPorts(reader.getOutputPort(), abstraction.getInputPort());
        this.connectPorts(abstraction.getOutputPort(), writer.getInputPort());
    }
}