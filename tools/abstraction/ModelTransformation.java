package tools.abstraction;


import kieker.analysis.util.Tuple;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.EMap;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.jgrapht.alg.util.Triple;

import java.util.*;

import tools.abstraction.InstructionSet.*;
import static tools.abstraction.AbstractionStrategy.*;
import static tools.abstraction.ModelAbstraction.*;

@SuppressWarnings("unchecked")
public class ModelTransformation {

    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_RESET = "\u001B[0m";

    /**
     * For safe and controlled manipulation of Map entries.
     * Static because global structure originating here.
     */
    private static final Map<EMap<Object, Object>, List<Triple<Object, Object, Object>>> MapChangeList = new IdentityHashMap<>();
    /**
     * For safe and controlled removal of Map entries.
     * Static because global structure originating here.
     */
    private static final Map<EMap<Object, Object>, Set<Object>> MapRemovalList = new IdentityHashMap<>();
    /**
     * For safe and controlled manipulation/removal of List entries.
     * Static because global structure originating here.
     */
    private static final Map<Object, Object> ListChangeList = new IdentityHashMap<>();
    private static final Map<Object, Object> changeReference = new IdentityHashMap<>();

    private final InstructionSet instructions;
    private final Map<Object, Object> keyMapping;

    public ModelTransformation(AbstractionStrategy plan) {
        this.instructions = plan.instructions;
        this.keyMapping = plan.keyMapping;
    }

    private static void addMapChange(
            EMap<Object, Object> map,
            Object newKey,
            Object value,
            Object oldKey
    ) {
        MapChangeList
                .computeIfAbsent(map, ignored -> new ArrayList<>())
                .add(new Triple<>(newKey, value, oldKey));
    }

    private static void addMapRemoval(
            EMap<Object, Object> map,
            Object oldKey
    ) {
        MapRemovalList
                .computeIfAbsent(map, ignored -> new LinkedHashSet<>())
                .add(oldKey);
    }

    /**
     * Entrypoint for propagation mechanism. Traverses every Models contents completely.
     */
    protected void transformModels() {
        traverseMap(assemblyModel.getComponents());
        traverseMap(typeModel.getComponentTypes());
        traverseMap(deploymentModel.getContexts());
        traverseMap(sourceModel.getSources());
        traverseMap(statisticsModel.getStatistics());
        refreshExecutionMaps();
        traverseMap(executionModel.getInvocations());
        traverseMap(executionModel.getStorageDataflows());
        traverseMap(executionModel.getOperationDataflows());
    }

