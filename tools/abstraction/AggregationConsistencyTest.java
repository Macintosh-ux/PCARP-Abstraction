package tools.abstraction;

import kieker.model.analysismodel.assembly.*;
import kieker.model.analysismodel.deployment.*;
import kieker.model.analysismodel.execution.*;
import kieker.model.analysismodel.source.SourceModel;
import kieker.model.analysismodel.statistics.*;
import kieker.model.analysismodel.type.TypeModel;
import org.eclipse.emf.common.util.EMap;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static tools.abstraction.ModelAbstraction.*;

/** Regression tests for the full aggregation pipeline, including its metadata and serialized result. */
class AggregationConsistencyTest extends AbstractionTestBase {
    @TempDir Path directory;

    private static class CountingAggregation extends Aggregation {
        int invocationRebuilds;
        @Override protected void rebuildInvocations() { invocationRebuilds++; super.rebuildInvocations(); }
    }

    @ParameterizedTest @ValueSource(strings = {"PD", "PT", "QT"})
    void everyAggregationModeRebuildsExecutionExactlyOnceAfterTransformation(String mode) {
        standardFixture();
        long before = calls();
        List<Invocation> originals = new ArrayList<>(executionModel.getInvocations().values());
        CountingAggregation plan = new CountingAggregation();
        switch (mode) {
            case "PD" -> plan.new PackageDepth().staticAggregation(0);
            case "PT" -> plan.new ThresholdAggregation().dynamicAggregationPercentage(10);
            case "QT" -> plan.new ThresholdAggregation().dynamicAggregationQuantile(50);
            default -> throw new AssertionError(mode);
        }
        assertEquals(0, plan.invocationRebuilds, "Planning must not rebuild execution prematurely");
        finishAggregation(plan);
        assertEquals(1, plan.invocationRebuilds);
        assertEquals(before, calls());
        for (Invocation old : originals) {
            Invocation target = AbstractionStrategy.resolveReplacement(old, Invocation.class);
            assertNotSame(old, target);
            assertTrue(executionModel.getInvocations().values().contains(target));
            assertNotNull(statisticsModel.getStatistics().get(target));
        }
        assertExecutionKeys();
        assertModelConsistent();
    }

