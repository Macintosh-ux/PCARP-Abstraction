package tools.abstraction;

import kieker.model.analysismodel.assembly.*;
import kieker.model.analysismodel.deployment.*;
import kieker.model.analysismodel.execution.*;
import kieker.model.analysismodel.statistics.*;
import kieker.model.analysismodel.type.*;
import org.eclipse.emf.common.util.*;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;

import java.util.*;
import java.util.function.Function;

import static tools.abstraction.AbstractionStrategy.*;
import static tools.abstraction.ModelAbstraction.*;
import static tools.abstraction.ModelAbstraction.ANSI_GREEN;
import static tools.abstraction.ModelAbstraction.ANSI_RESET;

/** Rebuilds cross-model metadata after one abstraction mechanism has finished. */
final class ModelConsistency {
    private final List<SourceSnapshot> sources = new ArrayList<>();
    private final List<StatisticSnapshot> statistics = new ArrayList<>();
    private final List<Invocation> originalInvocations = new ArrayList<>(executionModel.getInvocations().values());
    private long originalInvocationCalls;

    ModelConsistency() {
        snapshotSources();
        snapshotStatistics();
    }

    void finish(Map<Object, Object> replacements, Set<Object> removedObjects) {
        finish(replacements, removedObjects, false);
    }

    void finish(Map<Object, Object> replacements, Set<Object> removedObjects, boolean preserveInvocations) {
        Set<EObject> liveObjects = collectArchitectureObjects();
        rebuildStatistics(replacements, removedObjects, liveObjects);
        rebuildSources(replacements, removedObjects, liveObjects);
        if (preserveInvocations) verifyInvocationPreservation(replacements, removedObjects, liveObjects);
        validate();
    }

    private void snapshotSources() {
        if (sourceModel == null) return;
        for (Map.Entry<EObject, EList<String>> entry : sourceModel.getSources()) {
            sources.add(new SourceSnapshot(entry.getKey(), new ArrayList<>(entry.getValue())));
        }
    }

    private void snapshotStatistics() {
        if (statisticsModel == null) return;
        Set<Invocation> contained = identitySet(originalInvocations);
        for (Map.Entry<EObject, StatisticRecord> entry : statisticsModel.getStatistics()) {
            Map<String, Object> properties = new LinkedHashMap<>();
            if (entry.getValue() != null) properties.putAll(entry.getValue().getProperties().map());
            statistics.add(new StatisticSnapshot(entry.getKey(), properties));
            if (contained.contains(entry.getKey()) && properties.get("calls") instanceof Number calls)
                originalInvocationCalls = Math.addExact(originalInvocationCalls, calls.longValue());
        }
    }

    private void verifyInvocationPreservation(Map<Object, Object> replacements, Set<Object> removedObjects, Set<EObject> liveObjects) {
        for (Invocation original : originalInvocations) {
            EObject target = resolve(original, replacements, removedObjects);
            if (!(target instanceof Invocation) || !liveObjects.contains(target))
                throw new IllegalStateException("Aggregation lost an original invocation: " + original);
        }
        long actual = invocationCalls(identitySet(executionModel.getInvocations().values()));
        if (actual != originalInvocationCalls)
            throw new IllegalStateException("Aggregation changed invocation calls: " + originalInvocationCalls + " -> " + actual);
        System.out.println(ANSI_CYAN + "Invocation calls preserved: " + originalInvocationCalls + " -> " + actual + ANSI_RESET);
    }

    private void rebuildStatistics(Map<Object, Object> replacements, Set<Object> removedObjects,
                                   Set<EObject> liveObjects) {
        statisticsModel.getStatistics().clear();

        for (StatisticSnapshot snapshot : statistics) {
            EObject target = resolve(snapshot.object(), replacements, removedObjects);
            if (target == null || !liveObjects.contains(target)) continue;

            StatisticRecord record = statisticsModel.getStatistics().get(target);
            if (record == null) {
                record = StatisticsFactory.eINSTANCE.createStatisticRecord();
                statisticsModel.getStatistics().put(target, record);
            }

            mergeProperties(record, snapshot.properties());
        }
    }