    /**
     * Traverses map. If instructions for this map and entry class are available, instructionmechanism is started.
     *
     * @param map
     * @param <K>
     * @param <V>
     */
    protected <K extends Object, V extends Object> void traverseMap(EMap<K, V> map) {

        if (map.isEmpty()) {
            return;
        }

        if (active.add(map)) {

            Object firstEntryObject = map.get(0);
            Map.Entry<K, V> firstEntry = map.get(0);
            Object firstKey = firstEntry.getKey();

            Class<?> entryClass = firstEntry == null ? null : firstEntryObject.getClass();
            Class<?> thisClass = map.getClass();

            boolean status = instructions.checkTuple(thisClass, entryClass);

            if (visited.add(map)) {
                for (Map.Entry<K, V> entry : map) {
                    if (status) {
                        try {
                            EObject entryObject = (EObject) entry;
                            executeInstructions(entryObject, instructions.getListForClassTuple(thisClass, entryClass), map);
                        } catch (Exception e) {
                            System.out.println(ANSI_RED + "Exception (traverseMap): " + ANSI_RESET);
                            e.printStackTrace(System.out);

                        }
                    }

                    K oldKey = entry.getKey();
                    K newKey = (K) keyMapping.get(oldKey);

                    if (references.containsKey(entry)) {
                        addMapChange((EMap<Object, Object>) map, newKey, (V) references.get(entry), oldKey);
                        visited.add(entry);
                    } else {
                        transformationMapping(entry);
                    }
                }
            }
        }
        active.remove(map);

        Map<EMap<Object, Object>, Set<Object>> removalDuplicateList =
                new IdentityHashMap<>(MapRemovalList);

        for (Map.Entry<EMap<Object, Object>, Set<Object>> removalEntry
                : removalDuplicateList.entrySet()) {

            EMap<Object, Object> entryMap = removalEntry.getKey();

            if (active.contains(entryMap)) {
                continue;
            }

            for (Object key : removalEntry.getValue()) {
                entryMap.removeKey(key);
            }

            MapRemovalList.remove(entryMap);
        }

        Map<EMap<Object, Object>, List<Triple<Object, Object, Object>>> mapDuplicateList = new IdentityHashMap<>(MapChangeList);

        for (Map.Entry<EMap<Object, Object>, List<Triple<Object, Object, Object>>> entry : mapDuplicateList.entrySet()) {
            EMap<Object, Object> entryMap = entry.getKey();
            List<Triple<Object, Object, Object>> tripleList = entry.getValue();
            try {
                if (!active.contains(entryMap)) {
                    for (Triple<Object, Object, Object> triple : tripleList) {
                        entryMap.removeKey(triple.getThird());
                        entryMap.put(triple.getFirst(), triple.getSecond());
                    }
                    MapChangeList.remove(entryMap);
                }
            } catch (Exception e) {
                System.out.println("EXCEPTiON: ");
            }
        }

        for (Map.Entry<Object, Object> referenceEntry : changeReference.entrySet()) {
            references.put(referenceEntry.getKey(), map.get(referenceEntry.getValue()));
        }
        changeReference.clear();
    }

    private void executeInstructions(Object object,
                                     List<Instruction> instructionList) {
        for (Instruction instruction : instructionList) {

            List<Condition> conditions = instruction.conditions();
            List<Action> actions = instruction.actions();

            conditionCheck(object, conditions, instruction, actions, null);
        }
    }

    private <V extends Object> void executeInstructions(V object,
                                                        List<Instruction> instructionList,
                                                        EList<V> optList) {
        for (Instruction instruction : instructionList) {

            List<Condition> conditions = instruction.conditions();
            List<Action> actions = instruction.actions();

            conditionCheck(object, conditions, instruction, actions, optList);
        }
    }

    private <K extends Object, V extends Object> void executeInstructions(Object object,
                                                                          List<Instruction> instructionList,
                                                                          EMap<K, V> optMap) {
        for (Instruction instruction : instructionList) {

            List<Condition> conditions = instruction.conditions();
            List<Action> actions = instruction.actions();

            conditionCheck(object, conditions, instruction, actions, optMap);
        }
    }

