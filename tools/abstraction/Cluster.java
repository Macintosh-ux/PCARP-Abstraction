package tools.abstraction;

import kieker.model.analysismodel.assembly.AssemblyComponent;
import kieker.model.analysismodel.deployment.DeployedComponent;
import kieker.model.analysismodel.deployment.DeployedOperation;
import kieker.model.analysismodel.execution.Invocation;
import kieker.model.analysismodel.statistics.StatisticRecord;
import kieker.model.analysismodel.type.ComponentType;
import nl.cwts.networkanalysis.Clustering;
import nl.cwts.networkanalysis.LeidenAlgorithm;
import nl.cwts.networkanalysis.Network;
import nl.cwts.util.LargeDoubleArray;
import nl.cwts.util.LargeIntArray;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;

import java.util.*;

import static tools.abstraction.ModelAbstraction.*;

/**
 * Groups ComponentTypes with the Leiden community-detection algorithm.
 * Positive invocation counts form an undirected weighted graph. Self-calls do
 * not influence clustering, and components without inter-component calls are
 * placed in the residual package.
 */
public class Cluster extends AbstractionStrategy {

    private static final String CLUSTER_PREFIX = "CLUSTER:";
    private static final String RESIDUAL_PACKAGE = "RESIDUALS";
    private static final String PATH_SEPARATOR = "⎯";
    private static final int ITERATIONS = 10;
    private static final long RANDOM_SEED = 0L;
    private static final double[] RESOLUTION_CANDIDATES = {0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 3.0};
    private static final double MAX_LARGEST_CLUSTER_SHARE = 0.5;
    private static final double MAX_SINGLETON_SHARE = 0.2;

    private final Map<ComponentType, String> originalNames = new IdentityHashMap<>();
    private final Map<ComponentType, String> originalPackages = new IdentityHashMap<>();
    private final Map<ComponentType, Integer> clusterAssignments = new IdentityHashMap<>();
    private final Set<ComponentType> communicatingComponents = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Selects a resolution automatically and uses a fixed seed for reproducible output. */
    public void cluster() {
        run(null);
    }

    /** Larger resolution values generally produce more, smaller communities. */
    public void cluster(double resolution) {
        run(resolution);
    }

    private void run(Double manualResolution) {
        double validationResolution = manualResolution == null ? 1.0 : manualResolution;
        validateInput(validationResolution);
        resetState();
        rememberOriginalNames();

        GraphData graph = buildGraph();
        if (graph.components().isEmpty()) {
            System.out.println(ANSI_YELLOW + "\nNo inter-component communication found. All components become residuals." + ANSI_RESET);
            applyClusterStructure();
            return;
        }

        Network network = createNetwork(graph);
        ResolutionResult result = manualResolution == null ? selectResolution(network) : evaluateResolution(network, manualResolution);
        Clustering clustering = result.clustering();

        for (int node = 0; node < graph.components().size(); node++) {
            ComponentType component = graph.components().get(node);
            communicatingComponents.add(component);
            clusterAssignments.put(component, clustering.getCluster(node));
        }

        printResult(graph, clustering, result);
        applyClusterStructure();
        System.out.println(ANSI_GREEN + "\nLeiden clustering completed." + ANSI_RESET);
    }

