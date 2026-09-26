package tools.abstraction;

import kieker.model.analysismodel.assembly.*;
import kieker.model.analysismodel.deployment.*;
import kieker.model.analysismodel.execution.Invocation;
import kieker.model.analysismodel.type.ComponentType;
import kieker.model.analysismodel.type.OperationType;
import kieker.model.analysismodel.type.TypeFactory;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.util.EcoreUtil;

import java.util.*;

import static tools.abstraction.ModelAbstraction.*;

public class Aggregation extends AbstractionStrategy {

    public static final String AGGREGATED_MARKER = "::aggregated";
    public static final String BELOW_THRESHOLD_MARKER = "::below-threshold";
    private static final String AGGREGATED_OPERATION_KEY = "__aggregated_operation__";
    private static final String AGGREGATED_OPERATION_NAME = "AGGREGATED";

    private record PlannedTarget(String path, boolean belowThreshold) {}
    private record AggregationTarget(String key, String packageName, String name) {}

    /** Aggregation may merge relationships, but must not lose an invocation or its known call counts. */
    @Override protected void finalizeModelConsistency() { finalizeModelConsistency(true); }

    class PackageDepth {

        /** Aggregates packages to a fixed maximum depth. */
        protected void staticAggregation(int packageDepth) {
            if (packageDepth < 0) throw new IllegalArgumentException("Package depth must be >= 0");

            resetAggregationState();

            Map<ComponentType, PlannedTarget> targets = new IdentityHashMap<>();
            for (ComponentType type : typeModel.getComponentTypes().values()) {
                if (type == null) continue;
                String path = findPackageDepthTarget(type.getPackage(), packageDepth);
                if (path != null) targets.put(type, new PlannedTarget(path, false));
            }

            System.out.println(
                    ANSI_CYAN
                    + "\n===== PACKAGE DEPTH AGGREGATION PLAN ====="
                            + ANSI_RESET
                    + "\nOriginal ComponentType Count: " + typeModel.getComponentTypes().size()
                    + "\nNew ComponentType Count: " + targets.size()
            );

            applyComponentAggregationPlan(targets);
            finishAggregation();
        }

        /** Returns null if the component already satisfies the requested depth. */
        private String findPackageDepthTarget(String packageName, int packageDepth) {
            if (packageName == null || packageName.isBlank()) return null;

            String[] segments = packageName.split("\\.");
            if (packageDepth >= segments.length) return null;

            int targetLength = Math.min(packageDepth + 1, segments.length);
            return String.join(".", Arrays.copyOfRange(segments, 0, targetLength));
        }
    }

    class ThresholdAggregation {

        protected void dynamicAggregationWithThreshold(int threshold) {
            resetAggregationState();
            ThresholdSelection selection = new ThresholdSelection();
            selection.selectUsingThreshold(threshold);
//            printComponentCallCountDistribution();
            advancedAggregateComponentTypes(selection);
            finishAggregation();
        }

        protected void dynamicAggregationPercentage(double percentage) {
            resetAggregationState();
            ThresholdSelection selection = new ThresholdSelection();
            selection.computePercentageThreshold(percentage);
            selection.categorizeComponentTypes();
//            printComponentCallCountDistribution();
            advancedAggregateComponentTypes(selection);
            finishAggregation();
        }

        protected void dynamicAggregationQuantile(double quantile) {
            resetAggregationState();

            ThresholdSelection selection = new ThresholdSelection();
            selection.computeQuantileThreshold(quantile);
            selection.categorizeComponentTypes();

//            printComponentCallCountDistribution();
            advancedAggregateComponentTypes(selection);
            finishAggregation();
        }