    private <K extends Object, V extends Object> void conditionCheck(Object object, List<Condition> conditions, Instruction instruction, List<Action> actions, Object optValue) {
        boolean status = true;
        for (Condition condition : conditions) {
            if (!(resolvePath(object, condition.path()) instanceof EObject world)) {
                return;
            }
            if (condition.targetFeature().equals("")) {
                for (EStructuralFeature feature : world.eClass().getEAllStructuralFeatures()) {
                    if (!(visitedFeatures.add(new Tuple<>(object, feature)))) {
                        continue;
                    }

                    Object featureValue = world.eGet(feature);
                    status &= checking(featureValue, condition.operation(), condition.expectedValue(), status);

                    if (status) {
                        if (optValue instanceof EMap<?, ?> optMap) {
                            EMap<K, V> map = (EMap<K, V>) optMap;
                            transformMapEntry(object, feature, instruction, actions, map);
                        } else if (optValue instanceof EList<?> optList) {
                            EList<V> list = (EList<V>) optList;
                            transformListEntry(object, feature, instruction, actions, list);
                        } else if (optValue == null) {
                            transformObject(object, feature, instruction, actions);
                        } else {
                            System.out.println("Something went wrong!");
                        }
                    }
                }
            } else if ((condition.targetFeature().equals("wholeObject"))) {
                if (!(visited.add(new Tuple<>(object, null)))) {
                    return;
                }

                status &= checking(object, condition.operation(), condition.expectedValue(), status);

                if (status) {
                    if (optValue instanceof EMap<?, ?> optMap) {
                        EMap<K, V> map = (EMap<K, V>) optMap;
                        transformMapEntry(object, null, instruction, actions, map);
                    } else if (optValue instanceof EList<?> optList) {
                        EList<V> list = (EList<V>) optList;
                        transformListEntry(object, null, instruction, actions, list);
                    } else if (optValue == null) {
                        transformObject(object, null, instruction, actions);
                    } else {
                        System.out.println("Something went wrong!");
                    }
                }
            } else {
                EStructuralFeature feature = world.eClass().getEStructuralFeature(condition.targetFeature());
                if (feature == null) {
                    continue;
                }

                if (!(visited.add(new Tuple<>(object, feature)))) {
                    System.out.println(ANSI_RED + "Feature " + feature + " & Object " + object + ": ALREADY VISITED" + ANSI_RESET);
                    return;
                }
                Object featureValue = world.eGet(feature);
                status &= checking(featureValue, condition.operation(), condition.expectedValue(), status);

                if (status) {
                    if (optValue instanceof EMap<?, ?> optMap) {
                        EMap<K, V> map = (EMap<K, V>) optMap;
                        transformMapEntry(object, null, instruction, actions, map);
                    } else if (optValue instanceof EList<?> optList) {
                        EList<V> list = (EList<V>) optList;
                        transformListEntry(object, null, instruction, actions, list);
                    } else if (optValue == null) {
                        transformObject(object, null, instruction, actions);
                    } else {
                        System.out.println("Something went wrong!");
                    }
                }
            }
        }
    }

    static private Object resolvePath(Object object, List<String> path) {
        if (path.isEmpty()) {
            return object;
        }

        //ToDo: fix this somewhen
        List<String> pathCopy = new ArrayList<>();
        for (String string : path) {
            pathCopy.add(string);
        }

        try {
            EObject eobject = (EObject) object;
            EStructuralFeature curFeature = eobject.eClass().getEStructuralFeature(path.get(0));
            Object curValue = (Object) eobject.eGet(curFeature);
            pathCopy.remove(0);
            return resolvePath(curValue, pathCopy);
        } catch (Exception e) {
            System.out.println(ANSI_RED + "Path Exception: " + e + ANSI_RESET);
            return object;
        }
    }

    static private boolean checking(Object featureValue, ConditionOp conditionOp, Object expectedValue, boolean status) {

        switch (conditionOp) {
            case IS_CONTAINED_IN_SET:
                Set<?> set = (Set<?>) expectedValue;
                status = status && set.contains(featureValue);
                break;
            case IS_CONTAINEDKEY_IN_MAP:
                Map<?, ?> mapK = (Map<?, ?>) expectedValue;
                status = status && mapK.containsKey(featureValue);
                break;
            case IS_CONTAINEDVALUE_IN_MAP:
                Map<?, ?> mapV = (Map<?, ?>) expectedValue;
                status = status && mapV.containsValue(featureValue);
                break;
            case ISNOT_CONTAINEDVALUE_IN_MAP:
                Map<?, ?> mapNV = (Map<?, ?>) expectedValue;
                status = status && !(mapNV.containsValue(featureValue));
                break;
            case IS_CONTAINED_IN_LIST:
                List<?> list = (List<?>) expectedValue;
                status = status && list.contains(featureValue);
                break;
            case INSTANCEOF:
                Class<?> clazz = (Class<?>) expectedValue;
                status = status && clazz.isInstance(featureValue);
                break;
            case EQUALS:
                if (!(featureValue instanceof String)) {
                    break;
                }
                String string = (String) expectedValue;
                String featureValueString = (String) featureValue;
                status = status && featureValueString.equals(string);
                break;
            case STARTS_WITH:
                if (!(featureValue instanceof String)) {
                    break;
                }
                String string1 = (String) expectedValue;
                String featureValueString1 = (String) featureValue;
                status = status && featureValueString1.startsWith(string1);
                break;
            case ENDS_WITH:
                if (!(featureValue instanceof String)) {
                    break;
                }
                String string2 = (String) expectedValue;
                String featureValueString2 = (String) featureValue;
                status = status && featureValueString2.endsWith(string2);
                break;
            case CONTAINS:
                if (!(featureValue instanceof String featureValueString3)) {
                    break;
                }
                String string3 = (String) expectedValue;
                status = status && featureValueString3.contains(string3);
                break;
            case IS_NULL:
                status = status && featureValue == null;
                break;
            case ALWAYS:
                status = true;
                break;
        }
        return status;
    }

