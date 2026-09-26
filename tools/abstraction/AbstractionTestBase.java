package tools.abstraction;

import kieker.model.analysismodel.assembly.*;
import kieker.model.analysismodel.deployment.*;
import kieker.model.analysismodel.execution.*;
import kieker.model.analysismodel.source.SourceFactory;
import kieker.model.analysismodel.statistics.*;
import kieker.model.analysismodel.type.*;
import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.ecore.EObject;
import org.junit.jupiter.api.BeforeEach;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Shared model fixture and consistency assertions for all abstraction mechanisms. */
abstract class AbstractionTestBase {

    @BeforeEach
    void resetModels() {
        ModelAbstraction.typeModel = TypeFactory.eINSTANCE.createTypeModel();
        ModelAbstraction.assemblyModel = AssemblyFactory.eINSTANCE.createAssemblyModel();
        ModelAbstraction.deploymentModel = DeploymentFactory.eINSTANCE.createDeploymentModel();
        ModelAbstraction.executionModel = ExecutionFactory.eINSTANCE.createExecutionModel();
        ModelAbstraction.sourceModel = SourceFactory.eINSTANCE.createSourceModel();
        ModelAbstraction.statisticsModel = StatisticsFactory.eINSTANCE.createStatisticsModel();
        AbstractionStrategy.references.clear();
        AbstractionStrategy.statisticsMapping.clear();
        AbstractionStrategy.componentCallCounts.clear();
        AbstractionStrategy.packageCallCounts.clear();
        AbstractionStrategy.visited.clear();
        AbstractionStrategy.active.clear();
        AbstractionStrategy.visitedFeatures.clear();
        ThresholdSelection.getModifyObjects().clear();
    }

    /** Seven components form two clear communities plus one isolated component. */
    Fixture standardFixture() {
        List<Node> nodes = List.of(node("alpha.low.A"), node("alpha.low.B"), node("alpha.low.C"),
                node("beta.high.D"), node("beta.high.E"), node("beta.high.F"), node("gamma.zero.G"));
        invoke(nodes.get(0), nodes.get(1), 2, "low-ab");
        invoke(nodes.get(0), nodes.get(2), 2, "low-ac");
        invoke(nodes.get(3), nodes.get(4), 30, "high-de");
        invoke(nodes.get(4), nodes.get(5), 30, "high-ef");
        operationDataflow(nodes.get(0), nodes.get(1));
        storageDataflow(nodes.get(3));
        return new Fixture(nodes, 4);
    }

    Node node(String signature) {
        int dot = signature.lastIndexOf('.');
        String pkg = dot < 0 ? "" : signature.substring(0, dot);
        String name = dot < 0 ? signature : signature.substring(dot + 1);

        ComponentType type = TypeFactory.eINSTANCE.createComponentType();
        type.setPackage(pkg);
        type.setName(name);
        type.setSignature(signature);
        OperationType operationType = TypeFactory.eINSTANCE.createOperationType();
        operationType.setName("run");
        operationType.setSignature("void run()");
        StorageType storageType = TypeFactory.eINSTANCE.createStorageType();
        storageType.setName("state");
        type.getProvidedOperations().put("run", operationType);
        type.getProvidedStorages().put("state", storageType);
        ModelAbstraction.typeModel.getComponentTypes().put(signature, type);

        AssemblyComponent assembly = AssemblyFactory.eINSTANCE.createAssemblyComponent();
        assembly.setSignature(signature);
        assembly.setComponentType(type);
        AssemblyOperation operation = AssemblyFactory.eINSTANCE.createAssemblyOperation();
        operation.setOperationType(operationType);
        assembly.getOperations().put("run", operation);
        AssemblyStorage storage = AssemblyFactory.eINSTANCE.createAssemblyStorage();
        storage.setStorageType(storageType);
        assembly.getStorages().put("state", storage);
        ModelAbstraction.assemblyModel.getComponents().put(signature, assembly);

        DeploymentContext context = ModelAbstraction.deploymentModel.getContexts().get("context");
        if (context == null) {
            context = DeploymentFactory.eINSTANCE.createDeploymentContext();
            context.setName("context");
            ModelAbstraction.deploymentModel.getContexts().put("context", context);
        }
        DeployedComponent deployed = DeploymentFactory.eINSTANCE.createDeployedComponent();
        deployed.setSignature(signature);
        deployed.setAssemblyComponent(assembly);
        DeployedOperation deployedOperation = DeploymentFactory.eINSTANCE.createDeployedOperation();
        deployedOperation.setAssemblyOperation(operation);
        deployed.getOperations().put("run", deployedOperation);
        DeployedStorage deployedStorage = DeploymentFactory.eINSTANCE.createDeployedStorage();
        deployedStorage.setAssemblyStorage(storage);
        deployed.getStorages().put("state", deployedStorage);
        context.getComponents().put(signature, deployed);

        source(type, "type:" + signature);
        source(assembly, "assembly:" + signature);
        return new Node(type, operationType, assembly, operation, storage, deployed, deployedOperation, deployedStorage);
    }