    private void mergeProperties(StatisticRecord target, Map<String, Object> incoming) {
        for (Map.Entry<String, Object> property : incoming.entrySet()) {
            String key = property.getKey();
            Object value = property.getValue();
            Object existing = target.getProperties().get(key);

            if ("calls".equals(key) && value instanceof Number incomingCalls) {
                if (existing != null && !(existing instanceof Number))
                    throw new IllegalStateException("Cannot merge non-numeric call statistics");
                long previous = existing == null ? 0L : ((Number) existing).longValue();
                target.getProperties().put(key, Math.addExact(previous, incomingCalls.longValue()));
            } else if (existing == null) target.getProperties().put(key, value);
            else if (!Objects.equals(existing, value)) {
                System.out.println(AbstractionStrategy.ANSI_YELLOW
                        + "Keeping first conflicting statistic property '" + key + "'."
                        + AbstractionStrategy.ANSI_RESET);
            }
        }
    }

    private void rebuildSources(Map<Object, Object> replacements, Set<Object> removedObjects,
                                Set<EObject> liveObjects) {
        sourceModel.getSources().clear();

        for (SourceSnapshot snapshot : sources) {
            EObject target = resolve(snapshot.object(), replacements, removedObjects);
            if (target == null || !liveObjects.contains(target)) continue;

            EList<String> values = sourceModel.getSources().get(target);
            if (values == null) {
                sourceModel.getSources().put(target, new BasicEList<>());
                // EMF copies list-valued map entries; append to the stored list, not the temporary input.
                values = sourceModel.getSources().get(target);
            }

            for (String value : snapshot.values()) if (!values.contains(value)) values.add(value);
        }
    }

    private EObject resolve(Object object, Map<Object, Object> replacements, Set<Object> removedObjects) {
        if (!(object instanceof EObject current) || removedObjects.contains(current)) return null;

        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (true) {
            if (!seen.add(current)) throw new IllegalStateException("Cyclic metadata replacement");
            if (!replacements.containsKey(current) || replacements.get(current) == current) return current;
            Object next = replacements.get(current);
            if (!(next instanceof EObject replacement) || removedObjects.contains(replacement)) return null;
            current = replacement;
        }
    }

    private Set<EObject> collectArchitectureObjects() {
        Set<EObject> result = Collections.newSetFromMap(new IdentityHashMap<>());
        addTree(typeModel, result);
        addTree(assemblyModel, result);
        addTree(deploymentModel, result);
        addTree(executionModel, result);
        return result;
    }

    private void addTree(EObject root, Set<EObject> result) {
        if (root == null) return;
        result.add(root);
        TreeIterator<EObject> iterator = root.eAllContents();
        while (iterator.hasNext()) result.add(iterator.next());
    }

    private void validate() {
        Set<ComponentType> types = identitySet(typeModel.getComponentTypes().values());
        Set<OperationType> operationTypes = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<StorageType> storageTypes = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ComponentType type : types) {
            operationTypes.addAll(type.getProvidedOperations().values());
            storageTypes.addAll(type.getProvidedStorages().values());
        }

        Set<AssemblyComponent> assemblyComponents = identitySet(assemblyModel.getComponents().values());
        Set<AssemblyOperation> assemblyOperations = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<AssemblyStorage> assemblyStorages = Collections.newSetFromMap(new IdentityHashMap<>());
        for (AssemblyComponent component : assemblyComponents) {
            assemblyOperations.addAll(component.getOperations().values());
            assemblyStorages.addAll(component.getStorages().values());
        }

        Set<DeployedComponent> deployedComponents = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<DeployedOperation> deployedOperations = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<DeployedStorage> deployedStorages = Collections.newSetFromMap(new IdentityHashMap<>());
        for (DeploymentContext context : deploymentModel.getContexts().values()) {
            if (context == null) continue;
            deployedComponents.addAll(context.getComponents().values());
            for (DeployedComponent component : context.getComponents().values()) {
                if (component == null) continue;
                deployedOperations.addAll(component.getOperations().values());
                deployedStorages.addAll(component.getStorages().values());
            }
        }