    protected void transformationMapping(Object object) {
        if (visited.contains(object) || object == null) {
            return;
        }
        if (object instanceof EMap<?, ?> map) {
            traverseMap(map);
        } else if (object instanceof EList<?> list) {
            traverseList(list);
        } else if (object instanceof EObject) {
            traverseObject(object);
        }
    }

    protected <V extends Object> void traverseList(EList<V> list) {
        if (list.isEmpty()) {
            return;
        }

        if (active.add(list)) {

            Object firstEntry = list.get(0);

            Class<?> entryClass = firstEntry == null ? null : firstEntry.getClass();
            Class<?> thisClass = list.getClass();

            boolean status = instructions.checkTuple(thisClass, entryClass);

            if (!visited.contains(list)) { //Do we have to alter entries?
                for (V entry : list) {
                    if (status) {
                        executeInstructions(entry, instructions.getListForClassTuple(thisClass, entryClass), list);
                    }
                    transformationMapping(entry);
                }
            }

            for (Map.Entry<Object, Object> entry : ListChangeList.entrySet()) {
                int index = -1;
                if (entry.getKey() != null) {
                    index = list.indexOf(entry.getKey());
                    list.remove(entry.getKey());
                }
                if (entry.getValue() != null) {
                    if (index == -1) {
                        list.add((V) entry.getValue());
                    } else {
                        list.set(index, (V) entry.getValue());
                    }
                }
            }
            ListChangeList.clear();
            visited.add(list);
        }
        active.remove(list);
    }

    protected void traverseObject(Object object) {
        if (object == null) {
            return;
        }
        if (object instanceof EMap<?, ?> map) {
            traverseMap(map);
        } else if (object instanceof EList<?> list) {
            traverseList(list);
        } else if (object instanceof EObject eObject) {

            if (active.add(object)) {

                Class thisClass = object.getClass();
                boolean status = instructions.checkTuple(thisClass);

                if (status) {
                    executeInstructions(eObject, instructions.getListForClassTuple(thisClass));
                }

                for (EStructuralFeature feature : eObject.eClass().getEAllStructuralFeatures()) {
                    if ((eObject.eGet(feature) == null)) {
                        continue;
                    }

                    if ((eObject.eGet(feature) instanceof EMap<?, ?> map)) {
                        traverseMap(map);
                    } else if ((eObject.eGet(feature) instanceof EList<?> list)) {
                        traverseList(list);
                    } else if (feature instanceof EAttribute) {

                    } else if (feature instanceof EReference reference
                            && eObject.eGet(feature) instanceof EObject featureValue) {

                        if (reference.isContainment()) {
                            transformationMapping(featureValue);
                            continue;
                        }

                        Object replacement = references.get(featureValue);

                        if (replacement != null) {
                            if (!feature.isChangeable()) {
                                System.out.println(
                                        ANSI_RED
                                                + "Cannot replace reference "
                                                + feature.getName()
                                                + " in "
                                                + eObject
                                                + ANSI_RESET
                                );

                                transformationMapping(featureValue);
                                continue;
                            }

                            eObject.eSet(feature, replacement);
                            transformationMapping(replacement);

                        } else {
                            transformationMapping(featureValue);
                        }
                    }
                }
                visited.add(object);
            }
            active.remove(object);
        } else {
            System.out.println(ANSI_BLUE + "Object " + object + " is no EObject and could not be traversed" + ANSI_RESET);

        }
    }