    private ResolutionResult selectResolution(Network network) {
        List<ResolutionResult> results = new ArrayList<>();
        for (double resolution : RESOLUTION_CANDIDATES) results.add(evaluateResolution(network, resolution));

        System.out.println(ANSI_PURPLE + "\n===== AUTOMATIC RESOLUTION SELECTION =====" + ANSI_RESET);
        System.out.printf("%-11s %-9s %-12s %-12s %-12s %-8s%n", "Resolution", "Clusters", "Modularity", "Largest", "Singletons", "Valid");
        for (ResolutionResult result : results) {
            String largest = String.format(Locale.US, "%.2f%%", result.largestClusterShare() * 100.0);
            String singletons = String.format(Locale.US, "%.2f%%", result.singletonShare() * 100.0);
            System.out.printf(Locale.US, "%-11.2f %-9d %-12.6f %-12s %-12s %-8s%n", result.resolution(), result.clusterCount(),
                    result.modularity(), largest, singletons, result.valid() ? "yes" : "no");
        }

        Comparator<ResolutionResult> ranking = Comparator.comparingDouble(ResolutionResult::selectionScore)
                .thenComparingDouble(result -> -Math.abs(result.resolution() - 1.0));
        ResolutionResult selected = results.stream().filter(ResolutionResult::valid).max(ranking)
                .orElseGet(() -> results.stream().max(ranking).orElseThrow());
        System.out.printf(Locale.US, ANSI_GREEN + "Selected resolution: %.2f%n" + ANSI_RESET, selected.resolution());
        if (!selected.valid()) System.out.println(ANSI_YELLOW + "No candidate met every readability bound; selected the least-penalized result." + ANSI_RESET);
        return selected;
    }

    private ResolutionResult evaluateResolution(Network network, double resolution) {
        double totalWeight = 2.0 * network.getTotalEdgeWeight() + network.getTotalEdgeWeightSelfLinks();
        double adjustedResolution = resolution / totalWeight;
        LeidenAlgorithm leiden = new LeidenAlgorithm(adjustedResolution, ITERATIONS, LeidenAlgorithm.DEFAULT_RANDOMNESS, new Random(RANDOM_SEED));
        Clustering clustering = new Clustering(network.getNNodes());
        leiden.improveClustering(network, clustering);
        clustering.orderClustersByNNodes();

        int[] sizes = clustering.getNNodesPerCluster();
        int largest = Arrays.stream(sizes).max().orElse(0);
        long singletons = Arrays.stream(sizes).filter(size -> size == 1).count();
        double largestShare = (double) largest / network.getNNodes();
        double singletonShare = (double) singletons / network.getNNodes();
        int maxClusters = Math.max(2, (int) Math.floor(Math.sqrt(network.getNNodes())));
        boolean valid = clustering.getNClusters() >= 2 && clustering.getNClusters() <= maxClusters
                && largestShare <= MAX_LARGEST_CLUSTER_SHARE && singletonShare <= MAX_SINGLETON_SHARE;

        LeidenAlgorithm modularityEvaluator = new LeidenAlgorithm(1.0 / totalWeight, 1, LeidenAlgorithm.DEFAULT_RANDOMNESS, new Random(RANDOM_SEED));
        double modularity = modularityEvaluator.calcQuality(network, clustering);
        double penalty = 2.0 * Math.max(0.0, largestShare - MAX_LARGEST_CLUSTER_SHARE)
                + Math.max(0.0, singletonShare - MAX_SINGLETON_SHARE)
                + Math.max(0, 2 - clustering.getNClusters()) * 0.5
                + Math.max(0, clustering.getNClusters() - maxClusters) / (double) network.getNNodes();
        return new ResolutionResult(resolution, clustering, modularity, largestShare, singletonShare, valid, modularity - penalty);
    }

