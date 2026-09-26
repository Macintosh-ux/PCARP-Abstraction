package tools.abstraction;

import kieker.model.analysismodel.assembly.AssemblyComponent;
import kieker.model.analysismodel.assembly.AssemblyOperation;
import kieker.model.analysismodel.assembly.AssemblyStorage;
import kieker.model.analysismodel.deployment.DeployedComponent;
import kieker.model.analysismodel.deployment.DeployedOperation;
import kieker.model.analysismodel.deployment.DeployedStorage;
import kieker.model.analysismodel.deployment.DeploymentContext;
import kieker.model.analysismodel.execution.Invocation;
import kieker.model.analysismodel.execution.OperationDataflow;
import kieker.model.analysismodel.execution.StorageDataflow;
import kieker.model.analysismodel.type.ComponentType;

import java.util.*;

import static tools.abstraction.ModelAbstraction.*;

public class ThresholdSelection extends AbstractionStrategy {

    private int threshold = 0;
    private final Map<String, ComponentType> keepComponentTypes = new LinkedHashMap<>();
    private final Set<ComponentType> modifyComponentTypesSet = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<String, ComponentType> modifyComponentTypes = new LinkedHashMap<>();
    private static final Set<Object> modifyObjects = Collections.newSetFromMap(new IdentityHashMap<>());

    public ThresholdSelection() {}

    /** Uses an externally computed threshold but selects from the current model. */
    protected void selectUsingThreshold(int fixedThreshold) {
        if (fixedThreshold < 0) throw new IllegalArgumentException("Threshold must be >= 0");
        threshold = fixedThreshold;
        computeStatisticsMapping();
        computeCallCounts();
        categorizeComponentTypes();
        System.out.println(ANSI_PURPLE + "\nFixed threshold: " + threshold + ANSI_RESET);
    }

    /**
     * Converts the configured percentage of all observed invocations into the
     * absolute threshold used by CCT.
     */
    public void computePercentageThreshold(double thresholdPercentage) {
        computeStatisticsMapping();
        computeCallCounts();

        int totalCalls =
                statisticsMapping.values()
                        .stream()
                        .mapToInt(Integer::intValue)
                        .sum();

        double factor = thresholdPercentage / 100.0;
        threshold = (int) (totalCalls * factor);
        System.out.println(ANSI_PURPLE + "Threshold: " + threshold + ANSI_RESET);
    }

    /**
     * Selects the requested percentage of ComponentTypes with the lowest
     * communication counts.
     *
     * Selection is based on the requested count rather than a later threshold
     * comparison. Therefore, 100 percent selects every ComponentType, including
     * the strongest communicator.
     */
    protected void computeQuantileThreshold(double quantile) {
        if (quantile < 0 || quantile > 100) {
            throw new IllegalArgumentException(
                    "Quantile must be between 0 and 100"
            );
        }

        computeStatisticsMapping();
        computeCallCounts();

        List<Map.Entry<ComponentType, Integer>> sorted =
                componentCallCounts.entrySet().stream()
                        .filter(entry -> entry.getKey() != null)
                        .sorted(
                                Map.Entry.<ComponentType, Integer>comparingByValue()
                                        .thenComparing(
                                                entry -> entry.getKey().getSignature(),
                                                Comparator.nullsFirst(String::compareTo)
                                        )
                        )
                        .toList();

        if (sorted.isEmpty()) {
            threshold = 0;
            return;
        }

        int selectedCount =
                (int) Math.ceil(
                        quantile / 100.0 * sorted.size()
                );

        selectedCount =
                Math.min(selectedCount, sorted.size());

        threshold = selectedCount == 0
                ? 0
                : sorted.get(selectedCount - 1).getValue();


        System.out.println(
                ANSI_PURPLE
                        + "\n===== THRESHOLD COMPUTATION ====="
                        + ANSI_RESET
                        + "\nQuantile Threshold: " + threshold
                        + "\nAbstraction percentage: " + quantile + "%"
                        + "\nComponent count: " + sorted.size()
                        + "\nSelected ComponentTypes: " + selectedCount
                        + ANSI_RESET
        );
    }