    protected <V extends Object> void transformObject(Object object, EStructuralFeature optFeature, Instruction instruction, List<Action> actions) {
        if (object == null) {
            return;
        }
        if (object instanceof EMap<?, ?> map) {
            traverseMap(map);
        } else if (object instanceof EList<?> list) {
            traverseList(list);
        } else if (object instanceof EObject) {
            for (Action action : actions) {

                EObject world = (EObject) resolvePath(object, action.path());
                if (optFeature == null && action.targetFeature() == null) {
                    continue;
                } else if (optFeature == null && action.targetFeature() != null) {
                    optFeature = world.eClass().getEStructuralFeature(action.targetFeature());
                }


                switch (action.operation()) {
                    case SET:
                        Object newValue1 = action.value();
                        if (optFeature.isChangeable()) {
                            world.eSet(optFeature, newValue1);
                        }
                        break;
                    case RECREATE_ENTRY_WITH_KEY:
                        break;

                    case REPLACE:
                        Object featureValue = world.eGet(optFeature);
                        if (optFeature.isChangeable() && (featureValue instanceof String oldName)) {
                            String newName = oldName.replace((String) action.value(), (String) keyMapping.get(action.value()));
                            world.eSet(optFeature, newName);
                        }
                        break;
                    case REPLACE_PART_OF_STRING:
                        Object featureValue2 = world.eGet(optFeature);
                        if (optFeature.isChangeable() && (featureValue2 instanceof String oldName)) {
                            String newName = oldName.replace((String) action.value(), (String) keyMapping.get(action.value()));
                            world.eSet(optFeature, newName);
                        }
                        break;
                    case CREATE_AND_SET:
                        break;
                    case REMOVE:
                        break;
                    case ADD_TO_LIST:
                        break;
                    case REMOVE_FROM_LIST:
                        break;
                    case PUT_TO_MAP:
                        break;
                    case REMOVE_FROM_MAP:
                        break;
                    case REPLACE_FROM_MAP:
                        try {
                            Object featureValue3 = world.eGet(optFeature);
                            Map<?, ?> map = (Map<?, ?>) action.value();
                            Object replacementObject;
                            if (optFeature.isChangeable()) {
                                if (action.optionalValue() == null) {
                                    replacementObject = map.get(featureValue3);
                                } else {
                                    replacementObject = map.get(action.optionalValue());
                                }
                                world.eSet(optFeature, replacementObject);
                            }
                            break;
                        } catch (Exception e) {
                            break;
                        }
                    case PRINT:
                        System.out.println(ANSI_YELLOW + object + " in feature " + optFeature.getName() + " in world " + world + ANSI_RESET);
                        break;
                }
            }
        }
        traverseObject(object);
    }

