package tools.abstraction;

import kieker.analysis.util.Tuple;
import kieker.model.analysismodel.assembly.*;
import kieker.model.analysismodel.deployment.*;
import kieker.model.analysismodel.execution.ExecutionFactory;
import kieker.model.analysismodel.execution.EDirection;
import kieker.model.analysismodel.execution.Invocation;
import kieker.model.analysismodel.execution.OperationDataflow;
import kieker.model.analysismodel.execution.StorageDataflow;
import kieker.model.analysismodel.statistics.StatisticRecord;
import kieker.model.analysismodel.type.*;
import org.eclipse.emf.common.util.BasicEMap;
import org.eclipse.emf.common.util.EMap;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;

import java.util.*;

import static tools.abstraction.ModelAbstraction.*;

public class AbstractionStrategy {

    protected static final Set<Tuple<Object, EStructuralFeature>> visitedFeatures = new HashSet<>();
    protected static Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    protected static Set<Object> active = Collections.newSetFromMap(new IdentityHashMap<>());

    protected static Map<Object, Object> references = new IdentityHashMap<>();
    protected final Set<Object> removedObjects = Collections.newSetFromMap(new IdentityHashMap<>());
    private final ModelConsistency modelConsistency = new ModelConsistency();

    protected static final Map<String, ComponentType> componentTypeRebuildMap = new HashMap<>();
    protected final Map<String, AssemblyComponent> assemblyAggregationMapping = new LinkedHashMap<>();
    protected final Map<OperationType, String> operationTypeTargetKeys = new IdentityHashMap<>();
    protected final Map<DeploymentContext, Map<String, DeployedComponent>> deploymentAggregationMappings = new IdentityHashMap<>();
    protected InstructionSet instructions = new InstructionSet();
    protected Map<Object, Object> keyMapping = new HashMap<>();

    protected static final Map<Invocation, Integer> statisticsMapping = new IdentityHashMap<>();
    protected static final Map<String, Integer> packageCallCounts = new HashMap<>();
    protected static Map<ComponentType, Integer> componentCallCounts = new IdentityHashMap<>();

    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_CYAN = "\033[36m";
    public static final String ANSI_RESET = "\u001B[0m";

    public AbstractionStrategy() {}

    protected void markRemovedObjects(Collection<?> objects) {
        removedObjects.addAll(objects);
    }

    protected void finalizeModelConsistency() {
        modelConsistency.finish(references, removedObjects);
    }

    protected void finalizeModelConsistency(boolean preserveInvocations) {
        modelConsistency.finish(references, removedObjects, preserveInvocations);
    }

    /**
     * Recreates a ComponentType that is not affected by aggregation.
     * The model rebuild still requires a mapped target object, so package,
     * name and signature are copied without changing their identity semantics.
     */
    protected void copyUnchangedComponent(
            String oldKey,
            ComponentType oldType) {

        ComponentType targetType =
                componentTypeRebuildMap.computeIfAbsent(
                        oldKey,
                        key -> {
                            ComponentType created =
                                    TypeFactory.eINSTANCE
                                            .createComponentType();

                            created.setPackage(
                                    oldType.getPackage()
                            );

                            created.setName(
                                    oldType.getName()
                            );

                            created.setSignature(
                                    oldType.getSignature()
                            );

                            return created;
                        }
                );

        keyMapping.put(
                oldKey,
                oldKey
        );

        references.put(
                oldType,
                targetType
        );

        moveComponentTypeReferences(
                oldType,
                targetType
        );
    }