    protected void categorizeComponentTypes() {
        for (Map.Entry<String, ComponentType> componentTypeEntry : typeModel.getComponentTypes()) {
            ComponentType componentType = componentTypeEntry.getValue();
            String key = componentTypeEntry.getKey();

            if (componentType == null) {
                continue;
            }

            int componentCalls = componentCallCounts.getOrDefault(componentType, 0);

            if (componentCalls < threshold) {
                modifyComponentTypesSet.add(componentType);
                modifyComponentTypes.put(key, componentType);
                modifyObjects.add(componentType);
            } else {
                keepComponentTypes.put(key, componentType);
            }
        }
    }


    /**
     For each ComponentType that shall be filtered: propagate these changes to respective AssemblyOperations,
     -Components, -Storages, DeploymentContexts etc.
     Removal based on List.
     */
    protected void traceRelatedObjects() {
        Set<AssemblyComponent> modifyAssemblyComponents = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<AssemblyOperation> modifyAssemblyOperations = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<AssemblyStorage> modifyAssemblyStorages = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<DeployedComponent> modifyDeployedComponents = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<DeployedOperation> modifyDeployedOperations = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<DeployedStorage> modifyDeployedStorages = Collections.newSetFromMap(new IdentityHashMap<>());

        // 1. AssemblyComponents and their contained operations/storages.
        for (Map.Entry<String, AssemblyComponent> entry : assemblyModel.getComponents()) {

            AssemblyComponent component = entry.getValue();
            if (component == null) {
                continue;
            }

            if (modifyComponentTypesSet.contains(component.getComponentType())) {
                modifyAssemblyComponents.add(component);
                modifyObjects.add(component);

                for (AssemblyOperation operation
                        : component.getOperations().values()) {
                    if (operation != null) {
                        modifyAssemblyOperations.add(operation);
                        modifyObjects.add(operation);
                    }
                }

                for (AssemblyStorage storage
                        : component.getStorages().values()) {
                    if (storage != null) {
                        modifyAssemblyStorages.add(storage);
                        modifyObjects.add(storage);
                    }
                }
            } else {
                assemblyAggregationMapping.put(entry.getKey(), component);
            }
        }

        // 2. DeploymentComponents + survivor maps per context.
        for (DeploymentContext context
                : deploymentModel.getContexts().values()) {

            if (context == null) {
                continue;
            }

            Map<String, DeployedComponent> survivors = new LinkedHashMap<>();

            for (Map.Entry<String, DeployedComponent> entry
                    : context.getComponents()) {

                DeployedComponent component = entry.getValue();
                if (component == null) {
                    continue;
                }

                if (modifyAssemblyComponents.contains(
                        component.getAssemblyComponent())) {

                    modifyDeployedComponents.add(component);
                    modifyObjects.add(component);
                } else {
                    survivors.put(entry.getKey(), component);
                }
            }

            deploymentAggregationMappings.put(context, survivors);
        }

        // 3. Deployed operations/storages become obsolete either because
        //    their parent component is removed or because their assembly object is.
        for (DeploymentContext context
                : deploymentModel.getContexts().values()) {

            if (context == null) {
                continue;
            }

            for (DeployedComponent component
                    : context.getComponents().values()) {

                if (component == null) {
                    continue;
                }

                boolean parentRemoved =
                        modifyDeployedComponents.contains(component);

                for (DeployedOperation operation
                        : component.getOperations().values()) {

                    if (operation == null) {
                        continue;
                    }

                    AssemblyOperation assemblyOperation =
                            operation.getAssemblyOperation();

                    if (parentRemoved
                            || assemblyOperation == null
                            || modifyAssemblyOperations.contains(
                            assemblyOperation)) {

                        modifyDeployedOperations.add(operation);
                        modifyObjects.add(operation);
                    }
                }

                for (DeployedStorage storage
                        : component.getStorages().values()) {

                    if (storage == null) {
                        continue;
                    }

                    AssemblyStorage assemblyStorage =
                            storage.getAssemblyStorage();

                    if (parentRemoved
                            || assemblyStorage == null
                            || modifyAssemblyStorages.contains(
                            assemblyStorage)) {

                        modifyDeployedStorages.add(storage);
                        modifyObjects.add(storage);
                    }
                }
            }
        }

        // 4. Execution objects are removed when one of their referenced
// deployment objects is missing, invalid or scheduled for removal.
        int filteredInvocations = 0;
        for (Invocation invocation : executionModel.getInvocations().values()) {
            if (invocation == null) {
                filteredInvocations++;
                continue;
            }

            DeployedOperation caller = invocation.getCaller();
            DeployedOperation callee = invocation.getCallee();

            if (isRemovedOrInvalid(caller, modifyDeployedOperations)
                    || isRemovedOrInvalid(callee, modifyDeployedOperations)) {

                modifyObjects.add(invocation);
                filteredInvocations++;
            }
        }

        int filteredOperationDataflows = 0;
        for (OperationDataflow dataflow
                : executionModel.getOperationDataflows().values()) {

            if (dataflow == null) {
                filteredOperationDataflows++;
                continue;
            }

            if (isRemovedOrInvalid(dataflow.getCaller(), modifyDeployedOperations)
                    || isRemovedOrInvalid(dataflow.getCallee(), modifyDeployedOperations)) {

                modifyObjects.add(dataflow);
                filteredOperationDataflows++;
            }
        }

        int filteredStorageDataflows = 0;
        for (StorageDataflow dataflow
                : executionModel.getStorageDataflows().values()) {

            if (dataflow == null) {
                filteredStorageDataflows++;
                continue;
            }

            DeployedOperation operation = dataflow.getCode();
            DeployedStorage storage = dataflow.getStorage();

            boolean invalidOperation =
                    isRemovedOrInvalid(operation, modifyDeployedOperations);

            boolean invalidStorage =
                    storage == null
                            || storage.getAssemblyStorage() == null
                            || modifyDeployedStorages.contains(storage);

            if (invalidOperation || invalidStorage) {
                modifyObjects.add(dataflow);
                filteredStorageDataflows++;
            }
        }

//        for (var ct : modifyComponentTypesSet) {
//            System.out.println(ANSI_RED + ct.getSignature() + ": " + componentCallCounts.getOrDefault(ct, 0) + ANSI_RESET);
//        }

        System.out.println(ANSI_PURPLE +
                "\n===== THRESHOLD FILTER PLAN ====="
                + ANSI_RESET
                        //+ "\nThresholdPercentile: " + threshold
                        + "\nKept ComponentTypes: " + (typeModel.getComponentTypes().size() - modifyComponentTypesSet.size())
                        + "\nFiltered ComponentTypes: " + modifyComponentTypesSet.size()
                        + "\nFiltered AssemblyComponents: "
                        + modifyAssemblyComponents.size()
                        + "\nFiltered AssemblyOperations: "
                        + modifyAssemblyOperations.size()
                        + "\nFiltered AssemblyStorages: "
                        + modifyAssemblyStorages.size()
                        + "\nFiltered DeployedComponents: "
                        + modifyDeployedComponents.size()
                        + "\nFiltered DeployedOperations: "
                        + modifyDeployedOperations.size()
                        + "\nFiltered DeployedStorages: "
                        + modifyDeployedStorages.size()
                        + "\nFiltered Invocations: " + filteredInvocations
                        + "\nFiltered OperationDataflows: "
                        + filteredOperationDataflows
                        + "\nFiltered StorageDataflows: "
                        + filteredStorageDataflows
                        + "\nDeletion plan size: " + modifyObjects.size()
                        + ANSI_RESET
        );
    }

