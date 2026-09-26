package tools.abstraction;

import kieker.analysis.architecture.repository.ModelRepository;
import kieker.model.analysismodel.assembly.AssemblyModel;
import kieker.model.analysismodel.deployment.DeploymentModel;
import kieker.model.analysismodel.execution.ExecutionModel;
import kieker.model.analysismodel.source.SourceModel;
import kieker.model.analysismodel.statistics.StatisticsModel;
import kieker.model.analysismodel.type.TypeModel;
import org.eclipse.emf.ecore.EObject;
import teetime.stage.basic.AbstractFilter;

import static tools.abstraction.AbstractionStrategy.*;

public class ModelAbstraction extends AbstractFilter<ModelRepository> {
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_RESET = "\u001B[0m";

    private final String level;
    private final String first_arg;
    private final String second_arg;
    private final String third_arg;
    private final String fourth_arg;

    protected static ModelRepository model;
    protected static TypeModel typeModel;
    protected static SourceModel sourceModel;
    protected static AssemblyModel assemblyModel;
    protected static ExecutionModel executionModel;
    protected static DeploymentModel deploymentModel;
    protected static StatisticsModel statisticsModel;

    public ModelAbstraction(String level, String first_arg, String second_arg, String third_arg, String fourth_arg) {
        this.level = level;
        this.first_arg = first_arg;
        this.second_arg = second_arg;
        this.third_arg = third_arg;
        this.fourth_arg = fourth_arg;
    }

    @Override
    protected void execute(ModelRepository model) {
        ModelAbstraction.model = model;
        separateModelContents(model);
        refreshExecutionMaps();

        printState();
        printDisplayedComponentInvocationEdges();


        switch (level) {
            case "I" -> identity();

            case "PDA","PTA","CTA" -> aggregation(); // Aggregation: Package Depth, Percentage Threshold, Percentile Threshold

            case "PTF","CTF" -> filtering(); // Filtering: Percentage Threshold, Percentile Threshold

            case "C" -> clustering();

            case "CS" -> sequentialComposition();
            case "CF" -> fixedThresholdComposition();
            default -> throw new IllegalArgumentException("Unknown Abstraction Level: " + level);
        }
    }

    private void identity() {
        outputPort.send(model);
    }

    private void aggregation() {
        applyAggregation(level, first_arg);
        outputPort.send(model);
    }

    private void filtering() {
        applyFiltering(level, first_arg);
        outputPort.send(model);
    }

    private void clustering() {
        applyClustering(first_arg);
        outputPort.send(model);
    }

    private void sequentialComposition() {
        applyCompositionStep(first_arg, second_arg, null);
        applyCompositionStep(third_arg, fourth_arg, null);
        outputPort.send(model);
    }

    private void fixedThresholdComposition() {
        Integer firstThreshold = computeOriginalThreshold(first_arg, second_arg);
        Integer secondThreshold = computeOriginalThreshold(third_arg, fourth_arg);
        applyCompositionStep(first_arg, second_arg, firstThreshold);
        applyCompositionStep(third_arg, fourth_arg, secondThreshold);
        outputPort.send(model);
    }

    private Integer computeOriginalThreshold(String mechanism, String argument) {
        ThresholdSelection selection = new ThresholdSelection();

        switch (mechanism) {
            case "PTA", "PTF" -> selection.computePercentageThreshold(Double.parseDouble(argument));
            case "CTA", "CTF" -> selection.computeQuantileThreshold(Double.parseDouble(argument));
            default -> { return null; }
        }

        return selection.getThreshold();
    }

    private void applyCompositionStep(String mechanism, String argument, Integer fixedThreshold) {
        switch (mechanism) {
            case "PDA" -> applyAggregation(mechanism, argument, null);
            case "PTA","CTA" -> applyAggregation(mechanism, argument, fixedThreshold);

            case "PTF","CTF" -> applyFiltering(mechanism, argument, fixedThreshold);

            case "C" -> applyClustering(argument);
            default -> throw new IllegalArgumentException("Unknown composition mechanism: " + mechanism);
        }
    }