    protected void moveComponentTypeReferences(ComponentType oldType, ComponentType newType) {
        List<ComponentType> containedComponents = new ArrayList<>(oldType.getContainedComponents());
        for (ComponentType containedComponent : containedComponents) {
            newType.getContainedComponents().add(
                    containedComponent
            );
        }

        List<Map.Entry<String, OperationType>> operations = new ArrayList<>(oldType.getProvidedOperations());
        for (Map.Entry<String, OperationType> operationEntry
                : operations) {

            OperationType operationType =
                    operationEntry.getValue();

            String targetKey =
                    makeUniqueKey(
                            newType.getProvidedOperations(),
                            operationEntry.getKey(),
                            oldType.getSignature()
                    );

            newType.getProvidedOperations().put(
                    targetKey,
                    operationType
            );

            operationTypeTargetKeys.put(
                    operationType,
                    targetKey
            );
        }

        List<Map.Entry<String, StorageType>> providedStorages = new ArrayList<>(oldType.getProvidedStorages());
        for (Map.Entry<String, StorageType> providedStorage : providedStorages) {
            String targetKey = makeUniqueKey(
                    newType.getProvidedStorages(),
                    providedStorage.getKey(),
                    oldType.getSignature()
            );

            newType.getProvidedStorages().put(
                    targetKey,
                    providedStorage.getValue()
            );
        }

        List<ProvidedInterfaceType> providedInterfaceTypes =
                new ArrayList<>(oldType.getProvidedInterfaceTypes());

        for (ProvidedInterfaceType providedInterfaceType : providedInterfaceTypes) {
            boolean alreadyContained =
                    newType.getProvidedInterfaceTypes()
                            .stream()
                            .anyMatch(existing ->
                                    existing == providedInterfaceType
                            );

            if (!alreadyContained) {
                newType.getProvidedInterfaceTypes()
                        .add(providedInterfaceType);
            }
        }

        List<RequiredInterfaceType> requiredInterfaceTypes =
                new ArrayList<>(oldType.getRequiredInterfaceTypes());

        for (RequiredInterfaceType requiredInterfaceType
                : requiredInterfaceTypes) {

            boolean alreadyContained =
                    newType.getRequiredInterfaceTypes()
                            .stream()
                            .anyMatch(existing ->
                                    existing == requiredInterfaceType
                            );

            if (!alreadyContained) {
                newType.getRequiredInterfaceTypes()
                        .add(requiredInterfaceType);
            }
        }
    }

    /** Repairs structure and installs aggregated components, repairs endpoints, then rebuilds all execution keys. */
    protected void rebuildRepair() {
        rebuildComponentTypeMap();
        rebuildAssemblyComponentMap();
        rebuildDeployedComponentMaps();
        repairDeployedOperationReferences();
        repairAssemblyOperationTypeReferences();
        rebuildInvocations();
        rebuildOperationDataflows();
        rebuildStorageDataflows();
    }

    public static void computeStatisticsMapping() {
        statisticsMapping.clear();

        for (Map.Entry<EObject, StatisticRecord> entry : statisticsModel.getStatistics()) {

            if (!(entry.getKey() instanceof Invocation invocation)) {
                continue;
            }

            Object calls = entry.getValue().getProperties().get("calls");

            if (calls != null) {
                statisticsMapping.put(
                        invocation,
                        Integer.parseInt(calls.toString())
                );
            }
        }
    }

    protected static void refreshExecutionMaps() {
        if (executionModel == null) return;

        EcoreUtil.resolveAll(executionModel);

        refreshExecutionMap(executionModel.getInvocations());
        refreshExecutionMap(executionModel.getOperationDataflows());
        refreshExecutionMap(executionModel.getStorageDataflows());
    }

    private static <K, V> void refreshExecutionMap(EMap<K, V> map) {
        if (map.isEmpty()) return;

        List<Map.Entry<K, V>> entries = new ArrayList<>(map);

        map.clear();

        for (Map.Entry<K, V> entry : entries) {
            if (entry instanceof BasicEMap.Entry<?, ?> emfEntry) {
                emfEntry.setHash(-1); // mark as invalid
            }
        }

        map.addAll(entries); // reinsert all entries
    }

    /** Rebuilds unique operation pairs; metadata is merged later through the complete replacement map. */
    protected void rebuildInvocations() {
        List<Invocation> originals = new ArrayList<>(executionModel.getInvocations().values());
        Map<ExecutionEndpoints, Invocation> rebuilt = new LinkedHashMap<>();
        Map<Object, Object> replacements = new IdentityHashMap<>();
        Set<DeployedOperation> live = deployedOperations();

        for (Invocation original : originals) {
            if (original == null) throw new IllegalStateException("Cannot rebuild a null Invocation");
            DeployedOperation caller = resolveReplacement(original.getCaller(), DeployedOperation.class);
            DeployedOperation callee = resolveReplacement(original.getCallee(), DeployedOperation.class);
            requireOperation(caller, live, true);
            requireOperation(callee, live, false);

            ExecutionEndpoints endpoints = new ExecutionEndpoints(caller, callee);
            Invocation target = rebuilt.computeIfAbsent(endpoints, key -> {
                Invocation created = ExecutionFactory.eINSTANCE.createInvocation();
                created.setCaller(caller);
                created.setCallee(callee);
                return created;
            });
            replacements.put(original, target);
        }

        executionModel.getInvocations().clear();
        for (Invocation invocation : rebuilt.values()) {
            executionModel.getInvocations().put(executionKey(invocation.getCaller(), invocation.getCallee()), invocation);
        }
        references.putAll(replacements);
        System.out.println(ANSI_CYAN + "\nInvocations: " + originals.size() + " -> " + rebuilt.size()
                + " (merged " + (originals.size() - rebuilt.size()) + ")" + ANSI_RESET);
    }