    private GraphData buildGraph() {
        List<ComponentType> allComponents = uniqueComponents();
        Map<ComponentType, Integer> allIds = new IdentityHashMap<>();
        for (int i = 0; i < allComponents.size(); i++) allIds.put(allComponents.get(i), i);

        Map<Long, Long> rawWeights = new HashMap<>();
        for (Map.Entry<EObject, StatisticRecord> entry : statisticsModel.getStatistics()) {
            if (!(entry.getKey() instanceof Invocation invocation)) continue;
            long calls = extractCalls(entry.getValue());
            if (calls <= 0) continue;

            ComponentType caller = componentTypeOf(invocation.getCaller());
            ComponentType callee = componentTypeOf(invocation.getCallee());
            Integer callerId = allIds.get(caller);
            Integer calleeId = allIds.get(callee);
            if (callerId == null || calleeId == null || callerId.equals(calleeId)) continue;

            int low = Math.min(callerId, calleeId);
            int high = Math.max(callerId, calleeId);
            rawWeights.merge(pairKey(low, high), calls, Cluster::safeAdd);
        }

        Set<Integer> activeIds = new HashSet<>();
        for (long key : rawWeights.keySet()) {
            activeIds.add((int) (key >>> 32));
            activeIds.add((int) key);
        }

        List<Integer> orderedIds = new ArrayList<>(activeIds);
        Collections.sort(orderedIds);
        Map<Integer, Integer> compactIds = new HashMap<>();
        List<ComponentType> components = new ArrayList<>();
        for (int i = 0; i < orderedIds.size(); i++) {
            compactIds.put(orderedIds.get(i), i);
            components.add(allComponents.get(orderedIds.get(i)));
        }

        List<WeightedEdge> edges = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : rawWeights.entrySet()) {
            int source = compactIds.get((int) (entry.getKey() >>> 32));
            int target = compactIds.get((int) (long) entry.getKey());
            edges.add(new WeightedEdge(source, target, Math.log1p(entry.getValue()), entry.getValue()));
        }
        edges.sort(Comparator.comparingInt(WeightedEdge::source).thenComparingInt(WeightedEdge::target));