        /**
         * Aggregates individually weak components bottom-up.
         *
         * A group stops at the nearest package where its real combined communication
         * reaches the threshold. Groups below the threshold move to their parent.
         * Only a root-level aggregate may remain below the threshold.
         */
        protected void advancedAggregateComponentTypes(ThresholdSelection selection) {
            int threshold = selection.getThreshold();

            Set<ComponentType> eligible = Collections.newSetFromMap(new IdentityHashMap<>());
            for (ComponentType type : selection.getModifyComponentTypesSet()) {
                if (type != null && componentCallCounts.getOrDefault(type, 0) < threshold) eligible.add(type);
            }

            Map<ComponentType, PlannedTarget> targets = planThresholdTargets(eligible, threshold);
            applyComponentAggregationPlan(targets);

            System.out.println(ANSI_CYAN +
                    "\n===== THRESHOLD AGGREGATION PLAN ====="
                    + ANSI_RESET
                            + "\nOriginal ComponentType Count: " + typeModel.getComponentTypes().size()
                            + "\nSelected candidates: " + selection.getModifyComponentTypesSet().size()
                            + "\nBelow threshold: " + eligible.size()
                            + "\nActually aggregated: " + targets.size()
                            + "\nResulting ComponentTypes: " + componentTypeRebuildMap.size()
            );

            printAggregationTargets(threshold);
        }

        /**
         * Processes active groups from the deepest package level towards the root.
         *
         * Moving every insufficient group before processing its parent ensures that
         * package counts contain exactly the components that will share the resulting
         * aggregate.
         */
        private Map<ComponentType, PlannedTarget> planThresholdTargets(
                Set<ComponentType> eligible,
                int threshold) {

            Map<ComponentType, String> activePaths = new IdentityHashMap<>();
            Map<ComponentType, PlannedTarget> targets = new IdentityHashMap<>();

            for (ComponentType type : eligible) {
                String packageName = type.getPackage();
                if (packageName != null && !packageName.isBlank()) activePaths.put(type, packageName);
            }

            while (!activePaths.isEmpty()) {
                int deepestLevel = activePaths.values().stream()
                        .mapToInt(this::packageDepth)
                        .max()
                        .orElse(0);

                Map<String, Set<ComponentType>> groups = new LinkedHashMap<>();

                for (Map.Entry<ComponentType, String> entry : activePaths.entrySet()) {
                    if (packageDepth(entry.getValue()) != deepestLevel) continue;

                    groups.computeIfAbsent(
                            entry.getValue(),
                            ignored -> Collections.newSetFromMap(new IdentityHashMap<>())
                    ).add(entry.getKey());
                }

                for (Map.Entry<String, Set<ComponentType>> group : groups.entrySet()) {
                    String path = group.getKey();
                    Set<ComponentType> members = group.getValue();
                    int groupCalls = computeGroupCallCount(members);

                    if (groupCalls >= threshold || isRootPackage(path)) {
                        boolean belowThreshold = groupCalls < threshold;

                        for (ComponentType type : members) {
                            targets.put(type, new PlannedTarget(path, belowThreshold));
                            activePaths.remove(type);
                        }

                        continue;
                    }

                    String parent = getParentPackage(path);

                    if (parent == null || parent.isBlank() || parent.equals(path)) {
                        for (ComponentType type : members) {
                            targets.put(type, new PlannedTarget(path, true));
                            activePaths.remove(type);
                        }

                        continue;
                    }

                    for (ComponentType type : members) activePaths.put(type, parent);
                }
            }

            return targets;
        }

        /**
         * Computes the actual communication represented by one planned aggregate.
         *
         * An invocation contributes once when at least one endpoint belongs to the
         * group. Internal communication is therefore not counted twice.
         */
        private int computeGroupCallCount(Set<ComponentType> members) {
            int calls = 0;

            for (Map.Entry<Invocation, Integer> entry : statisticsMapping.entrySet()) {
                Invocation invocation = entry.getKey();
                if (invocation == null) continue;

                ComponentType caller = getComponentType(invocation.getCaller());
                ComponentType callee = getComponentType(invocation.getCallee());

                if (members.contains(caller) || members.contains(callee)) {
                    calls = safeAdd(calls, entry.getValue());
                }
            }

            return calls;
        }

        /** Returns the number of segments in a package path. */
        private int packageDepth(String packageName) {
            if (packageName == null || packageName.isBlank()) return 0;
            return packageName.split("\\.").length;
        }

        /** A root package contains exactly one package segment. */
        private boolean isRootPackage(String packageName) {
            return packageDepth(packageName) == 1;
        }