    private void applyAggregation(String mode, String argument) {
        applyAggregation(mode, argument, null);
    }

    private void applyAggregation(String mode, String argument, Integer fixedThreshold) {
        Aggregation plan = new Aggregation();

        switch (mode) {
            case "PDA" -> plan.new PackageDepth().staticAggregation(Integer.parseInt(argument));
            case "PTA" -> {
                Aggregation.ThresholdAggregation aggregation = plan.new ThresholdAggregation();
                if (fixedThreshold == null) aggregation.dynamicAggregationPercentage(Double.parseDouble(argument));
                else aggregation.dynamicAggregationWithThreshold(fixedThreshold);
            }
            case "CTA" -> {
                Aggregation.ThresholdAggregation aggregation = plan.new ThresholdAggregation();
                if (fixedThreshold == null) aggregation.dynamicAggregationQuantile(Double.parseDouble(argument));
                else aggregation.dynamicAggregationWithThreshold(fixedThreshold);
            }
            default -> throw new IllegalArgumentException("Unknown aggregation type: " + mode);
        }

        new ModelTransformation(plan).transformModels();
        plan.rebuildRepair();
        plan.finalizeModelConsistency();
    }

    private void applyFiltering(String mode, String argument) {
        applyFiltering(mode, argument, null);
    }

    private void applyFiltering(String mode, String argument, Integer fixedThreshold) {
        Filtering plan = new Filtering();

        if (fixedThreshold != null) plan.filterWithThreshold(fixedThreshold);
        else {
            switch (mode) {
                case "PTF" -> plan.filterPercentage(Double.parseDouble(argument));
                case "CTF" -> plan.filterQuantile(Double.parseDouble(argument));
                default -> throw new IllegalArgumentException("Unknown filtering type: " + mode);
            }
        }

        new ModelTransformation(plan).transformModels();
        plan.finalizeModelConsistency();
    }

    private void applyClustering(String resolution) {
        Cluster plan = new Cluster();
        if (resolution.equals("0")) {
            plan.cluster();
        } else {
            plan.cluster(Double.parseDouble(resolution));
        }
        new ModelTransformation(plan).transformModels();
        plan.finalizeModelConsistency();
    }

    private void separateModelContents(ModelRepository model) {
        for (EObject repositoryModel : model.getModels().values()) {
            switch (repositoryModel.eClass().getName()) {
                case "TypeModel" -> typeModel = (TypeModel) repositoryModel;
                case "SourceModel" -> sourceModel = (SourceModel) repositoryModel;
                case "ExecutionModel" -> executionModel = (ExecutionModel) repositoryModel;
                case "DeploymentModel" -> deploymentModel = (DeploymentModel) repositoryModel;
                case "AssemblyModel" -> assemblyModel = (AssemblyModel) repositoryModel;
                case "StatisticsModel" -> statisticsModel = (StatisticsModel) repositoryModel;
            }
        }

        if (typeModel == null || sourceModel == null || assemblyModel == null || executionModel == null
                || deploymentModel == null || statisticsModel == null) {
            throw new IllegalStateException("ModelRepository does not contain all six analysis models.");
        }
    }

    private void printState() {
        System.out.println(ANSI_GREEN
                + "\n===== ORIGINAL MODEL STATE ====="
                +ANSI_RESET
                + "\nComponentTypes: " + typeModel.getComponentTypes().size()
                + "\nAssemblyComponents: " + assemblyModel.getComponents().size()
                + "\nInvocations: " + executionModel.getInvocations().size()
                + "\nStatistics entries: " + statisticsModel.getStatistics().size()
                + "\nSource entries: " + sourceModel.getSources().size()
                + ANSI_RESET);
    }
}