    /**
     * Calculates both kinds of communication information needed by threshold selection.
     *
     * Component counts belong to concrete ComponentTypes.
     * Package counts belong only to package paths.
     */
    protected static void computeCallCounts() {

        componentCallCounts.clear();
        packageCallCounts.clear();

        // Components without observed communication must still receive count 0.
        for (ComponentType componentType : typeModel.getComponentTypes().values()) {

            if (componentType != null) {
                componentCallCounts.put(
                        componentType,
                        0
                );

                addPackagePaths(
                        componentType.getPackage(),
                        0
                );
            }
        }

        for (Map.Entry<Invocation, Integer> entry
                : statisticsMapping.entrySet()) {

            Invocation invocation = entry.getKey();
            int callCount = entry.getValue();

            if (invocation == null) {
                continue;
            }

            ComponentType caller =
                    getComponentType(
                            invocation.getCaller()
                    );

            ComponentType callee =
                    getComponentType(
                            invocation.getCallee()
                    );

            /*
             * Identity semantics are intentional here.
             *
             * A self-call must contribute only once to the component's call
             * count, while communication between two different components
             * contributes to both participating components.
             */
            Set<ComponentType> involvedComponents =
                    Collections.newSetFromMap(
                            new IdentityHashMap<>()
                    );

            if (caller != null) {
                involvedComponents.add(caller);
            }

            if (callee != null) {
                involvedComponents.add(callee);
            }

            for (ComponentType componentType
                    : involvedComponents) {

                componentCallCounts.merge(
                        componentType,
                        callCount,
                        Integer::sum
                );
            }

            /*
             * Package communication follows the same principle.
             *
             * If caller and callee share a package path, that invocation is
             * counted only once for that package context.
             */
            Set<String> involvedPackages =
                    new HashSet<>();

            if (caller != null) {
                involvedPackages.addAll(
                        getPackagePaths(
                                caller.getPackage()
                        )
                );
            }

            if (callee != null) {
                involvedPackages.addAll(
                        getPackagePaths(
                                callee.getPackage()
                        )
                );
            }

            for (String packagePath : involvedPackages) {
                packageCallCounts.merge(
                        packagePath,
                        callCount,
                        Integer::sum
                );
            }
        }
    }

