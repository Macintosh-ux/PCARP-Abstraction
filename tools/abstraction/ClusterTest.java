package tools.abstraction;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ClusterTest extends AbstractionTestBase {

    @Test
    void manualResolutionFindsTwoSeparatedCommunitiesAndResiduals() {
        Fixture fixture = standardFixture();
        Cluster plan = new Cluster();
        plan.cluster(1.0);
        finish(plan);
        Map<String, String> packages = componentPackages();
        assertAll(() -> assertEquals(fixture.nodes().size(), packages.size()),
                () -> assertEquals("RESIDUALS", packages.get("gamma.zero.G")),
                () -> assertEquals(packages.get("alpha.low.A"), packages.get("alpha.low.B")),
                () -> assertEquals(packages.get("beta.high.D"), packages.get("beta.high.F")),
                () -> assertNotEquals(packages.get("alpha.low.A"), packages.get("beta.high.D")), this::assertModelConsistent);
    }

    @Test
    void clusteringWithFixedSeedIsDeterministic() {
        standardFixture();
        Cluster first = new Cluster();
        first.cluster(1.0);
        finish(first);
        Map<String, String> expected = componentPackages();
        resetModels();
        standardFixture();
        Cluster second = new Cluster();
        second.cluster(1.0);
        finish(second);
        assertEquals(expected, componentPackages());
    }

    @Test
    void automaticResolutionProducesAValidDeterministicPartition() {
        standardFixture();
        Cluster plan = new Cluster();
        plan.cluster();
        finish(plan);
        Map<String, String> packages = componentPackages();
        assertAll(() -> assertTrue(packages.values().stream().anyMatch(value -> value.startsWith("CLUSTER:"))),
                () -> assertTrue(packages.values().stream().anyMatch("RESIDUALS"::equals)), this::assertModelConsistent);
    }

    @Test
    void modelWithoutInterComponentCommunicationBecomesResidualOnly() {
        Node a = node("isolated.A");
        Node b = node("isolated.B");
        invoke(a, a, 5, "self");
        Cluster plan = new Cluster();
        plan.cluster(1.0);
        finish(plan);
        assertAll(() -> assertEquals("RESIDUALS", a.type().getPackage()),
                () -> assertEquals("RESIDUALS", b.type().getPackage()),
                () -> assertEquals(1, ModelAbstraction.executionModel.getInvocations().size()), this::assertModelConsistent);
    }

    @Test
    void reciprocalInvocationsRemainIntactAfterUndirectedClustering() {
        Node a = node("left.A");
        Node b = node("right.B");
        invoke(a, b, 7, "forward");
        invoke(b, a, 3, "backward");
        long callsBefore = calls();
        Cluster plan = new Cluster();
        plan.cluster(1.0);
        finish(plan);
        assertAll(() -> assertEquals(2, ModelAbstraction.executionModel.getInvocations().size()),
                () -> assertEquals(callsBefore, calls()), this::assertModelConsistent);
    }

    @Test
    void clusteringPreservesAllArchitectureObjectsAndMetadata() {
        Fixture fixture = standardFixture();
        long callsBefore = calls();
        int assemblyBefore = ModelAbstraction.assemblyModel.getComponents().size();
        Cluster plan = new Cluster();
        plan.cluster(1.0);
        finish(plan);
        assertAll(() -> assertEquals(fixture.nodes().size(), ModelAbstraction.typeModel.getComponentTypes().size()),
                () -> assertEquals(assemblyBefore, ModelAbstraction.assemblyModel.getComponents().size()),
                () -> assertEquals(fixture.invocationCount(), ModelAbstraction.executionModel.getInvocations().size()),
                () -> assertEquals(1, ModelAbstraction.executionModel.getOperationDataflows().size()),
                () -> assertEquals(1, ModelAbstraction.executionModel.getStorageDataflows().size()),
                () -> assertEquals(callsBefore, calls()), this::assertModelConsistent);
    }

    @Test
    void manualResolutionRejectsNonPositiveAndNonFiniteValues() {
        standardFixture();
        assertAll(() -> assertThrows(IllegalArgumentException.class, () -> new Cluster().cluster(0)),
                () -> assertThrows(IllegalArgumentException.class, () -> new Cluster().cluster(-1)),
                () -> assertThrows(IllegalArgumentException.class, () -> new Cluster().cluster(Double.NaN)),
                () -> assertThrows(IllegalArgumentException.class, () -> new Cluster().cluster(Double.POSITIVE_INFINITY)));
    }
}