        Set<Invocation> invocations = identitySet(executionModel.getInvocations().values());
        List<String> errors = new ArrayList<>();

        for (AssemblyComponent component : assemblyComponents) {
            if (component == null || !types.contains(component.getComponentType())) {
                errors.add("AssemblyComponent references a missing ComponentType: " + signature(component));
                continue;
            }
            Set<OperationType> ownedOperations = identitySet(component.getComponentType().getProvidedOperations().values());
            Set<StorageType> ownedStorages = identitySet(component.getComponentType().getProvidedStorages().values());
            for (AssemblyOperation operation : component.getOperations().values()) {
                if (operation == null || operation.getComponent() != component
                        || !ownedOperations.contains(operation.getOperationType())) {
                    errors.add("Invalid AssemblyOperation in " + signature(component));
                }
            }
            for (AssemblyStorage storage : component.getStorages().values()) {
                if (storage == null || storage.getComponent() != component
                        || !ownedStorages.contains(storage.getStorageType())) {
                    errors.add("Invalid AssemblyStorage in " + signature(component));
                }
            }
        }

        for (DeployedComponent component : deployedComponents) {
            if (component == null || !assemblyComponents.contains(component.getAssemblyComponent())) {
                errors.add("DeployedComponent references a missing AssemblyComponent: " + signature(component));
                continue;
            }
            for (DeployedOperation operation : component.getOperations().values()) {
                if (operation == null || operation.getComponent() != component
                        || !assemblyOperations.contains(operation.getAssemblyOperation())
                        || operation.getAssemblyOperation().getComponent() != component.getAssemblyComponent()) {
                    errors.add("Invalid DeployedOperation in " + signature(component));
                }
            }
            for (DeployedStorage storage : component.getStorages().values()) {
                if (storage == null || storage.getComponent() != component
                        || !assemblyStorages.contains(storage.getAssemblyStorage())
                        || storage.getAssemblyStorage().getComponent() != component.getAssemblyComponent()) {
                    errors.add("Invalid DeployedStorage in " + signature(component));
                }
            }
        }

        for (Invocation invocation : invocations) {
            if (invocation == null || invocation.getCallee() == null
                    || !deployedOperations.contains(invocation.getCallee())
                    || invocation.getCaller() != null && !deployedOperations.contains(invocation.getCaller())) {
                errors.add("Invocation references a missing DeployedOperation: " + invocation);
            }
        }

        for (OperationDataflow dataflow : executionModel.getOperationDataflows().values()) {
            if (dataflow == null || dataflow.getCallee() == null
                    || !deployedOperations.contains(dataflow.getCallee())
                    || dataflow.getCaller() != null && !deployedOperations.contains(dataflow.getCaller())) {
                errors.add("OperationDataflow references a missing DeployedOperation: " + dataflow);
            }
        }

        for (StorageDataflow dataflow : executionModel.getStorageDataflows().values()) {
            if (dataflow == null || dataflow.getCode() != null && !deployedOperations.contains(dataflow.getCode())
                    || !deployedStorages.contains(dataflow.getStorage())) {
                errors.add("StorageDataflow references a missing deployment object: " + dataflow);
            }
        }

        validateExecutionMap(executionModel.getInvocations(), Invocation::getCaller, Invocation::getCallee, "Invocation", errors);
        validateExecutionMap(executionModel.getOperationDataflows(), OperationDataflow::getCaller, OperationDataflow::getCallee, "OperationDataflow", errors);
        validateExecutionMap(executionModel.getStorageDataflows(), StorageDataflow::getCode, StorageDataflow::getStorage, "StorageDataflow", errors);

        Set<EObject> liveObjects = collectArchitectureObjects();
        for (Map.Entry<EObject, StatisticRecord> entry : statisticsModel.getStatistics()) {
            if (!liveObjects.contains(entry.getKey())) errors.add("Statistics key is stale: " + entry.getKey());
        }
        for (Map.Entry<EObject, EList<String>> entry : sourceModel.getSources()) {
            if (!liveObjects.contains(entry.getKey())) errors.add("Source key is stale: " + entry.getKey());
        }