    /**
     * Resolves the ComponentType owning a deployed operation.
     *
     * Broken or incomplete reference chains return null and are ignored during
     * call-count calculation instead of failing the complete abstraction.
     */
    private static ComponentType getComponentType(
            DeployedOperation operation) {

        if (operation == null) {
            return null;
        }

        AssemblyOperation assemblyOperation =
                operation.getAssemblyOperation();

        if (assemblyOperation == null) {
            return null;
        }

        AssemblyComponent assemblyComponent =
                assemblyOperation.getComponent();

        if (assemblyComponent == null) {
            return null;
        }

        return assemblyComponent.getComponentType();
    }

    /**
     * Adds all hierarchical prefixes of a package to packageCallCounts.
     *
     * This is mainly used to ensure packages without observed communication
     * still exist with an explicit count of zero.
     */
    private static void addPackagePaths(
            String packageName,
            int callCount) {

        for (String path : getPackagePaths(packageName)) {
            packageCallCounts.merge(
                    path,
                    callCount,
                    Integer::sum
            );
        }
    }

    /**
     * Returns every package context represented by a package name.
     *
     * Example:
     * "uxsim.ResultGUIViewer.core"
     *
     * becomes:
     * "uxsim"
     * "uxsim.ResultGUIViewer"
     * "uxsim.ResultGUIViewer.core"
     */
    private static Set<String> getPackagePaths(
            String packageName) {

        Set<String> paths =
                new LinkedHashSet<>();

        if (packageName == null
                || packageName.isBlank()) {
            return paths;
        }

        String[] parts =
                packageName.split("\\.");

        StringBuilder current =
                new StringBuilder();

        for (String part : parts) {
            if (!current.isEmpty()) {
                current.append(".");
            }

            current.append(part);
            paths.add(current.toString());
        }

        return paths;
    }


    /**
     * Returns true when an operation cannot safely be resolved by downstream
     * consumers or is explicitly scheduled for removal.
     */
    private boolean isRemovedOrInvalid(
            DeployedOperation operation,
            Set<DeployedOperation> removedOperations) {

        return operation == null
                || operation.getAssemblyOperation() == null
                || operation.getAssemblyOperation().getComponent() == null
                || operation.getAssemblyOperation().getComponent()
                .getComponentType() == null
                || removedOperations.contains(operation);
    }




    public Set<ComponentType> getModifyComponentTypesSet() {
        return modifyComponentTypesSet;
    }


    public int getThreshold() {
        return threshold;
    }

    public static Set<Object> getModifyObjects() {
        return modifyObjects;
    }
}