    @Test void operationsAndInvocationsCollapseWithAggregatedComponents() {
        Node a = node("alpha.deep.A"), b = node("alpha.deep.B");
        invocation(a.deployedOperation(), b.deployedOperation(), 10L, "ab");
        invocation(b.deployedOperation(), a.deployedOperation(), 20L, "ba");
        finishPd(0);
        Invocation target = executionModel.getInvocations().values().iterator().next();
        assertEquals(1, typeModel.getComponentTypes().size());
        assertEquals(1, deploymentModel.getContexts().get("context").getComponents().size());
        assertSame(target.getCaller(), target.getCallee());
        assertEquals(1, target.getCaller().getComponent().getOperations().size());
        assertEquals(1, executionModel.getInvocations().size());
        assertEquals(30, calls());
        assertExecutionKeys();
        assertModelConsistent();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void identicalFinalAssemblyOperationsShareOneDeployedOperationAndMetadata(boolean separateAssembly) {
        Node a = node("alpha.deep.A"), b = node("alpha.deep.B");
        DeployedOperation alias = alias(a, "context", separateAssembly);
        source(a.deployedOperation(), "original-operation");
        source(alias, "alias-operation");
        Invocation first = invocation(a.deployedOperation(), b.deployedOperation(), 10L, "first");
        Invocation second = invocation(alias, b.deployedOperation(), 20L, "second");
        finishPd(0);
        Invocation target = executionModel.getInvocations().values().iterator().next();
        assertEquals(1, executionModel.getInvocations().size());
        assertEquals(1, statisticsModel.getStatistics().size());
        assertEquals(30, calls());
        assertSame(AbstractionStrategy.references.get(first), AbstractionStrategy.references.get(second));
        assertSame(target, AbstractionStrategy.references.get(first));
        assertEquals(Set.of("first", "second"), new HashSet<>(sourceModel.getSources().get(target)));
        assertEquals(Set.of("original-operation", "alias-operation"), new HashSet<>(sourceModel.getSources().get(target.getCaller())));
        assertEquals(1, target.getCaller().getComponent().getOperations().size());
        assertExecutionKeys();
        assertModelConsistent();
    }

    @Test void differentDeploymentContextsAreNeverCollapsedIntoOneEndpoint() {
        Node a = node("alpha.deep.A"), b = node("alpha.deep.B");
        DeployedOperation alias = alias(a, "another-context", false);
        invocation(a.deployedOperation(), b.deployedOperation(), 10L, "first-context");
        invocation(alias, b.deployedOperation(), 20L, "second-context");
        finishPd(0);
        assertEquals(2, deploymentModel.getContexts().size());
        assertEquals(2, executionModel.getInvocations().size());
        assertNotSame(a.deployedOperation().getComponent(), alias.getComponent());
        assertEquals(30, calls());
        assertExecutionKeys();
        assertModelConsistent();
    }

    @Test void entryCallsAndSelfLoopsRemainSeparateAndKeepTheirCounts() {
        Node a = node("alpha.deep.A");
        DeployedOperation alias = alias(a, "context", false);
        invocation(null, a.deployedOperation(), 3L, "entry-one");
        invocation(null, alias, 4L, "entry-two");
        invocation(a.deployedOperation(), a.deployedOperation(), 5L, "loop-one");
        invocation(alias, alias, 6L, "loop-two");
        finishPd(0);
        assertEquals(2, executionModel.getInvocations().size());
        for (Invocation invocation : executionModel.getInvocations().values()) {
            if (invocation.getCaller() == null) assertEquals(7, callCount(invocation));
            else {
                assertSame(invocation.getCaller(), invocation.getCallee());
                assertEquals(11, callCount(invocation));
            }
        }
        assertEquals(18, calls());
        assertExecutionKeys();
        assertModelConsistent();
    }

    @Test void collidingDataflowsMergeDirectionsStatisticsAndSources() {
        Node a = node("alpha.deep.A"), b = node("alpha.deep.B");
        DeployedOperation alias = alias(a, "context", false);
        operationFlow(a.deployedOperation(), b.deployedOperation(), EDirection.READ, 3L, "read-op");
        operationFlow(alias, b.deployedOperation(), EDirection.WRITE, 4L, "write-op");
        storageFlow(a.deployedOperation(), b.deployedStorage(), EDirection.READ, 5L, "read-storage");
        storageFlow(alias, b.deployedStorage(), EDirection.WRITE, 6L, "write-storage");
        finishPd(0);
        assertEquals(1, executionModel.getOperationDataflows().size());
        assertEquals(1, executionModel.getStorageDataflows().size());
        OperationDataflow operation = executionModel.getOperationDataflows().values().iterator().next();
        StorageDataflow storage = executionModel.getStorageDataflows().values().iterator().next();
        assertEquals(EDirection.BOTH, operation.getDirection());
        assertEquals(EDirection.BOTH, storage.getDirection());
        assertEquals(7, callCount(operation));
        assertEquals(11, callCount(storage));
        assertEquals(Set.of("read-op", "write-op"), new HashSet<>(sourceModel.getSources().get(operation)));
        assertEquals(Set.of("read-storage", "write-storage"), new HashSet<>(sourceModel.getSources().get(storage)));
        assertExecutionKeys();
        assertModelConsistent();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void missingStatisticsDoNotInventCallCounts(boolean oneKnownCount) {
        Node a = node("alpha.deep.A"), b = node("alpha.deep.B");
        DeployedOperation alias = alias(a, "context", false);
        invocation(a.deployedOperation(), b.deployedOperation(), oneKnownCount ? 10L : null, "first");
        invocation(alias, b.deployedOperation(), null, "unknown-count");
        finishPd(0);
        assertEquals(1, executionModel.getInvocations().size());
        assertEquals(oneKnownCount ? 1 : 0, statisticsModel.getStatistics().size());
        assertEquals(oneKnownCount ? 10 : 0, calls());
        assertEquals(2, sourceModel.getSources().get(executionModel.getInvocations().values().iterator().next()).size());
        assertModelConsistent();
    }

    @Test void statisticsKeepLongPrecisionAndAreNotAddedTwiceInComposition() {
        Node a = node("alpha.deep.A"), b = node("alpha.deep.B");
        DeployedOperation alias = alias(a, "context", false);
        invocation(a.deployedOperation(), b.deployedOperation(), 3_000_000_000L, "first");
        invocation(alias, b.deployedOperation(), 4_000_000_000L, "second");
        finishPd(1);
        assertEquals(7_000_000_000L, calls());
        finishPd(0);
        assertEquals(7_000_000_000L, calls());
        assertEquals(1, statisticsModel.getStatistics().size());
        assertExecutionKeys();
        assertModelConsistent();
    }

    @Test void integerCallValuesAreWrittenAsLongsForMvis() {
        Node a = node("alpha.deep.A"), b = node("alpha.deep.B");
        invocation(a.deployedOperation(), b.deployedOperation(), Integer.valueOf(12), "integer");
        finishPd(0);
        Object value = statisticsModel.getStatistics().values().iterator().next().getProperties().get("calls");
        assertInstanceOf(Long.class, value);
        assertEquals(12L, value);
    }

    @Test void invalidCalleeFailsBeforeTheExecutionMapIsCleared() {
        Node a = node("alpha.deep.A");
        Invocation invalid = invocation(a.deployedOperation(), null, 5L, "invalid");
        AbstractionStrategy plan = new AbstractionStrategy();
        assertThrows(IllegalStateException.class, plan::rebuildInvocations);
        assertEquals(1, executionModel.getInvocations().size());
        assertSame(invalid, executionModel.getInvocations().values().iterator().next());
        assertEquals(5, calls());
    }

    @Test void replacementChainsAreResolvedAndCyclesAreRejected() {
        Node a = node("alpha.deep.A"), b = node("alpha.deep.B"), c = node("alpha.deep.C");
        invocation(a.deployedOperation(), c.deployedOperation(), 10L, "first");
        invocation(b.deployedOperation(), c.deployedOperation(), 20L, "second");
        AbstractionStrategy plan = new AbstractionStrategy();
        AbstractionStrategy.references.put(a.deployedOperation(), b.deployedOperation());
        AbstractionStrategy.references.put(b.deployedOperation(), c.deployedOperation());
        plan.rebuildInvocations();
        plan.finalizeModelConsistency();
        assertEquals(1, executionModel.getInvocations().size());
        assertEquals(30, calls());
        assertExecutionKeys();
        AbstractionStrategy.references.put(c.deployedOperation(), a.deployedOperation());
        assertThrows(IllegalStateException.class, plan::rebuildInvocations);
        assertEquals(1, executionModel.getInvocations().size());
    }

    @Test void validationRejectsAMapKeyThatDoesNotMatchTheInvocation() {
        Node a = node("alpha.deep.A"), b = node("alpha.deep.B");
        Invocation invocation = invocation(a.deployedOperation(), b.deployedOperation(), 10L, "wrong-key");
        invocation.setCaller(b.deployedOperation());
        assertThrows(IllegalStateException.class, () -> new ModelConsistency().finish(Map.of(), Set.of()));
    }

    @Test void aggregationRejectsAnInvocationLostBeforeMetadataFinalization() {
        Node a = node("alpha.deep.A"), b = node("alpha.deep.B");
        invocation(a.deployedOperation(), b.deployedOperation(), 10L, "must-survive");
        Aggregation plan = new Aggregation();
        plan.new PackageDepth().staticAggregation(0);
        transform(plan);
        plan.rebuildRepair();
        executionModel.getInvocations().clear();
        assertThrows(IllegalStateException.class, plan::finalizeModelConsistency);
    }

    @Test void metadataCallOverflowFailsInsteadOfSilentlyWrapping() {
        Node a = node("alpha.deep.A"), b = node("alpha.deep.B");
        DeployedOperation alias = alias(a, "context", false);
        invocation(a.deployedOperation(), b.deployedOperation(), Long.MAX_VALUE, "first");
        invocation(alias, b.deployedOperation(), 1L, "second");
        assertThrows(ArithmeticException.class, () -> finishPd(0));
    }

    @Test void allSixModelsRemainConsistentAfterAnXmiRoundTrip() throws Exception {
        Node a = node("alpha.deep.A"), b = node("alpha.deep.B");
        DeployedOperation alias = alias(a, "context", true);
        invocation(a.deployedOperation(), b.deployedOperation(), 10L, "first");
        invocation(alias, b.deployedOperation(), 20L, "second");
        operationFlow(a.deployedOperation(), b.deployedOperation(), EDirection.READ, 3L, "read");
        operationFlow(alias, b.deployedOperation(), EDirection.WRITE, 4L, "write");
        storageFlow(null, b.deployedStorage(), EDirection.READ, 2L, "external-storage");
        finishPd(0);
        long before = calls();
        String[] names = {"type", "assembly", "deployment", "execution", "statistics", "source"};
        EObject[] models = {typeModel, assemblyModel, deploymentModel, executionModel, statisticsModel, sourceModel};
        ResourceSetImpl resources = resourceSet();
        for (int i = 0; i < names.length; i++)
            resources.createResource(uri(names[i])).getContents().add(models[i]);
        for (Resource resource : resources.getResources()) resource.save(Map.of());

        ResourceSetImpl loaded = resourceSet();
        for (int i = 0; i < names.length; i++) models[i] = loaded.getResource(uri(names[i]), true).getContents().get(0);
        EcoreUtil.resolveAll(loaded);
        typeModel = (TypeModel) models[0]; assemblyModel = (AssemblyModel) models[1];
        deploymentModel = (DeploymentModel) models[2]; executionModel = (ExecutionModel) models[3];
        statisticsModel = (StatisticsModel) models[4]; sourceModel = (SourceModel) models[5];
        assertEquals(before, calls());
        assertEquals(1, executionModel.getInvocations().size());
        assertEquals(30, callCount(executionModel.getInvocations().values().iterator().next()));
        assertEquals(2, sourceModel.getSources().get(executionModel.getInvocations().values().iterator().next()).size());
        assertExecutionKeys();
        assertModelConsistent();
    }

    private void finishPd(int depth) {
        Aggregation plan = new Aggregation();
        plan.new PackageDepth().staticAggregation(depth);
        finishAggregation(plan);
    }

    /** Creates an additional deployed instance, optionally with a separate assembly of the same type. */
    private DeployedOperation alias(Node node, String contextName, boolean separateAssembly) {
        DeploymentContext context = deploymentModel.getContexts().get(contextName);
        if (context == null) {
            context = DeploymentFactory.eINSTANCE.createDeploymentContext();
            context.setName(contextName);
            deploymentModel.getContexts().put(contextName, context);
        }
        AssemblyComponent assembly = node.assembly();
        AssemblyOperation operation = node.operation();
        if (separateAssembly) {
            assembly = AssemblyFactory.eINSTANCE.createAssemblyComponent();
            assembly.setSignature(node.type().getSignature() + ".second-instance");
            assembly.setComponentType(node.type());
            operation = AssemblyFactory.eINSTANCE.createAssemblyOperation();
            operation.setOperationType(node.operationType());
            assembly.getOperations().put("run", operation);
            assemblyModel.getComponents().put(assembly.getSignature(), assembly);
        }
        DeployedComponent component = DeploymentFactory.eINSTANCE.createDeployedComponent();
        component.setSignature(node.type().getSignature() + ".alias");
        component.setAssemblyComponent(assembly);
        DeployedOperation result = DeploymentFactory.eINSTANCE.createDeployedOperation();
        result.setAssemblyOperation(operation);
        component.getOperations().put("run", result);
        context.getComponents().put(component.getSignature(), component);
        return result;
    }

    private Invocation invocation(DeployedOperation caller, DeployedOperation callee, Number calls, String source) {
        Invocation result = ExecutionFactory.eINSTANCE.createInvocation();
        result.setCaller(caller); result.setCallee(callee);
        executionModel.getInvocations().put(key(caller, callee), result);
        metadata(result, calls, source);
        return result;
    }

    private void operationFlow(DeployedOperation caller, DeployedOperation callee, EDirection direction, long calls, String source) {
        OperationDataflow flow = ExecutionFactory.eINSTANCE.createOperationDataflow();
        flow.setCaller(caller); flow.setCallee(callee); flow.setDirection(direction);
        executionModel.getOperationDataflows().put(key(caller, callee), flow);
        metadata(flow, calls, source);
    }

    private void storageFlow(DeployedOperation code, DeployedStorage storage, EDirection direction, long calls, String source) {
        StorageDataflow flow = ExecutionFactory.eINSTANCE.createStorageDataflow();
        flow.setCode(code); flow.setStorage(storage); flow.setDirection(direction);
        executionModel.getStorageDataflows().put(key(code, storage), flow);
        metadata(flow, calls, source);
    }

    private void metadata(EObject object, Number calls, String label) {
        if (calls != null) {
            StatisticRecord record = StatisticsFactory.eINSTANCE.createStatisticRecord();
            record.getProperties().put("calls", calls);
            statisticsModel.getStatistics().put(object, record);
        }
        source(object, label);
    }

    private long callCount(EObject object) {
        return ((Number) statisticsModel.getStatistics().get(object).getProperties().get("calls")).longValue();
    }

    private void assertExecutionKeys() {
        assertKeys(executionModel.getInvocations(), Invocation::getCaller, Invocation::getCallee);
        assertKeys(executionModel.getOperationDataflows(), OperationDataflow::getCaller, OperationDataflow::getCallee);
        assertKeys(executionModel.getStorageDataflows(), StorageDataflow::getCode, StorageDataflow::getStorage);
    }

    private <F, S, V> void assertKeys(EMap<kieker.model.analysismodel.execution.Tuple<F, S>, V> map, Function<V, F> first, Function<V, S> second) {
        for (var entry : map) {
            assertSame(first.apply(entry.getValue()), entry.getKey().getFirst());
            assertSame(second.apply(entry.getValue()), entry.getKey().getSecond());
            assertSame(entry.getValue(), map.get(key(first.apply(entry.getValue()), second.apply(entry.getValue()))));
        }
    }

    private static <F, S> kieker.model.analysismodel.execution.Tuple<F, S> key(F first, S second) {
        kieker.model.analysismodel.execution.Tuple<F, S> result = ExecutionFactory.eINSTANCE.createTuple();
        result.setFirst(first); result.setSecond(second);
        return result;
    }

    private URI uri(String name) {
        return URI.createFileURI(directory.resolve(name + "-model.xmi").toString());
    }
    private ResourceSetImpl resourceSet() {
        ResourceSetImpl result = new ResourceSetImpl();
        result.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
        return result;
    }
}
