package tools.abstraction;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.EMap;
import org.eclipse.emf.ecore.util.EcoreUtil;

import java.util.*;

import static tools.abstraction.ModelAbstraction.*;

public class Filtering extends AbstractionStrategy {

    protected void filterWithThreshold(int threshold) {
        resetFilteringState();
        ThresholdSelection selection = new ThresholdSelection();
        selection.selectUsingThreshold(threshold);
        selection.traceRelatedObjects();
        markRemovedObjects(ThresholdSelection.getModifyObjects());
        addThresholdRemovalInstructions();
    }

    protected void filterPercentage(Double thresholdPercentage) {
        resetFilteringState();
        ThresholdSelection selection = new ThresholdSelection();
        selection.computePercentageThreshold(thresholdPercentage);

        selection.categorizeComponentTypes();
        selection.traceRelatedObjects();
        markRemovedObjects(ThresholdSelection.getModifyObjects());

        addThresholdRemovalInstructions();
    }

    protected void filterQuantile(Double thresholdQuantile) {
        resetFilteringState();
        ThresholdSelection selection = new ThresholdSelection();
        selection.computeQuantileThreshold(thresholdQuantile);

        selection.categorizeComponentTypes();
        selection.traceRelatedObjects();
        markRemovedObjects(ThresholdSelection.getModifyObjects());

        addThresholdRemovalInstructions();
    }


    /**
     * Reset all involved structures to ensure no stale data
     */
    protected void resetFilteringState() {
        statisticsMapping.clear();
        componentCallCounts.clear();
        ThresholdSelection.getModifyObjects().clear();

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

        instructions = new InstructionSet();

        EcoreUtil.resolveAll(typeModel);
        EcoreUtil.resolveAll(assemblyModel);
        EcoreUtil.resolveAll(deploymentModel);
        EcoreUtil.resolveAll(executionModel);
        EcoreUtil.resolveAll(statisticsModel);
    }

    /**
     * Generic deletion instructions.
     * Objects are checked for deletion status from set "removedObjects"
     * EMaps are checked both by key and value,
     * ELists are checked by their contained object.
     */
    private void addThresholdRemovalInstructions() {
        instructions.addInstruction(
                EMap.class,
                null,
                new InstructionSet.Instruction(
                        List.of(
                                new InstructionSet.Condition(
                                        List.of(),
                                        "key",
                                        InstructionSet.ConditionOp.IS_CONTAINED_IN_SET,
                                        ThresholdSelection.getModifyObjects()
                                )
                        ),
                        List.of(
                                new InstructionSet.Action(
                                        List.of(),
                                        "wholeObject",
                                        InstructionSet.ActionOp.REMOVE_THIS_FROM_MAP,
                                        null,
                                        null
                                )
                        )
                )
        );

        instructions.addInstruction(
                EMap.class,
                null,
                new InstructionSet.Instruction(
                        List.of(
                                new InstructionSet.Condition(
                                        List.of(),
                                        "value",
                                        InstructionSet.ConditionOp.IS_CONTAINED_IN_SET,
                                        ThresholdSelection.getModifyObjects()
                                )
                        ),
                        List.of(
                                new InstructionSet.Action(
                                        List.of(),
                                        "wholeObject",
                                        InstructionSet.ActionOp.REMOVE_THIS_FROM_MAP,
                                        null,
                                        null
                                )
                        )
                )
        );

        instructions.addInstruction(
                EList.class,
                null,
                new InstructionSet.Instruction(
                        List.of(
                                new InstructionSet.Condition(
                                        List.of(),
                                        "wholeObject",
                                        InstructionSet.ConditionOp.IS_CONTAINED_IN_SET,
                                        ThresholdSelection.getModifyObjects()
                                )
                        ),
                        List.of(
                                new InstructionSet.Action(
                                        List.of(),
                                        "wholeObject",
                                        InstructionSet.ActionOp.REMOVE_THIS_FROM_LIST,
                                        null,
                                        null
                                )
                        )
                )
        );
    }
}
