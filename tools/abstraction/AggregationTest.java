package tools.abstraction;

import kieker.model.analysismodel.type.ComponentType;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class AggregationTest extends AbstractionTestBase {

    @Test
    void packageDepthLeavesAlreadyShallowComponentsUnchanged() {
        node("alpha.A");
        node("beta.B");
        Aggregation plan = new Aggregation();
        plan.new PackageDepth().staticAggregation(1);
        finishAggregation(plan);
        assertAll(() -> assertEquals(2, ModelAbstraction.typeModel.getComponentTypes().size()),
                () -> assertNotNull(ModelAbstraction.typeModel.getComponentTypes().get("alpha.A")), this::assertModelConsistent);
    }

    @Test
    void packageDepthMergesSiblingsButKeepsDifferentPackagesSeparate() {
        node("alpha.deep.A"); node("alpha.deep.B");
        node("beta.deep.C"); node("beta.deep.D");
        Aggregation plan = new Aggregation();
        plan.new PackageDepth().staticAggregation(1);
        finishAggregation(plan);
        Set<String> signatures = ModelAbstraction.typeModel.getComponentTypes().values().stream()
                .map(ComponentType::getSignature).collect(Collectors.toSet());
        assertAll(() -> assertEquals(2, signatures.size()),
                () -> assertTrue(signatures.stream().allMatch(value -> value.contains("::aggregated"))), this::assertModelConsistent);
    }

    @Test
    void packageDepthPreservesOperationsStoragesInvocationsDataflowsAndMetadata() {
        Fixture fixture = standardFixture();
        long callsBefore = calls();
        Aggregation plan = new Aggregation();
        plan.new PackageDepth().staticAggregation(1);
        finishAggregation(plan);
        assertAll(() -> assertEquals(2, ModelAbstraction.executionModel.getInvocations().size()),
                () -> assertEquals(1, ModelAbstraction.executionModel.getOperationDataflows().size()),
                () -> assertEquals(1, ModelAbstraction.executionModel.getStorageDataflows().size()),
                () -> assertEquals(callsBefore, calls()), this::assertModelConsistent);
    }

    @Test
    void packageDepthRejectsNegativeDepthWithoutChangingTheModel() {
        standardFixture();
        int before = ModelAbstraction.typeModel.getComponentTypes().size();
        Aggregation plan = new Aggregation();
        assertAll(() -> assertThrows(IllegalArgumentException.class, () -> plan.new PackageDepth().staticAggregation(-1)),
                () -> assertEquals(before, ModelAbstraction.typeModel.getComponentTypes().size()));
    }

    @Test
    void percentageAggregationAggregatesOnlyComponentsBelowTheComputedThreshold() {
        standardFixture();
        Aggregation plan = new Aggregation();
        plan.new ThresholdAggregation().dynamicAggregationPercentage(10);
        finishAggregation(plan);
        assertAll(() -> assertTrue(ModelAbstraction.typeModel.getComponentTypes().values().stream()
                        .anyMatch(type -> type.getSignature().contains("::aggregated"))),
                () -> assertNotNull(ModelAbstraction.typeModel.getComponentTypes().get("beta.high.E")), this::assertModelConsistent);
    }

    @Test
    void fixedThresholdEscalatesWeakGroupsAndMarksUnreachableThresholds() {
        standardFixture();
        Aggregation plan = new Aggregation();
        plan.new ThresholdAggregation().dynamicAggregationWithThreshold(5);
        finishAggregation(plan);
        assertAll(() -> assertTrue(ModelAbstraction.typeModel.getComponentTypes().values().stream()
                        .anyMatch(type -> type.getSignature().contains("::below-threshold"))),
                () -> assertTrue(ModelAbstraction.typeModel.getComponentTypes().values().stream()
                        .anyMatch(type -> type.getSignature().equals("beta.high.D"))), this::assertModelConsistent);
    }

    @Test
    void zeroPercentageAggregationIsANoOp() {
        Fixture fixture = standardFixture();
        Aggregation plan = new Aggregation();
        plan.new ThresholdAggregation().dynamicAggregationPercentage(0);
        finishAggregation(plan);
        assertAll(() -> assertEquals(fixture.nodes().size(), ModelAbstraction.typeModel.getComponentTypes().size()),
                () -> assertTrue(ModelAbstraction.typeModel.getComponentTypes().values().stream()
                        .noneMatch(type -> type.getSignature().contains("::aggregated"))), this::assertModelConsistent);
    }

    @Test
    void quantileAggregationSelectsTheRequestedLowerHalfDeterministically() {
        standardFixture();
        Aggregation plan = new Aggregation();
        plan.new ThresholdAggregation().dynamicAggregationQuantile(50);
        finishAggregation(plan);
        assertAll(() -> assertTrue(ModelAbstraction.typeModel.getComponentTypes().size() < 7),
                () -> assertNotNull(ModelAbstraction.typeModel.getComponentTypes().get("beta.high.E")), this::assertModelConsistent);
    }

    @Test
    void hundredPercentQuantileIncludesTheStrongestComponent() {
        standardFixture();
        Aggregation plan = new Aggregation();
        plan.new ThresholdAggregation().dynamicAggregationQuantile(100);
        finishAggregation(plan);
        assertAll(
                () -> assertNotNull(ModelAbstraction.typeModel.getComponentTypes().get("beta.high.E")),
                () -> assertTrue(ModelAbstraction.typeModel.getComponentTypes().values().stream()
                        .filter(type -> !"beta.high.E".equals(type.getSignature()))
                        .allMatch(type -> type.getSignature().contains("::aggregated"))), this::assertModelConsistent);
    }

    @Test
    void quantileAggregationRejectsValuesOutsideZeroToHundred() {
        standardFixture();
        assertAll(() -> assertThrows(IllegalArgumentException.class,
                        () -> new Aggregation().new ThresholdAggregation().dynamicAggregationQuantile(-1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Aggregation().new ThresholdAggregation().dynamicAggregationQuantile(101)));
    }
}