    /** Resolves replacement chains without silently accepting removed targets or cycles. */
    protected static <T> T resolveReplacement(T object, Class<T> type) {
        if (object == null) return null;
        Object current = object;
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (true) {
            if (!type.isInstance(current)) throw new IllegalStateException("Invalid replacement for " + type.getSimpleName());
            if (!seen.add(current)) throw new IllegalStateException("Cyclic replacement for " + type.getSimpleName());
            if (!references.containsKey(current) || references.get(current) == current) return type.cast(current);
            current = references.get(current);
        }
    }

    private Set<DeployedOperation> deployedOperations() {
        Set<DeployedOperation> result = Collections.newSetFromMap(new IdentityHashMap<>());
        for (DeploymentContext context : deploymentModel.getContexts().values())
            for (DeployedComponent component : context.getComponents().values()) result.addAll(component.getOperations().values());
        return result;
    }

    private void requireOperation(DeployedOperation operation, Set<DeployedOperation> live, boolean allowEntry) {
        if (operation == null && allowEntry) return;
        if (operation == null || !live.contains(operation))
            throw new IllegalStateException("Execution endpoint is not a contained DeployedOperation: " + operation);
    }

    private static <F, S> kieker.model.analysismodel.execution.Tuple<F, S> executionKey(F first, S second) {
        kieker.model.analysismodel.execution.Tuple<F, S> key = ExecutionFactory.eINSTANCE.createTuple();
        key.setFirst(first);
        key.setSecond(second);
        return key;
    }

    /** Endpoint identity matters; equal names do not make different operations equivalent. */
    private record ExecutionEndpoints(EObject first, EObject second) {
        @Override public boolean equals(Object object) {
            return object instanceof ExecutionEndpoints other && first == other.first && second == other.second;
        }
        @Override public int hashCode() { return 31 * System.identityHashCode(first) + System.identityHashCode(second); }
    }

    void rebuildComponentTypeMap() {
        EMap<String, ComponentType> componentTypez = typeModel.getComponentTypes();

        componentTypez.clear();

        for (Map.Entry<String, ComponentType> entry : componentTypeRebuildMap.entrySet()) {

            componentTypez.put(
                    entry.getKey(),
                    entry.getValue()
            );
        }
    }

    void rebuildAssemblyComponentMap() {
        EMap<String, AssemblyComponent> components = assemblyModel.getComponents();

        components.clear();

        for (Map.Entry<String, AssemblyComponent> entry : assemblyAggregationMapping.entrySet()) {

            if (entry.getValue() == null) {
                throw new IllegalStateException(
                        "NULL in assemblyAggregationMapping for key: "
                                + entry.getKey()
                );
            }

            components.put(
                    entry.getKey(),
                    entry.getValue()
            );
        }
    }

    protected void rebuildDeployedComponentMaps() {
        for (Map.Entry<
                DeploymentContext,
                Map<String, DeployedComponent>
                > contextEntry
                : deploymentAggregationMappings.entrySet()) {

            DeploymentContext context =
                    contextEntry.getKey();

            Map<String, DeployedComponent> aggregatedComponents =
                    contextEntry.getValue();

            EMap<String, DeployedComponent> components =
                    context.getComponents();

            components.clear();

            for (Map.Entry<String, DeployedComponent> componentEntry
                    : aggregatedComponents.entrySet()) {

                components.put(
                        componentEntry.getKey(),
                        componentEntry.getValue()
                );
            }
        }
    }