    protected <K extends Object, V extends Object> void transformMapEntry(Object object, EStructuralFeature optFeature, Instruction instruction, List<Action> actions, EMap<K, V> map) {
        if (object == null) {
            return;
        }

        if (!(object instanceof EObject eObject)) {
            return;
        }

        for (Action action : actions) {

            Object world = resolvePath(eObject, action.path());
            if (!(world instanceof EObject entryEObject)) {
                return;
            }

            if (optFeature == null && action.targetFeature() == null) {
                continue;
            } else if (optFeature == null && action.targetFeature() != null) {
                optFeature = entryEObject.eClass().getEStructuralFeature(action.targetFeature());
            }

            switch (action.operation()) {
                case SET:
                    Object newValue1 = action.value();
                    if (optFeature.isChangeable()) {
                        entryEObject.eSet(optFeature, newValue1);
                    }
                    break;

                case RECREATE_ENTRY_WITH_KEY:
                    EStructuralFeature keyFeature1 = entryEObject.eClass().getEStructuralFeature("key");
                    EStructuralFeature valueFeature1 = entryEObject.eClass().getEStructuralFeature("value");
                    Object oldKey = entryEObject.eGet(keyFeature1);
                    Object newKey;
                    if (action.value() != null) {
                        newKey = action.value();
                    } else {
                        newKey = keyMapping.get(oldKey);
                    }
                    V value = (V) entryEObject.eGet(valueFeature1);
                    addMapChange((EMap<Object, Object>) map, newKey, value, oldKey);
                    //System.out.println(MapChangeList);
                    break;

                case RECREATE_ENTRY_WITH_PARTLY_REPLACED_KEY:
                    EStructuralFeature keyFeature2 = entryEObject.eClass().getEStructuralFeature("key");
                    EStructuralFeature valueFeature2 = entryEObject.eClass().getEStructuralFeature("value");
                    Object oldKey2 = entryEObject.eGet(keyFeature2);
                    if (!(oldKey2 instanceof String)) {
                        break;
                    }
                    Object newKeyPart = keyMapping.get(action.value());
                    if (newKeyPart == null) {
                        break;
                    }
                    Object newKey2 = ((String) oldKey2).replace((String) action.value(), (String) newKeyPart);
                    V value2 = (V) entryEObject.eGet(valueFeature2);
                    addMapChange((EMap<Object, Object>) map, newKey2, value2, oldKey2);
                    break;

                case REPLACE_PART_OF_STRING:
                    Object featureValue = entryEObject.eGet(optFeature);
                    if (optFeature.isChangeable() && (featureValue instanceof String oldName)) {
                        String newName = oldName.replace((String) action.value(), (String) keyMapping.get(action.value()));
                        entryEObject.eSet(optFeature, newName);

                    }
                    break;
                case CREATE_AND_SET:
                    break;
                case REMOVE:
                    break;
                case ADD_TO_LIST:
                    break;
                case REMOVE_FROM_LIST:
                    break;
                case PUT_TO_MAP:
                    break;
                case REMOVE_FROM_MAP:
                    break;
                case REMOVE_THIS_FROM_MAP:
                    EStructuralFeature keyFeature =
                            entryEObject.eClass().getEStructuralFeature("key");

                    if (keyFeature != null) {
                        Object keyToRemove = entryEObject.eGet(keyFeature);
                        addMapRemoval((EMap<Object, Object>) map, keyToRemove);
                    }
                    break;
                case REPLACE_MAPENTRY_FROM_MAP:
                    break;
            }
        }
    }

    static protected <V extends Object> void transformListEntry(Object object, EStructuralFeature optFeature, Instruction instruction, List<Action> actions, EList<V> list) {
        if (object == null) {
            return;
        }

        for (Action action : actions) {
            EObject entryObject = (EObject) resolvePath(object, action.path());
            if (optFeature == null && action.targetFeature() == null) {
                continue;
            } else if (optFeature == null && action.targetFeature() != null) {
                optFeature = entryObject.eClass().getEStructuralFeature(action.targetFeature());
            } else if (action.targetFeature().equals("wholeObject")) {
                optFeature = null;
            }


            switch (action.operation()) {
                case ADD_TO_LIST:
                    Object newValue1 = action.value();
                    ListChangeList.put(null, (Object) newValue1);
                    break;

                case REMOVE_FROM_LIST:
                    Object newValue2 = action.value();
                    ListChangeList.put((EObject) newValue2, null);
                    break;

                case REMOVE_THIS_FROM_LIST:
                    ListChangeList.put(object, null);
                    break;

                case REPLACE_IN_LIST:
                    Object newValue3 = action.value();
                    ListChangeList.put(object, (EObject) newValue3);
                    break;
                case REPLACE_LISTENTRY_FROM_MAP:
                    Map<Object, Object> map = (Map<Object, Object>) action.value();

                    if (!map.containsKey(object)) {
                        break;
                    }

                    Object replacement = map.get(object);

                    if (replacement == null) {
                        throw new IllegalStateException(
                                "Null replacement for list entry: " + object
                        );
                    }

                    ListChangeList.put(object, replacement);

                    break;
            }
        }
    }
}