        validateProxies(typeModel, errors);
        validateProxies(assemblyModel, errors);
        validateProxies(deploymentModel, errors);
        validateProxies(executionModel, errors);
        validateProxies(statisticsModel, errors);
        validateProxies(sourceModel, errors);

        System.out.println(ANSI_GREEN
                + "\n===== COMPLETE MODEL VALIDATION ====="
                + ANSI_RESET
                + "\nComponentTypes: " + types.size()
                + "\nAssemblyComponents: " + assemblyComponents.size()
                + "\nDeployedComponents: " + deployedComponents.size()
                + "\nOperationTypes: " + operationTypes.size()
                + "\nAssemblyOperations: " + assemblyOperations.size()
                + "\nDeployedOperations: " + deployedOperations.size()
                + "\nInvocations: " + invocations.size()
                + "\nInvocation call sum: " + invocationCalls(invocations)
                + "\nStatistics entries: " + statisticsModel.getStatistics().size()
                + "\nSource entries: " + sourceModel.getSources().size()
                + "\nConsistency errors: " + errors.size()
                + ANSI_RESET);

        printDisplayedComponentInvocationEdges();

        if (!errors.isEmpty()) {
            for (int i = 0; i < Math.min(errors.size(), 25); i++) System.out.println("  - " + errors.get(i));
            throw new IllegalStateException("Abstraction produced an inconsistent model (" + errors.size() + " errors).");
        }
    }

    /** Checks map keys as well as values; live endpoints alone do not guarantee a valid execution map. */
    private <F, S, V> void validateExecutionMap(EMap<kieker.model.analysismodel.execution.Tuple<F, S>, V> entries,
                                                Function<V, F> first, Function<V, S> second, String kind, List<String> errors) {
        Set<kieker.model.analysismodel.execution.Tuple<F, S>> seen = new HashSet<>();
        for (Map.Entry<kieker.model.analysismodel.execution.Tuple<F, S>, V> entry : entries) {
            var key = entry.getKey();
            V value = entry.getValue();
            if (key == null || value == null) {
                errors.add(kind + " map contains a null key or value");
                continue;
            }
            if (key.getFirst() != first.apply(value) || key.getSecond() != second.apply(value))
                errors.add(kind + " key does not match its endpoints");
            if (!seen.add(key)) errors.add(kind + " map contains duplicate endpoint pairs");

            kieker.model.analysismodel.execution.Tuple<F, S> probe = ExecutionFactory.eINSTANCE.createTuple();
            probe.setFirst(first.apply(value));
            probe.setSecond(second.apply(value));
            if (entries.get(probe) != value) errors.add(kind + " cannot be retrieved by its current endpoints");
        }
    }

    private long invocationCalls(Set<Invocation> invocations) {
        long result = 0;
        for (Invocation invocation : invocations) {
            StatisticRecord record = statisticsModel.getStatistics().get(invocation);
            Object value = record == null ? null : record.getProperties().get("calls");
            if (value instanceof Number calls) result = Math.addExact(result, calls.longValue());
        }
        return result;
    }

    private void validateProxies(EObject root, List<String> errors) {
        if (root == null) return;
        int unresolved = EcoreUtil.UnresolvedProxyCrossReferencer.find(root).size();
        if (unresolved > 0) errors.add(root.eClass().getName() + " contains " + unresolved + " unresolved proxies.");
    }

    private <T> Set<T> identitySet(Collection<T> values) {
        Set<T> result = Collections.newSetFromMap(new IdentityHashMap<>());
        for (T value : values) if (value != null) result.add(value);
        return result;
    }

    private String signature(AssemblyComponent component) {
        return component == null ? "null" : component.getSignature();
    }

    private String signature(DeployedComponent component) {
        return component == null ? "null" : component.getSignature();
    }

    private record SourceSnapshot(EObject object, List<String> values) {}
    private record StatisticSnapshot(EObject object, Map<String, Object> properties) {}
}