        System.out.println("\nComponent communication edges: " + edges.size());
        System.out.println("Components participating in communication: " + components.size());
        return new GraphData(components, edges);
    }

    private Network createNetwork(GraphData graph) {
        LargeIntArray[] endpoints = {new LargeIntArray(graph.edges().size()), new LargeIntArray(graph.edges().size())};
        LargeDoubleArray weights = new LargeDoubleArray(graph.edges().size());
        for (int i = 0; i < graph.edges().size(); i++) {
            WeightedEdge edge = graph.edges().get(i);
            endpoints[0].set(i, edge.source());
            endpoints[1].set(i, edge.target());
            weights.set(i, edge.weight());
        }
        return new Network(graph.components().size(), true, endpoints, weights, false, true);
    }

    private void applyClusterStructure() {
        int clustered = 0;
        int residual = 0;
        for (ComponentType component : uniqueComponents()) {
            Integer cluster = clusterAssignments.get(component);
            String targetPackage = cluster == null ? RESIDUAL_PACKAGE : CLUSTER_PREFIX + (cluster + 1);
            if (cluster == null) residual++; else clustered++;

            String oldName = originalNames.get(component);
            component.setName(preservedName(originalPackages.get(component), oldName));
            component.setPackage(targetPackage);
        }

        System.out.println("\n===== CLUSTER TRANSFORMATION =====");
        System.out.println("Clustered components: " + clustered);
        System.out.println("Residual components: " + residual);
    }

    private void printResult(GraphData graph, Clustering clustering, ResolutionResult result) {
        long internalCalls = 0;
        long externalCalls = 0;
        for (WeightedEdge edge : graph.edges()) {
            if (clustering.getCluster(edge.source()) == clustering.getCluster(edge.target())) internalCalls = safeAdd(internalCalls, edge.rawCalls());
            else externalCalls = safeAdd(externalCalls, edge.rawCalls());
        }
        long totalCalls = safeAdd(internalCalls, externalCalls);
        double internalShare = totalCalls == 0 ? 0.0 : 100.0 * internalCalls / totalCalls;

        System.out.println(ANSI_PURPLE + "\n===== LEIDEN CLUSTERING =====" + ANSI_RESET);
        System.out.println("Quality function: modularity");
        System.out.printf(Locale.US, "Resolution: %.4f%n", result.resolution());
        System.out.println("Seed: " + RANDOM_SEED);
        System.out.println("Nodes: " + graph.components().size());
        System.out.println("Edges: " + graph.edges().size());
        System.out.println("Clusters: " + clustering.getNClusters());
        System.out.printf(Locale.US, "Modularity quality: %.6f%n", result.modularity());
        System.out.printf(Locale.US, "Internal raw-call share: %.2f%%%n", internalShare);

        for (int cluster = 0; cluster < clustering.getNClusters(); cluster++) {
            System.out.println("\n" + CLUSTER_PREFIX + (cluster + 1));
            for (int node = 0; node < graph.components().size(); node++) {
                if (clustering.getCluster(node) == cluster) System.out.println("  " + qualifiedName(graph.components().get(node)));
            }
        }
    }

    private List<ComponentType> uniqueComponents() {
        Set<ComponentType> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<ComponentType> result = new ArrayList<>();
        for (ComponentType component : typeModel.getComponentTypes().values()) {
            if (component != null && seen.add(component)) result.add(component);
        }
        result.sort(Comparator.comparing(this::qualifiedName));
        return result;
    }

    private void rememberOriginalNames() {
        for (ComponentType component : uniqueComponents()) {
            originalNames.put(component, component.getName());
            originalPackages.put(component, component.getPackage());
        }
    }

    private ComponentType componentTypeOf(DeployedOperation operation) {
        if (operation == null) return null;
        DeployedComponent deployed = operation.getComponent();
        if (deployed == null) return null;
        AssemblyComponent assembly = deployed.getAssemblyComponent();
        return assembly == null ? null : assembly.getComponentType();
    }

    private long extractCalls(StatisticRecord record) {
        if (record == null) return 0;
        Object value = record.getProperties().get("calls");
        if (value == null) return 0;
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException exception) {
            System.out.println(ANSI_YELLOW + "Ignoring invalid call count: " + value + ANSI_RESET);
            return 0;
        }
    }

    private void resetState() {
        originalNames.clear();
        originalPackages.clear();
        clusterAssignments.clear();
        communicatingComponents.clear();
        visited.clear();
        active.clear();
        references.clear();
        keyMapping.clear();
        instructions = new InstructionSet();
        EcoreUtil.resolveAll(typeModel);
        EcoreUtil.resolveAll(assemblyModel);
        EcoreUtil.resolveAll(deploymentModel);
        EcoreUtil.resolveAll(executionModel);
        EcoreUtil.resolveAll(statisticsModel);
    }

    private void validateInput(double resolution) {
        if (!Double.isFinite(resolution) || resolution <= 0) throw new IllegalArgumentException("Leiden resolution must be finite and > 0.");
    }

    private String preservedName(String packageName, String name) {
        String path = packageName == null ? "" : packageName.replace(".", PATH_SEPARATOR);
        if (path.isBlank()) return name;
        if (name == null || name.isBlank()) return path;
        return path + PATH_SEPARATOR + name;
    }

    private String qualifiedName(ComponentType component) {
        String packageName = originalPackages.getOrDefault(component, component.getPackage());
        String name = originalNames.getOrDefault(component, component.getName());
        if (packageName == null || packageName.isBlank()) return name == null ? "" : name;
        if (name == null || name.isBlank()) return packageName;
        return packageName + "." + name;
    }

    private static long pairKey(int low, int high) {
        return ((long) low << 32) | (high & 0xffffffffL);
    }

    private static long safeAdd(long first, long second) {
        if (second > 0 && first > Long.MAX_VALUE - second) return Long.MAX_VALUE;
        return first + second;
    }

    private record WeightedEdge(int source, int target, double weight, long rawCalls) {}
    private record GraphData(List<ComponentType> components, List<WeightedEdge> edges) {}
    private record ResolutionResult(double resolution, Clustering clustering, double modularity,
                                    double largestClusterShare, double singletonShare, boolean valid,
                                    double selectionScore) {
        private int clusterCount() {
            return clustering.getNClusters();
        }
    }
}