        /** Prevents integer overflow when large call counts are combined. */
        private int safeAdd(int left, int right) {
            long result = (long) left + right;
            return result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
        }

        private void printComponentCallCountDistribution() {
            System.out.println("\n===== COMPONENT CALLCOUNT DISTRIBUTION =====");

            typeModel.getComponentTypes().values().stream()
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(ComponentType::getSignature))
                    .forEach(type -> System.out.println(
                            type.getSignature() + " -> " + componentCallCounts.getOrDefault(type, 0)
                    ));
        }

        private void printAggregationTargets(int threshold) {
            Map<ComponentType, Integer> counts = computeAggregatedComponentCallCounts();

//            System.out.println("\n===== ADVANCED AGGREGATION =====\nThreshold: " + threshold);

            typeModel.getComponentTypes().values().stream()
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(ComponentType::getSignature))
                    .forEach(oldType -> {
                        ComponentType target = replacementOf(oldType);

                        if (target == null || Objects.equals(oldType.getSignature(), target.getSignature())) return;

                        String status = target.getSignature().contains(BELOW_THRESHOLD_MARKER)
                                ? " [MAX DEPTH, BELOW THRESHOLD]"
                                : "";

//                        System.out.println(
//                                oldType.getSignature()
//                                        + " (" + componentCallCounts.getOrDefault(oldType, 0) + ") -> "
//                                        + target.getSignature()
//                                        + " (" + counts.getOrDefault(target, 0) + ")"
//                                        + status
//                        );
                    });
        }

        protected static Map<ComponentType, Integer> computeAggregatedComponentCallCounts() {
            Map<ComponentType, Integer> result = new IdentityHashMap<>();
            for (ComponentType type : componentTypeRebuildMap.values()) {
                if (type != null) result.put(type, 0);
            }

            for (Map.Entry<Invocation, Integer> entry : statisticsMapping.entrySet()) {
                Invocation invocation = entry.getKey();
                if (invocation == null) continue;

                ComponentType caller = replacementOf(getComponentType(invocation.getCaller()));
                ComponentType callee = replacementOf(getComponentType(invocation.getCallee()));
                Set<ComponentType> involved = Collections.newSetFromMap(new IdentityHashMap<>());

                if (caller != null) involved.add(caller);
                if (callee != null) involved.add(callee);

                for (ComponentType type : involved) result.merge(type, entry.getValue(), Integer::sum);
            }

            return result;
        }

        private static ComponentType getComponentType(DeployedOperation operation) {
            if (operation == null || operation.getAssemblyOperation() == null
                    || operation.getAssemblyOperation().getComponent() == null) return null;

            return operation.getAssemblyOperation().getComponent().getComponentType();
        }
    }

    /** Applies a complete plan; absent ComponentTypes remain unchanged. */
    private void applyComponentAggregationPlan(Map<ComponentType, PlannedTarget> targets) {
        Set<ComponentType> processed = Collections.newSetFromMap(new IdentityHashMap<>());

        for (Map.Entry<String, ComponentType> entry : typeModel.getComponentTypes()) {
            ComponentType oldType = entry.getValue();
            if (oldType == null || !processed.add(oldType)) continue;

            registerComponentReplacement(entry.getKey(), oldType, targets.get(oldType));
        }
    }

    /** Registers an unchanged copy or a shared synthetic aggregate. */
    private void registerComponentReplacement(
            String oldKey,
            ComponentType oldType,
            PlannedTarget plannedTarget) {

        if (plannedTarget == null) {
            copyUnchangedComponent(oldKey, oldType);
            return;
        }

        AggregationTarget target = createAggregationTarget(plannedTarget);
        ComponentType targetType = componentTypeRebuildMap.computeIfAbsent(
                target.key(), key -> createAggregatedComponentType(target)
        );

        keyMapping.put(oldKey, target.key());
        references.put(oldType, targetType);
        moveComponentTypeReferences(oldType, targetType);
    }

    /**
     * Creates the canonical synthetic component for an aggregation context.
     *
     * A depth-one target receives an explicit AGGREGATED component:
     * uxsim -> uxsim.AGGREGATED::aggregated
     *
     * If maximal aggregation still remains below the threshold:
     * uxsim.AGGREGATED::aggregated::below-threshold
     */
    private AggregationTarget createAggregationTarget(PlannedTarget planned) {
        String path = planned.path();

        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(
                    "Aggregation target path must not be empty"
            );
        }

        boolean depthOne = !path.contains(".");
        String packageName = depthOne ? path : getParentPackage(path);
        String componentName = depthOne ? "AGGREGATED" : getLastSegment(path);

        String qualifiedName = packageName + "." + componentName;
        String key = qualifiedName + AGGREGATED_MARKER;

        if (planned.belowThreshold()) {
            key += BELOW_THRESHOLD_MARKER;
        }

        return new AggregationTarget(
                key,
                packageName,
                componentName
        );
    }

    private ComponentType createAggregatedComponentType(AggregationTarget target) {
        ComponentType created = TypeFactory.eINSTANCE.createComponentType();
        created.setPackage(target.packageName());
        created.setName(target.name());
        created.setSignature(target.key());
        return created;
    }

    protected void resetAggregationState() {
        componentTypeRebuildMap.clear();
        assemblyAggregationMapping.clear();
        deploymentAggregationMappings.clear();
        operationTypeTargetKeys.clear();
        references.clear();
        removedObjects.clear();
        keyMapping.clear();
        visited.clear();
        active.clear();
        visitedFeatures.clear();
        statisticsMapping.clear();
        packageCallCounts.clear();
        componentCallCounts.clear();
        ThresholdSelection.getModifyObjects().clear();
        instructions = new InstructionSet();

        EcoreUtil.resolveAll(typeModel);
        EcoreUtil.resolveAll(assemblyModel);
        EcoreUtil.resolveAll(deploymentModel);
        EcoreUtil.resolveAll(executionModel);
        EcoreUtil.resolveAll(statisticsModel);
    }

    private static String getParentPackage(String value) {
        if (value == null || value.isBlank()) return null;
        int separator = value.lastIndexOf('.');
        return separator < 0 ? value : value.substring(0, separator);
    }

    private static String getLastSegment(String value) {
        if (value == null || value.isBlank()) return null;
        int separator = value.lastIndexOf('.');
        return separator < 0 ? value : value.substring(separator + 1);
    }

    @SuppressWarnings("unchecked")
    private static <T> T replacementOf(T object) {
        if (object == null) return null;
        Object replacement = references.get(object);
        return replacement == null ? object : (T) replacement;
    }

    private void addAggregationInstructions() {
        addReferenceListReplacementInstruction(ComponentType.class);
        addReferenceListReplacementInstruction(AssemblyComponent.class);
        addReferenceListReplacementInstruction(DeployedComponent.class);
    }

    private void addReferenceListReplacementInstruction(Class<?> referencedType) {
        instructions.addInstruction(
                EList.class,
                referencedType,
                new InstructionSet.Instruction(
                        List.of(new InstructionSet.Condition(
                                List.of(),
                                "wholeObject",
                                InstructionSet.ConditionOp.IS_CONTAINEDKEY_IN_MAP,
                                references
                        )),
                        List.of(new InstructionSet.Action(
                                List.of(),
                                "wholeObject",
                                InstructionSet.ActionOp.REPLACE_LISTENTRY_FROM_MAP,
                                references,
                                null
                        ))
                )
        );
    }

    /** Execution entries are rebuilt once, after transformation and endpoint repair. */
    private void finishAggregation() {
        aggregateAssemblyComponents();
        aggregateDeployedComponents();
        addAggregationInstructions();
    }

    protected void aggregateAssemblyComponents() {
        Map<AssemblyComponent, AssemblyComponent> componentReferences = new IdentityHashMap<>();
        Map<AssemblyOperation, AssemblyOperation> operationReferences = new IdentityHashMap<>();
        List<Map.Entry<String, AssemblyComponent>> oldComponents =
                new ArrayList<>(assemblyModel.getComponents());

        Map<ComponentType, AssemblyComponent> targetComponents = new IdentityHashMap<>();

        for (Map.Entry<String, AssemblyComponent> componentEntry : oldComponents) {
            AssemblyComponent oldComponent = componentEntry.getValue();
            if (oldComponent == null) continue;

            ComponentType newType = replacementOf(oldComponent.getComponentType());
            if (newType == null) {
                throw new IllegalStateException(
                        "AssemblyComponent has no target ComponentType: " + oldComponent.getSignature()
                );
            }

            AssemblyComponent newComponent = assemblyAggregationMapping.computeIfAbsent(
                    newType.getSignature(),
                    signature -> {
                        AssemblyComponent created = AssemblyFactory.eINSTANCE.createAssemblyComponent();
                        created.setSignature(signature);
                        created.setComponentType(newType);
                        return created;
                    }
            );

            componentReferences.put(oldComponent, newComponent);
            targetComponents.put(newType, newComponent);
            transferAssemblyStorages(oldComponent, newComponent);
        }

        Map<ComponentType, OperationType> aggregateOperationTypes =
                prepareAggregatedOperationTypes();

        Map<AssemblyComponent, AssemblyOperation> aggregateOperations = new IdentityHashMap<>();
        for (Map.Entry<String, AssemblyComponent> componentEntry : oldComponents) {
            AssemblyComponent oldComponent = componentEntry.getValue();
            if (oldComponent == null) continue;

            ComponentType newType = replacementOf(oldComponent.getComponentType());
            AssemblyComponent newComponent = targetComponents.get(newType);
            OperationType aggregateType = aggregateOperationTypes.get(newType);

            if (aggregateType != null) {
                transferAggregatedAssemblyOperations(
                        oldComponent,
                        newComponent,
                        aggregateType,
                        aggregateOperations,
                        operationReferences
                );
            } else {
                transferAssemblyOperations(oldComponent, newComponent, operationReferences);
            }
        }

        references.putAll(componentReferences);
        references.putAll(operationReferences);
    }

    /**
     * Creates one synthetic operation type for every aggregated component type.
     * All original operation types are mapped to that type so statistics and
     * source entries follow the same replacement chain as execution objects.
     */
    private Map<ComponentType, OperationType> prepareAggregatedOperationTypes() {

        Map<ComponentType, OperationType> result = new IdentityHashMap<>();
        Set<ComponentType> aggregateTypes = Collections.newSetFromMap(new IdentityHashMap<>());

        for (Map.Entry<String, ComponentType> entry : componentTypeRebuildMap.entrySet()) {
            ComponentType type = entry.getValue();
            if (type != null && isAggregatedType(type)) aggregateTypes.add(type);
        }

        for (ComponentType oldType : new ArrayList<>(typeModel.getComponentTypes().values())) {
            if (oldType == null) continue;

            ComponentType newType = replacementOf(oldType);
            if (!aggregateTypes.contains(newType)) continue;

            List<OperationType> oldOperations =
                    new ArrayList<>(oldType.getProvidedOperations().values());
            if (oldOperations.isEmpty()) continue;

            OperationType aggregateType = result.computeIfAbsent(
                    newType,
                    this::createAggregatedOperationType
            );

            for (OperationType oldOperation : oldOperations) {
                references.put(oldOperation, aggregateType);
                operationTypeTargetKeys.put(oldOperation, AGGREGATED_OPERATION_KEY);
            }
        }

        for (ComponentType aggregateType : aggregateTypes) {
            aggregateType.getProvidedOperations().clear();
            OperationType operationType = result.get(aggregateType);
            if (operationType != null) {
                aggregateType.getProvidedOperations().put(
                        AGGREGATED_OPERATION_KEY,
                        operationType
                );
            }
        }

        return result;
    }

    private OperationType createAggregatedOperationType(ComponentType componentType) {
        OperationType operationType = TypeFactory.eINSTANCE.createOperationType();
        operationType.setName(AGGREGATED_OPERATION_NAME);
        operationType.setSignature(
                componentType.getSignature() + "::" + AGGREGATED_OPERATION_NAME
        );
        operationType.setReturnType("void");
        return operationType;
    }

    private boolean isAggregatedType(ComponentType type) {
        return type != null && type.getSignature() != null
                && type.getSignature().contains(AGGREGATED_MARKER);
    }

    /** Maps every original operation in one aggregate to one assembly operation. */
    private void transferAggregatedAssemblyOperations(
            AssemblyComponent oldComponent,
            AssemblyComponent newComponent,
            OperationType aggregateType,
            Map<AssemblyComponent, AssemblyOperation> aggregateOperations,
            Map<AssemblyOperation, AssemblyOperation> operationReferences) {

        for (AssemblyOperation oldOperation
                : new ArrayList<>(oldComponent.getOperations().values())) {
            if (oldOperation == null) continue;

            AssemblyOperation aggregateOperation = aggregateOperations.computeIfAbsent(
                    newComponent,
                    component -> {
                        AssemblyOperation created =
                                AssemblyFactory.eINSTANCE.createAssemblyOperation();
                        created.setOperationType(aggregateType);
                        component.getOperations().put(
                                AGGREGATED_OPERATION_KEY,
                                created
                        );
                        return created;
                    }
            );

            OperationType oldType = oldOperation.getOperationType();
            if (oldType != null) references.put(oldType, aggregateType);
            operationReferences.put(oldOperation, aggregateOperation);
        }
    }

    private void transferAssemblyOperations(
            AssemblyComponent oldComponent,
            AssemblyComponent newComponent,
            Map<AssemblyOperation, AssemblyOperation> operationReferences) {

        for (Map.Entry<String, AssemblyOperation> entry
                : new ArrayList<>(oldComponent.getOperations())) {

            AssemblyOperation operation = entry.getValue();
            if (operation == null) continue;

            OperationType type = operation.getOperationType();
            if (type == null) {
                throw new IllegalStateException(
                        "AssemblyOperation has no OperationType"
                                + "\nComponent: " + oldComponent.getSignature()
                                + "\nOperation key: " + entry.getKey()
                );
            }

            String targetKey = operationTypeTargetKeys.get(type);
            if (targetKey == null) {
                throw new IllegalStateException(
                        "No target key for OperationType"
                                + "\nComponent: " + oldComponent.getSignature()
                                + "\nOperation key: " + entry.getKey()
                                + "\nOperationType: " + type.getSignature()
                                + "\nIdentity: " + System.identityHashCode(type)
                );
            }

            AssemblyOperation existing = newComponent.getOperations().get(targetKey);
            if (existing != null) {
                operationReferences.put(operation, existing);
                continue;
            }

            newComponent.getOperations().put(targetKey, operation);
            operationReferences.put(operation, operation);
        }
    }

    private void transferAssemblyStorages(
            AssemblyComponent oldComponent,
            AssemblyComponent newComponent) {

        for (Map.Entry<String, AssemblyStorage> entry
                : new ArrayList<>(oldComponent.getStorages())) {

            AssemblyStorage storage = entry.getValue();
            if (storage == null) continue;

            String targetKey = makeUniqueKey(
                    newComponent.getStorages(),
                    entry.getKey(),
                    oldComponent.getSignature()
            );

            newComponent.getStorages().put(targetKey, storage);
        }
    }

    protected void aggregateDeployedComponents() {
        Map<DeployedComponent, DeployedComponent> deployedReferences = new IdentityHashMap<>();
        Map<DeployedComponent, Map<AssemblyOperation, DeployedOperation>> operationTargets = new IdentityHashMap<>();

        for (Map.Entry<String, DeploymentContext> contextEntry
                : deploymentModel.getContexts()) {

            String contextKey = contextEntry.getKey();
            DeploymentContext context = contextEntry.getValue();
            if (context == null) continue;

            Map<String, DeployedComponent> contextMapping = new LinkedHashMap<>();
            deploymentAggregationMappings.put(context, contextMapping);

            for (Map.Entry<String, DeployedComponent> componentEntry
                    : new ArrayList<>(context.getComponents())) {

                DeployedComponent oldComponent = componentEntry.getValue();
                if (oldComponent == null) continue;

                AssemblyComponent newAssembly = replacementOf(oldComponent.getAssemblyComponent());
                if (newAssembly == null) {
                    throw new IllegalStateException(
                            "DeployedComponent has no AssemblyComponent"
                                    + "\nContext: " + contextKey
                                    + "\nComponent key: " + componentEntry.getKey()
                    );
                }

                String signature = createDeployedSignature(contextKey, newAssembly.getSignature());
                DeployedComponent newComponent = contextMapping.computeIfAbsent(
                        signature,
                        key -> {
                            DeployedComponent created = DeploymentFactory.eINSTANCE.createDeployedComponent();
                            created.setSignature(key);
                            created.setAssemblyComponent(newAssembly);
                            return created;
                        }
                );

                deployedReferences.put(oldComponent, newComponent);
                transferDeployedOperations(oldComponent, newComponent,
                        operationTargets.computeIfAbsent(newComponent, key -> new IdentityHashMap<>()));
                transferDeployedStorages(oldComponent, newComponent);
                transferContainedComponents(oldComponent, newComponent);
                transferProvidedInterfaces(oldComponent, newComponent);
                transferRequiredInterfaces(oldComponent, newComponent);
            }
        }

        references.putAll(deployedReferences);
    }

    /** Shares only identical final assembly operations within one aggregated deployment component. */
    private void transferDeployedOperations(DeployedComponent oldComponent, DeployedComponent newComponent,
                                            Map<AssemblyOperation, DeployedOperation> targets) {
        for (Map.Entry<String, DeployedOperation> entry : new ArrayList<>(oldComponent.getOperations())) {
            DeployedOperation operation = entry.getValue();
            if (operation == null) continue;

            AssemblyOperation assembly = resolveReplacement(operation.getAssemblyOperation(), AssemblyOperation.class);
            if (assembly == null || assembly.getComponent() != newComponent.getAssemblyComponent()) {
                throw new IllegalStateException("DeployedOperation has no valid target AssemblyOperation: " + entry.getKey());
            }

            DeployedOperation target = targets.get(assembly);
            if (target == null) {
                String key = makeUniqueKey(newComponent.getOperations(), entry.getKey(), oldComponent.getSignature());
                operation.setAssemblyOperation(assembly);
                newComponent.getOperations().put(key, operation);
                targets.put(assembly, operation);
                target = operation;
            }
            // Distinct methods remain distinct, even if they have identical names or signatures.
            references.put(operation, target);
        }
    }

    private void transferDeployedStorages(
            DeployedComponent oldComponent,
            DeployedComponent newComponent) {

        for (Map.Entry<String, DeployedStorage> entry
                : new ArrayList<>(oldComponent.getStorages())) {

            DeployedStorage storage = entry.getValue();
            if (storage == null) continue;

            String targetKey = makeUniqueKey(
                    newComponent.getStorages(),
                    entry.getKey(),
                    oldComponent.getSignature()
            );

            newComponent.getStorages().put(targetKey, storage);
        }
    }

    private void transferContainedComponents(
            DeployedComponent oldComponent,
            DeployedComponent newComponent) {

        for (DeployedComponent component
                : new ArrayList<>(oldComponent.getContainedComponents())) {

            if (component != null && !newComponent.getContainedComponents().contains(component)) {
                newComponent.getContainedComponents().add(component);
            }
        }
    }

    private void transferProvidedInterfaces(
            DeployedComponent oldComponent,
            DeployedComponent newComponent) {

        for (Map.Entry<String, DeployedProvidedInterface> entry
                : new ArrayList<>(oldComponent.getProvidedInterfaces())) {

            DeployedProvidedInterface providedInterface = entry.getValue();
            if (providedInterface == null) continue;

            String targetKey = makeUniqueKey(
                    newComponent.getProvidedInterfaces(),
                    entry.getKey(),
                    oldComponent.getSignature()
            );

            newComponent.getProvidedInterfaces().put(targetKey, providedInterface);
        }
    }

    private void transferRequiredInterfaces(
            DeployedComponent oldComponent,
            DeployedComponent newComponent) {

        for (DeployedRequiredInterface requiredInterface
                : new ArrayList<>(oldComponent.getRequiredInterfaces())) {

            if (requiredInterface != null
                    && !newComponent.getRequiredInterfaces().contains(requiredInterface)) {
                newComponent.getRequiredInterfaces().add(requiredInterface);
            }
        }
    }
}
