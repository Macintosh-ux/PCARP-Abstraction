package tools.abstraction;

import java.util.*;
import kieker.analysis.util.Tuple;

public class InstructionSet {

    private final Map<Tuple<Class<?>, Class<?>>, List<Instruction>> instructions = new HashMap<>();

    public record Instruction(
            List<Condition> conditions,
            List<Action> actions
    ) {}

    public record Condition(
            List<String> path,
            String targetFeature,
            ConditionOp operation,
            Object expectedValue
    ) {}

    public record Action(
            List<String> path,
            String targetFeature,
            ActionOp operation,
            Object value,
            Object optionalValue
    ) {}

    enum ConditionOp {
        ALWAYS,
        CONTAINS,
        CONTAINS_FEATURE_WITH_TYPE,
        STARTS_WITH,
        ENDS_WITH,
        IS_NULL,
        EQUALS,
        INSTANCEOF,
        IS_CONTAINED_IN_SET,
        IS_CONTAINEDKEY_IN_MAP,
        IS_CONTAINEDVALUE_IN_MAP,
        ISNOT_CONTAINEDVALUE_IN_MAP,
        IS_CONTAINED_IN_LIST
    }

    enum ActionOp {
        PRINT,
        SET,
        REPLACE_PART_OF_STRING,
        REPLACE_WITHIN_FROM_2_MAPS,
        REPLACE,
        REPLACE_FROM_MAP,
        REPLACE_FROM_KEYMAP,
        CREATE_AND_SET,
        REMOVE,
        ADD_TO_LIST,
        REMOVE_FROM_LIST,
        REMOVE_THIS_FROM_LIST,
        REMOVE_THIS_FROM_MAP,
        REPLACE_IN_LIST,
        REPLACE_LISTENTRY_FROM_MAP,
        RECREATE_ENTRY_WITH_KEY,
        RECREATE_ENTRY_WITH_PARTLY_REPLACED_KEY,
        PUT_TO_MAP,
        REMOVE_FROM_MAP,
        REPLACE_MAPENTRY_FROM_MAP
    }

    private boolean matches(
            Class<?> registeredClass,
            Class<?> actualClass
    ) {
        return registeredClass == null
                || registeredClass.isAssignableFrom(actualClass);
    }

    public void addInstruction(
            Class<?> targetClass,
            Class<?> optClass,
            Instruction instruction
    ) {
        instructions
                .computeIfAbsent(new Tuple<Class<?>, Class<?>>(targetClass, optClass), ignore -> new ArrayList<Instruction>())
                .add(instruction);
    }


    public Map<Tuple<Class<?>, Class<?>>, List<Instruction>> getMap() {
        if (instructions.isEmpty()) {
            return null;
        }
        return instructions;
    }

    public List<Instruction> getList() {
        List<Instruction> result = new ArrayList<>();
        for (List<Instruction> list : instructions.values()) {
            result.addAll(list);
        }
        return result;
    }

    public boolean checkTuple(Class<?> actualClass1) {
        return instructions.keySet().stream()
                .anyMatch(tuple ->
                        matches(tuple.getFirst(), actualClass1)
                                && tuple.getSecond() == null
                );
    }

    public boolean checkTuple(
            Class<?> actualClass1,
            Class<?> actualClass2
    ) {
        return instructions.keySet().stream()
                .anyMatch(tuple ->
                        matches(tuple.getFirst(), actualClass1)
                                && matches(tuple.getSecond(), actualClass2)
                );
    }

    public List<Instruction> getListForClassTuple(
            Class<?> actualClass1
    ) {
        List<Instruction> result = new ArrayList<>();

        for (Map.Entry<Tuple<Class<?>, Class<?>>,
                List<Instruction>> entry : instructions.entrySet()) {

            Class<?> registeredClass1 =
                    entry.getKey().getFirst();

            Class<?> registeredClass2 =
                    entry.getKey().getSecond();

            if (matches(registeredClass1, actualClass1)
                    && registeredClass2 == null) {

                result.addAll(entry.getValue());
            }
        }
        return result;
    }

    public List<Instruction> getListForClassTuple(
            Class<?> actualClass1,
            Class<?> actualClass2
    ) {
        List<Instruction> result = new ArrayList<>();

        for (Map.Entry<Tuple<Class<?>, Class<?>>,
                List<Instruction>> entry : instructions.entrySet()) {

            Class<?> registeredClass1 =
                    entry.getKey().getFirst();

            Class<?> registeredClass2 =
                    entry.getKey().getSecond();

            if (matches(registeredClass1, actualClass1)
                    && matches(registeredClass2, actualClass2)) {

                result.addAll(entry.getValue());
            }
        }

        return result;
    }
}