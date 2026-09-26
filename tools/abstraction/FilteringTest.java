package tools.abstraction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FilteringTest extends AbstractionTestBase {

    @Test
    void fixedThresholdRemovesTheCompleteDependencyClosure() {
        standardFixture();
        Filtering plan = new Filtering();
        plan.filterWithThreshold(5);
        finish(plan);
        assertAll(() -> assertEquals(3, ModelAbstraction.typeModel.getComponentTypes().size()),
                () -> assertEquals(3, ModelAbstraction.assemblyModel.getComponents().size()),
                () -> assertEquals(2, ModelAbstraction.executionModel.getInvocations().size()), this::assertModelConsistent);
    }

    @Test
    void percentageFilteringDerivesItsThresholdFromTotalCalls() {
        standardFixture();
        Filtering plan = new Filtering();
        plan.filterPercentage(5.0);
        finish(plan);
        assertAll(() -> assertEquals(4, ModelAbstraction.typeModel.getComponentTypes().size()),
                () -> assertNotNull(ModelAbstraction.typeModel.getComponentTypes().get("alpha.low.A")),
                () -> assertNull(ModelAbstraction.typeModel.getComponentTypes().get("alpha.low.B")), this::assertModelConsistent);
    }

    @Test
    void zeroPercentageFilteringIsANoOp() {
        Fixture fixture = standardFixture();
        long callsBefore = calls();
        Filtering plan = new Filtering();
        plan.filterPercentage(0.0);
        finish(plan);
        assertAll(() -> assertEquals(fixture.nodes().size(), ModelAbstraction.typeModel.getComponentTypes().size()),
                () -> assertEquals(fixture.invocationCount(), ModelAbstraction.executionModel.getInvocations().size()),
                () -> assertEquals(callsBefore, calls()), this::assertModelConsistent);
    }

    @Test
    void filteringRemovesAffectedOperationAndStorageDataflows() {
        standardFixture();
        Filtering plan = new Filtering();
        plan.filterWithThreshold(31);
        finish(plan);
        assertAll(() -> assertTrue(ModelAbstraction.executionModel.getOperationDataflows().isEmpty()),
                () -> assertTrue(ModelAbstraction.executionModel.getStorageDataflows().isEmpty()), this::assertModelConsistent);
    }

    @Test
    void filteringCleansStatisticsAndSourcesOfRemovedObjects() {
        standardFixture();
        Filtering plan = new Filtering();
        plan.filterWithThreshold(5);
        finish(plan);
        assertAll(() -> assertEquals(2, ModelAbstraction.statisticsModel.getStatistics().size()),
                this::assertMetadataReferencesLiveObjects, this::assertModelConsistent);
    }

    @Test
    void zeroQuantileFilteringIsANoOp() {
        Fixture fixture = standardFixture();
        Filtering plan = new Filtering();
        plan.filterQuantile(0.0);
        finish(plan);
        assertAll(() -> assertEquals(fixture.nodes().size(), ModelAbstraction.typeModel.getComponentTypes().size()),
                () -> assertEquals(fixture.invocationCount(), ModelAbstraction.executionModel.getInvocations().size()), this::assertModelConsistent);
    }

    @Test
    void hundredPercentQuantileProducesAConsistentModelOfOneComponent() {
        standardFixture();
        Filtering plan = new Filtering();
        plan.filterQuantile(100.0);
        finish(plan);
        assertAll(
                () -> assertEquals(1,ModelAbstraction.typeModel.getComponentTypes().size()),
                () -> assertEquals(1,ModelAbstraction.assemblyModel.getComponents().size()),
                () -> assertTrue(ModelAbstraction.executionModel.getInvocations().isEmpty()),
                () -> assertEquals(2,ModelAbstraction.sourceModel.getSources().size()),
                this::assertModelConsistent);
    }

    @Test
    void quantileFilteringUsesStableSignatureTieBreaking() {
        Node a = node("tie.A"); Node b = node("tie.B"); Node c = node("tie.C"); Node d = node("tie.D");
        invoke(a, d, 1, "a");
        invoke(b, d, 1, "b");
        invoke(c, d, 1, "c");
        Filtering plan = new Filtering();
        plan.filterQuantile(100.0);
        finish(plan);
        assertAll(
                () -> assertNotNull(ModelAbstraction.typeModel.getComponentTypes().get("tie.D")),
                () -> assertEquals(1, ModelAbstraction.typeModel.getComponentTypes().size()),
                this::assertModelConsistent);
    }

    @Test
    void quantileFilteringRejectsInvalidValuesWithoutMutation() {
        standardFixture();
        int before = ModelAbstraction.typeModel.getComponentTypes().size();
        assertAll(() -> assertThrows(IllegalArgumentException.class, () -> new Filtering().filterQuantile(-1.0)),
                () -> assertThrows(IllegalArgumentException.class, () -> new Filtering().filterQuantile(101.0)),
                () -> assertEquals(before, ModelAbstraction.typeModel.getComponentTypes().size()));
    }
}