    void repairAssemblyOperationTypeReferences() {
        for (Map.Entry<String, AssemblyComponent> componentEntry
                : assemblyModel.getComponents()) {

            AssemblyComponent assemblyComponent =
                    componentEntry.getValue();

            ComponentType componentType =
                    assemblyComponent.getComponentType();

            if (componentType == null) {
                continue;
            }

            for (Map.Entry<String, AssemblyOperation> operationEntry
                    : assemblyComponent.getOperations()) {

                AssemblyOperation assemblyOperation =
                        operationEntry.getValue();

                OperationType currentType =
                        assemblyOperation.getOperationType();

                if (currentType != null && !currentType.eIsProxy()) {
                    continue;
                }

                String operationKey =
                        operationEntry.getKey();

                OperationType replacement =
                        componentType.getProvidedOperations()
                                .get(operationKey);

                if (replacement != null) {
                    assemblyOperation.setOperationType(replacement);
                } else {
                    System.out.println(
                            ANSI_RED
                                    + "No OperationType found"
                                    + "\nAssemblyComponent: "
                                    + assemblyComponent.getSignature()
                                    + "\nOperation key: "
                                    + operationKey
                                    + ANSI_RESET
                    );
                }
            }
        }
    }

    /** Rebuilds operation dataflows after endpoint replacement, retaining both access directions. */
    protected void rebuildOperationDataflows() {
        Map<ExecutionEndpoints, OperationDataflow> rebuilt = new LinkedHashMap<>();
        Map<Object, Object> replacements = new IdentityHashMap<>();
        Set<DeployedOperation> live = deployedOperations();

        for (OperationDataflow original : new ArrayList<>(executionModel.getOperationDataflows().values())) {
            if (original == null) throw new IllegalStateException("Cannot rebuild a null OperationDataflow");
            DeployedOperation caller = resolveReplacement(original.getCaller(), DeployedOperation.class);
            DeployedOperation callee = resolveReplacement(original.getCallee(), DeployedOperation.class);
            requireOperation(caller, live, true);
            requireOperation(callee, live, false);

            ExecutionEndpoints endpoints = new ExecutionEndpoints(caller, callee);
            OperationDataflow target = rebuilt.get(endpoints);
            if (target == null) {
                target = ExecutionFactory.eINSTANCE.createOperationDataflow();
                target.setCaller(caller);
                target.setCallee(callee);
                target.setDirection(original.getDirection());
                rebuilt.put(endpoints, target);
            } else target.setDirection(mergeDirections(target.getDirection(), original.getDirection()));
            replacements.put(original, target);
        }

        executionModel.getOperationDataflows().clear();
        for (OperationDataflow flow : rebuilt.values())
            executionModel.getOperationDataflows().put(executionKey(flow.getCaller(), flow.getCallee()), flow);
        references.putAll(replacements);
    }

    /** Recreates storage-dataflow keys too, because their operation endpoint may have changed. */
    protected void rebuildStorageDataflows() {
        Map<ExecutionEndpoints, StorageDataflow> rebuilt = new LinkedHashMap<>();
        Map<Object, Object> replacements = new IdentityHashMap<>();
        Set<DeployedOperation> live = deployedOperations();
        Set<DeployedStorage> storages = Collections.newSetFromMap(new IdentityHashMap<>());
        for (DeploymentContext context : deploymentModel.getContexts().values())
            for (DeployedComponent component : context.getComponents().values()) storages.addAll(component.getStorages().values());

        for (StorageDataflow original : new ArrayList<>(executionModel.getStorageDataflows().values())) {
            if (original == null) throw new IllegalStateException("Cannot rebuild a null StorageDataflow");
            DeployedOperation code = resolveReplacement(original.getCode(), DeployedOperation.class);
            DeployedStorage storage = resolveReplacement(original.getStorage(), DeployedStorage.class);
            requireOperation(code, live, true);
            if (storage == null || !storages.contains(storage))
                throw new IllegalStateException("StorageDataflow references a missing DeployedStorage");

            ExecutionEndpoints endpoints = new ExecutionEndpoints(code, storage);
            StorageDataflow target = rebuilt.get(endpoints);
            if (target == null) {
                target = ExecutionFactory.eINSTANCE.createStorageDataflow();
                target.setCode(code);
                target.setStorage(storage);
                target.setDirection(original.getDirection());
                rebuilt.put(endpoints, target);
            } else target.setDirection(mergeDirections(target.getDirection(), original.getDirection()));
            replacements.put(original, target);
        }

        executionModel.getStorageDataflows().clear();
        for (StorageDataflow flow : rebuilt.values())
            executionModel.getStorageDataflows().put(executionKey(flow.getCode(), flow.getStorage()), flow);
        references.putAll(replacements);
    }