    Invocation invoke(Node caller, Node callee, long calls, String source) {
        Invocation invocation = ExecutionFactory.eINSTANCE.createInvocation();
        invocation.setCaller(caller.deployedOperation());
        invocation.setCallee(callee.deployedOperation());
        kieker.model.analysismodel.execution.Tuple<DeployedOperation, DeployedOperation> key = ExecutionFactory.eINSTANCE.createTuple();
        key.setFirst(caller.deployedOperation());
        key.setSecond(callee.deployedOperation());
        ModelAbstraction.executionModel.getInvocations().put(key, invocation);
        StatisticRecord record = StatisticsFactory.eINSTANCE.createStatisticRecord();
        record.getProperties().put("calls", calls);
        ModelAbstraction.statisticsModel.getStatistics().put(invocation, record);
        source(invocation, source);
        return invocation;
    }

    void operationDataflow(Node caller, Node callee) {
        OperationDataflow flow = ExecutionFactory.eINSTANCE.createOperationDataflow();
        flow.setCaller(caller.deployedOperation());
        flow.setCallee(callee.deployedOperation());
        flow.setDirection(EDirection.WRITE);
        kieker.model.analysismodel.execution.Tuple<DeployedOperation, DeployedOperation> key = ExecutionFactory.eINSTANCE.createTuple();
        key.setFirst(caller.deployedOperation());
        key.setSecond(callee.deployedOperation());
        ModelAbstraction.executionModel.getOperationDataflows().put(key, flow);
        source(flow, "operation-dataflow");
    }

    void storageDataflow(Node node) {
        StorageDataflow flow = ExecutionFactory.eINSTANCE.createStorageDataflow();
        flow.setCode(node.deployedOperation());
        flow.setStorage(node.deployedStorage());
        flow.setDirection(EDirection.BOTH);
        kieker.model.analysismodel.execution.Tuple<DeployedOperation, DeployedStorage> key = ExecutionFactory.eINSTANCE.createTuple();
        key.setFirst(node.deployedOperation());
        key.setSecond(node.deployedStorage());
        ModelAbstraction.executionModel.getStorageDataflows().put(key, flow);
        source(flow, "storage-dataflow");
    }

    void source(EObject object, String value) {
        BasicEList<String> values = new BasicEList<>();
        values.add(value);
        ModelAbstraction.sourceModel.getSources().put(object, values);
    }

    void transform(AbstractionStrategy plan) { new ModelTransformation(plan).transformModels(); }

    void finishAggregation(Aggregation plan) {
        transform(plan);
        plan.rebuildRepair();
        plan.finalizeModelConsistency();
    }

    void finish(AbstractionStrategy plan) {
        transform(plan);
        plan.finalizeModelConsistency();
    }

    void assertModelConsistent() {
        assertDoesNotThrow(() -> new ModelConsistency().finish(Map.of(), Set.of()));
        assertEveryInvocationHasContainedEndpoints();
        assertMetadataReferencesLiveObjects();
    }

    void assertEveryInvocationHasContainedEndpoints() {
        Set<DeployedOperation> operations = Collections.newSetFromMap(new IdentityHashMap<>());
        for (DeploymentContext context : ModelAbstraction.deploymentModel.getContexts().values())
            for (DeployedComponent component : context.getComponents().values()) operations.addAll(component.getOperations().values());
        for (Invocation invocation : ModelAbstraction.executionModel.getInvocations().values()) {
            assertTrue(invocation.getCaller() == null || operations.contains(invocation.getCaller()), "Stale invocation caller");
            assertTrue(operations.contains(invocation.getCallee()), "Stale invocation callee");
        }
    }

    void assertMetadataReferencesLiveObjects() {
        Set<EObject> live = Collections.newSetFromMap(new IdentityHashMap<>());
        addTree(ModelAbstraction.typeModel, live);
        addTree(ModelAbstraction.assemblyModel, live);
        addTree(ModelAbstraction.deploymentModel, live);
        addTree(ModelAbstraction.executionModel, live);
        ModelAbstraction.statisticsModel.getStatistics().keySet().forEach(key -> assertTrue(live.contains(key), "Stale statistic key"));
        ModelAbstraction.sourceModel.getSources().keySet().forEach(key -> assertTrue(live.contains(key), "Stale source key"));
    }

    private void addTree(EObject root, Set<EObject> target) {
        target.add(root);
        root.eAllContents().forEachRemaining(target::add); }

    Map<String, String> componentPackages() {
        Map<String, String> result = new TreeMap<>();
        for (ComponentType type : ModelAbstraction.typeModel.getComponentTypes().values()) result.put(type.getSignature(), type.getPackage());
        return result;
    }

    long calls() {
        return ModelAbstraction.statisticsModel.getStatistics().values().stream().filter(Objects::nonNull)
                .map(record -> record.getProperties().get("calls")).filter(Number.class::isInstance)
                .map(Number.class::cast).mapToLong(Number::longValue).sum();
    }

    record Node(ComponentType type, OperationType operationType, AssemblyComponent assembly,
                AssemblyOperation operation, AssemblyStorage storage, DeployedComponent deployed,
                DeployedOperation deployedOperation, DeployedStorage deployedStorage) {}
    record Fixture(List<Node> nodes, int invocationCount) {}
}
