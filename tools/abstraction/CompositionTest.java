package tools.abstraction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies both composition semantics without coupling tests to the TeeTime scheduler. */
class CompositionTest extends AbstractionTestBase {

    @Test
    void sequentialAggregationThenFilteringUsesTheChangedModel() {
        standardFixture();
        Aggregation aggregation = new Aggregation();
        aggregation.new ThresholdAggregation().dynamicAggregationPercentage(10); finishAggregation(aggregation);
        int afterFirstStep = ModelAbstraction.typeModel.getComponentTypes().size();
        Filtering filtering = new Filtering();
        filtering.filterQuantile(50.0);
        finish(filtering);
        assertAll(() -> assertTrue(ModelAbstraction.typeModel.getComponentTypes().size() < afterFirstStep), this::assertModelConsistent);
    }

    @Test
    void sequentialFilteringThenAggregationNeverRecreatesRemovedTypes() {
        standardFixture();
        Filtering filtering = new Filtering();
        filtering.filterQuantile(50.0);
        finish(filtering);
        assertNull(ModelAbstraction.typeModel.getComponentTypes().get("gamma.zero.G"));
        Aggregation aggregation = new Aggregation();
        aggregation.new PackageDepth().staticAggregation(1);
        finishAggregation(aggregation);
        assertAll(() -> assertTrue(ModelAbstraction.typeModel.getComponentTypes().values().stream()
                        .noneMatch(type -> type.getSignature().contains("gamma.zero.G"))), this::assertModelConsistent);
    }

    @Test
    void fixedThresholdAggregationThenFilteringUsesTheOriginalAbsoluteValues() {
        standardFixture();
        int fixedThreshold = originalPercentageThreshold(10);
        Aggregation aggregation = new Aggregation();
        aggregation.new ThresholdAggregation().dynamicAggregationWithThreshold(fixedThreshold);
        finishAggregation(aggregation);
        Filtering filtering = new Filtering();
        filtering.filterWithThreshold(fixedThreshold);
        finish(filtering);
        assertAll(() -> assertTrue(ModelAbstraction.typeModel.getComponentTypes().size() < 7), this::assertModelConsistent);
    }

    @Test
    void fixedThresholdFilteringThenAggregationUsesTheOriginalAbsoluteValues() {
        standardFixture();
        int fixedThreshold = originalQuantileThreshold(50);
        Filtering filtering = new Filtering();
        filtering.filterWithThreshold(fixedThreshold);
        finish(filtering);
        int afterFiltering = ModelAbstraction.typeModel.getComponentTypes().size();
        Aggregation aggregation = new Aggregation();
        aggregation.new ThresholdAggregation().dynamicAggregationWithThreshold(fixedThreshold);
        finishAggregation(aggregation);
        assertAll(() -> assertTrue(ModelAbstraction.typeModel.getComponentTypes().size() <= afterFiltering), this::assertModelConsistent);
    }

    @Test
    void clusteringCanRunBeforeFiltering() {
        standardFixture();
        Cluster cluster = new Cluster();
        cluster.cluster(1.0);
        finish(cluster);
        Filtering filtering = new Filtering();
        filtering.filterPercentage(5.0);
        finish(filtering);
        assertAll(() -> assertTrue(ModelAbstraction.typeModel.getComponentTypes().values().stream()
                        .allMatch(type -> type.getPackage().startsWith("CLUSTER:") || type.getPackage().equals("RESIDUALS"))),
                this::assertModelConsistent);
    }

    @Test
    void clusteringCanRunAfterFiltering() {
        standardFixture();
        Filtering filtering = new Filtering();
        filtering.filterWithThreshold(5);
        finish(filtering);
        int survivors = ModelAbstraction.typeModel.getComponentTypes().size();
        Cluster cluster = new Cluster();
        cluster.cluster(1.0);
        finish(cluster);
        assertAll(() -> assertEquals(survivors, ModelAbstraction.typeModel.getComponentTypes().size()), this::assertModelConsistent);
    }

    @Test
    void secondStepCanSafelyProcessAnEmptyModel() {
        resetModels();
        Aggregation aggregation = new Aggregation();
        assertDoesNotThrow(() -> aggregation.new PackageDepth().staticAggregation(1));
        finishAggregation(aggregation);
        assertAll(
                () -> assertTrue(ModelAbstraction.typeModel.getComponentTypes().isEmpty()), this::assertModelConsistent);
    }

    @Test
    void compositionDoesNotLeakSelectionStateIntoTheSecondStep() {
        standardFixture();
        Filtering first = new Filtering();
        first.filterWithThreshold(5);
        finish(first);
        int survivors = ModelAbstraction.typeModel.getComponentTypes().size();
        Filtering second = new Filtering();
        second.filterWithThreshold(0);
        finish(second);
        assertAll(() -> assertEquals(survivors, ModelAbstraction.typeModel.getComponentTypes().size()), this::assertModelConsistent);
    }

    private int originalPercentageThreshold(double percentage) {
        ThresholdSelection selection = new ThresholdSelection();
        selection.computePercentageThreshold(percentage);
        return selection.getThreshold();
    }

    private int originalQuantileThreshold(double quantile) {
        ThresholdSelection selection = new ThresholdSelection();
        selection.computeQuantileThreshold(quantile);
        return selection.getThreshold();
    }
}