    /**
     * Reconnects deployed operations to AssemblyOperations contained in the
     * rebuilt AssemblyModel. A non-null reference may still be stale when its
     * target is no longer contained in an AssemblyComponent operation map.
     */
    void repairDeployedOperationReferences() {
        Set<AssemblyOperation> containedAssemblyOperations =
                Collections.newSetFromMap(new IdentityHashMap<>());

        for (AssemblyComponent component
                : assemblyModel.getComponents().values()) {

            if (component != null) {
                containedAssemblyOperations.addAll(
                        component.getOperations().values()
                );
            }
        }

        int replacedFromMap = 0;
        int repairedStale = 0;
        int unresolved = 0;

        for (DeploymentContext context
                : deploymentModel.getContexts().values()) {

            if (context == null) {
                continue;
            }

            for (DeployedComponent deployedComponent
                    : context.getComponents().values()) {

                if (deployedComponent == null) {
                    continue;
                }

                AssemblyComponent assemblyComponent =
                        deployedComponent.getAssemblyComponent();

                if (assemblyComponent == null) {
                    continue;
                }

                for (Map.Entry<String, DeployedOperation> operationEntry
                        : deployedComponent.getOperations()) {

                    DeployedOperation deployedOperation =
                            operationEntry.getValue();

                    if (deployedOperation == null) {
                        continue;
                    }

                    AssemblyOperation current =
                            deployedOperation.getAssemblyOperation();

                    Object mapped = references.get(current);

                    if (mapped instanceof AssemblyOperation replacement
                            && containedAssemblyOperations.contains(replacement)) {

                        if (replacement != current) {
                            deployedOperation.setAssemblyOperation(replacement);
                            replacedFromMap++;
                        }

                        continue;
                    }

                    /*
                     * A valid-looking AssemblyOperation may still be stale.
                     * Membership in the rebuilt model is the decisive condition.
                     */
                    if (current != null
                            && !current.eIsProxy()
                            && containedAssemblyOperations.contains(current)) {

                        continue;
                    }

                    AssemblyOperation replacement =
                            assemblyComponent.getOperations()
                                    .get(operationEntry.getKey());

                    if (replacement != null
                            && containedAssemblyOperations.contains(replacement)) {

                        deployedOperation.setAssemblyOperation(replacement);
                        repairedStale++;
                        continue;
                    }
                    unresolved++;
                }
            }
        }
    }



    static String makeUniqueKey(
            EMap<String, ?> map,
            String originalKey,
            String oldComponentSignature) {
        if (!map.containsKey(originalKey)) {
            return originalKey;
        }

        String qualifiedKey =
                oldComponentSignature + "::" + originalKey;

        if (!map.containsKey(qualifiedKey)) {
            return qualifiedKey;
        }

        int suffix = 2;
        String candidate = qualifiedKey + "#" + suffix;

        while (map.containsKey(candidate)) {
            suffix++;
            candidate = qualifiedKey + "#" + suffix;
        }

        return candidate;
    }

    static String createDeployedSignature(
            String contextKey,
            String assemblySignature) {
        String safeContext =
                contextKey == null || contextKey.isBlank()
                        ? "unknown-context"
                        : contextKey;


        // avoid special characters
        safeContext = safeContext
                .replace("<", "")
                .replace(">", "")
                .replaceAll("[^a-zA-Z0-9_.-]", "_");

        String safeAssembly =
                assemblySignature == null
                        || assemblySignature.isBlank()
                        ? "__unknown__"
                        : assemblySignature;

        return safeContext + "." + safeAssembly;
    }

    private static EDirection mergeDirections(EDirection first, EDirection second) {
        if (first == null) return second;
        if (second == null || first == second) return first;
        return EDirection.BOTH;
    }

    Map<ComponentType, Map<ComponentType, Long>> calculateCallCounts() {

        Map<ComponentType, Map<ComponentType, Long>> callCounts = new HashMap<>();

        for (Map.Entry<Invocation, Integer> entry : statisticsMapping.entrySet()) {
            Invocation invocation = entry.getKey();
            long callCount = entry.getValue();

            ComponentType callerCT = invocation.getCaller()
                    .getAssemblyOperation()
                    .getComponent()
                    .getComponentType();

            ComponentType calleeCT = invocation.getCallee()
                    .getAssemblyOperation()
                    .getComponent()
                    .getComponentType();

            callCounts
                    .computeIfAbsent(callerCT, key -> new HashMap<>())
                    .merge(calleeCT, callCount, Long::sum);
        }

        return callCounts;
    }

    Map<ComponentPair, CommunicationScore> calculateCommunicationScores(
            Map<ComponentType, Map<ComponentType, Long>> callCounts) {

        Map<ComponentPair, CommunicationScore> result = new HashMap<>();

        for (var sourceEntry : callCounts.entrySet()) {

            ComponentType source = sourceEntry.getKey();

            for (var targetEntry : sourceEntry.getValue().entrySet()) {

                ComponentType target = targetEntry.getKey();

                if (source == target) {
                    continue;
                }

                ComponentPair pair = pairOf(source, target);

                // schon berechnet
                if (result.containsKey(pair)) {
                    continue;
                }

                long callsAB = callCounts
                        .getOrDefault(pair.first(), Map.of())
                        .getOrDefault(pair.second(), 0L);

                long callsBA = callCounts
                        .getOrDefault(pair.second(), Map.of())
                        .getOrDefault(pair.first(), 0L);

                long communication = callsAB + callsBA;

                long max = Math.max(callsAB, callsBA);
                long min = Math.min(callsAB, callsBA);

                double reciprocity =
                        max == 0
                                ? 0.0
                                : (double) min / max;

                double score =
                        communication * (1.0 + reciprocity);

                result.put(
                        pair,
                        new CommunicationScore(
                                pair.first(),
                                pair.second(),
                                callsAB,
                                callsBA,
                                communication,
                                reciprocity,
                                score
                        )
                );
            }
        }

        return result;
    }

    /**
     * Returns a new ComponentPair: if the signatures match, build (a,b) - otherwise (b,a)
     * @param a
     * @param b
     * @return ComponentPair
     */
    ComponentPair pairOf(ComponentType a, ComponentType b) {
        if (a.getSignature().compareTo(b.getSignature()) <= 0) {
            return new ComponentPair(a, b);
        }
        return new ComponentPair(b, a);
    }

    /**
     * Contains two ComponentTypes
     * @param first
     * @param second
     */
    public record ComponentPair(
            ComponentType first,
            ComponentType second
    ) {}

    /**
     * Record containing various data regarding communication of two Components, as for example:
     * calls in both directions, total communication, reciprocity and the resulting score.
     * @param componentA
     * @param componentB
     * @param callsAB
     * @param callsBA
     * @param communication
     * @param reciprocity
     * @param score
     */
    public record CommunicationScore(
            ComponentType componentA,
            ComponentType componentB,
            long callsAB,
            long callsBA,
            long communication,
            double reciprocity,
            double score
    ) {}

    static protected void printDisplayedComponentInvocationEdges() {
        Set<String> displayedEdges = new LinkedHashSet<>();

        for (Invocation invocation : executionModel.getInvocations().values()) {
            String source = invocation.getCaller() == null
                    ? "entry"
                    : componentSignature(invocation.getCaller());

            String target = componentSignature(invocation.getCallee());

            if (target == null) continue;

            displayedEdges.add(source + " -> " + target);
        }

//        System.out.println(ANSI_GREEN + "\n===== DISPLAYED COMPONENT EDGES =====");
//        displayedEdges.stream()
//                .sorted()
//                .forEach(System.out::println);

        System.out.println(ANSI_GREEN + "Displayed edges: " + displayedEdges.size() + ANSI_RESET);
    }

    static private String componentSignature(DeployedOperation operation) {
        if (operation == null
                || operation.getAssemblyOperation() == null
                || operation.getAssemblyOperation().getComponent() == null) {
            return null;
        }

        return operation.getAssemblyOperation()
                .getComponent()
                .getSignature();
    }

}
