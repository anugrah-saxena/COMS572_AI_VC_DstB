package isu.coms572.smarthome;

//import sun.swing.plaf.synth.DefaultSynthStyle;

import java.util.*;

public class VacuumCleaner {
    public static final int OVERALL_STEPS_TO_CLEAN = 5;
    private int CAPACITY;
    public static int TRASH_PER_CLEAN_ACTION = 5;

    private Queue<VCAction> actionPlan = null;

    private SmartHomeMap map;
    private boolean dustbinPresent;
    private VCState state;
    Node [] currentPathToDumpRoom = null;

    public VacuumCleaner(SmartHomeMap map, int initialRoomId, boolean dustbinPresent) {
        this.map = map;
        this.state = new VCState(map, initialRoomId);
        this.CAPACITY = 200;
        this.dustbinPresent = dustbinPresent;
        
        System.out.println("INIT #VC# [Room " + this.getVCLocation().id + ", Load = " + this.getVCLoad() + ", Capacity = " + this.CAPACITY + "]");
    }

    public VacuumCleaner(SmartHomeMap map, int initialRoomId, boolean dustbinPresent, int capacity) {
        this(map, initialRoomId, dustbinPresent);
        this.CAPACITY = capacity;
    }

    public Node getVCLocation() {
        return this.state.currentRoom;
    }

    public int getVCLoad() {
        return this.state.load;
    }

    public int getCleanRooms() {
    	// need to update map for clean rooms
//        if (state.totalCleanStepsToBeTaken > 50) {
//            System.out.println("lol");
//        }
        return (map.size - 1)  * OVERALL_STEPS_TO_CLEAN - this.state.totalCleanStepsToBeTaken;
    }

    public VCAction action(VCPercept percept) {
        // update state
        state.updateState(percept);
        VCAction actionTaken = null;

        if (percept.requestedTransfer && state.load > 0) {
            currentPathToDumpRoom = null;
            // dump trash to the dustbin agent
            state.load = 0;
            actionPlan = findOptimalPath(); // update actionPlan
            actionTaken = new VCAction(VCActions.DUMP, state.load);
        } else {
            if (!dustbinPresent && state.load + TRASH_PER_CLEAN_ACTION > CAPACITY) {
                // need to go to the dump room myself
                actionPlan = null;
                if (state.currentRoom.id == 0) {
                    // In the dump room - dump and proceed
                    state.load = 0;
                    actionTaken = new VCAction(VCActions.DUMP, state.load);
                    currentPathToDumpRoom = null;
                } else {
                    if (currentPathToDumpRoom == null || currentPathToDumpRoom[state.currentRoom.id] == null ||
                            currentPathToDumpRoom[state.currentRoom.id].occupied) {
                        currentPathToDumpRoom = findPathToDumpRoom();
                    }
                    if (currentPathToDumpRoom[state.currentRoom.id] == null) {
                        actionTaken = new VCAction(VCActions.STAY, state.load);
                    } else {
                        actionTaken = new VCAction(VCActions.MOVE, state.load, currentPathToDumpRoom[state.currentRoom.id]);
                    }
                }

            }  else if (state.load + TRASH_PER_CLEAN_ACTION > CAPACITY) {
                // dustbin is present and I am full -- wait for the dustbin to come
                actionTaken = new VCAction(VCActions.STAY, state.load);
            } else {
                currentPathToDumpRoom = null;
                if (state.roomsToClean != 0) {
                    if (actionPlan == null || actionPlan.isEmpty()) {
                        actionPlan = findOptimalPath();
                    }

                    if (actionPlan.isEmpty()) {
                        actionTaken = new VCAction(VCActions.STAY, state.load);
                    } else {
                        VCAction nextAction = actionPlan.remove();
                        if (possibleActions(state, false).contains(nextAction)) {
                            // if planned action is still possible, then do it
                            actionTaken = nextAction;
                        } else {
                            // run/rerun A*
                            actionPlan = findOptimalPath();
                            if (!actionPlan.isEmpty()) {
                                actionTaken = actionPlan.remove();
                            } else {
                                actionTaken = new VCAction(VCActions.STAY, state.load);
                            }
                        }
                    }
                } else {
                    // nothing to clean
                    actionTaken = new VCAction(VCActions.STAY, state.load);
                }
            }
        }

        if (actionTaken.action != VCActions.STAY) {
            System.out.println(actionTaken.log(state.currentRoom));
        }
        state = resolveNextState(state, actionTaken, false);
        return actionTaken;
    }

    /**
     * Runs A* search algorithm. Does not consider dump actions; therefore,
     * when VC gets full there are no more successor actions. In addition, does not consider STAY action -
     * see {@link #possibleActions}.
     *
     * @return sequence (queue) of actions that leads to optimal/suboptimal (in case VC gets full) behavior
     */
    private Queue<VCAction> findOptimalPath() {
        Map<VCState, SearchStateInfo> STATES = new HashMap<>();
        Queue<VCState> OPEN = new PriorityQueue<>();
        int gValue;
        state.gValue = 0;
        OPEN.add(state);
        STATES.put(state, new SearchStateInfo(false, 0, null, null));
        VCState currentState;
        do {
            currentState = OPEN.remove();

            try{
                STATES.get(currentState).inClosed = true;
            } catch (Exception e) {
                System.out.println("error: " + currentState.toString());
                System.out.println("in states: " + STATES.containsKey(currentState));
                continue;
            }

            gValue = currentState.gValue; // TODO: consider a custom comparator
            for (VCAction nextAction : possibleActions(currentState, true)) {
                VCState childState = resolveNextState(currentState, nextAction, true);
                if(STATES.containsKey(childState)) {
                    SearchStateInfo stateInfo = STATES.get(childState);
                    if (!stateInfo.inClosed && stateInfo.stateGValue > childState.gValue) {
                        stateInfo.stateGValue = childState.gValue;
                        stateInfo.parentState = currentState;
                        stateInfo.parentAction = nextAction;

                        // need to update the state in open:
                        OPEN.remove(childState);
                        OPEN.add(childState);
                    }
                } else {
                    STATES.put(childState, new SearchStateInfo(false, childState.gValue, currentState, nextAction));
                    OPEN.add(childState);
                }
            }
        }
        while(currentState.roomsToClean > 0 && !OPEN.isEmpty());

        if (currentState.roomsToClean > 0) {
            VCState optimalState = null;
            // find suboptimal path
            int minHValue = Integer.MAX_VALUE;
            for (Map.Entry<VCState, SearchStateInfo> entry : STATES.entrySet()) {
                VCState candidateState = entry.getKey();
                if (candidateState.hValue() < minHValue) {
                    minHValue = candidateState.hValue();
                    optimalState = candidateState;
                }
            }
            currentState = optimalState;
        }

        List<VCAction> path = new ArrayList<>();
        while (currentState != null) {
            try{
            SearchStateInfo stateInfo = STATES.get(currentState);
            if (stateInfo.parentAction != null) {
                path.add(stateInfo.parentAction);
            }
            currentState = STATES.get(currentState).parentState;
            } catch (Exception e) {
//                System.out.println("error: " + currentState.toString());
//                System.out.println("in states: " + STATES.containsKey(currentState));
                          currentState = null;
            }
        }

        Queue<VCAction> actionQueue = new ArrayDeque<>();
        for (int i = path.size() - 1; i >= 0; i--) {
            VCAction action = path.get(i);
            int timesToAdd = (action.action == VCActions.CLEAN) ? OVERALL_STEPS_TO_CLEAN : 1;
            for (int k = 0; k < timesToAdd; k++) {
                actionQueue.add(action);
            }
        }
            return actionQueue;



    }

    // if search, then we do not care about capacity
    private List<VCAction> possibleActions(VCState parentState, boolean search) {
        List<VCAction> actions = new ArrayList<>();

        if (!search && state.load + TRASH_PER_CLEAN_ACTION > CAPACITY) {
            // If it is impossible to clean - return none actions
            return actions;
        }

        // Cleaning action:
        if (parentState.isCurrentRoomDirty() && !parentState.isCurrentRoomOccupied()) {
            if (search || state.load + TRASH_PER_CLEAN_ACTION <= CAPACITY) {
                actions.add(new VCAction(VCActions.CLEAN, state.load + TRASH_PER_CLEAN_ACTION));
            }
        }

        // Moving actions:
        for (Node adjacentRoom : parentState.currentRoom.adjacentRooms) {
            if (!adjacentRoom.occupied) {
                actions.add(new VCAction(VCActions.MOVE, state.load, adjacentRoom));
            }
        }

        // TODO: add DUMP action???

        return actions;

    }

    private Node[] findPathToDumpRoom() {
        Node[] nextNodes = new Node[map.size];
        nextNodes[map.dumpRoom.id] = null;

        Queue<Node> queue = new ArrayDeque<>();
        boolean [] visited = new boolean[map.size];
        queue.add(map.dumpRoom);
        while(!queue.isEmpty()) {
            Node currentRoom = queue.remove();
            visited[currentRoom.id] = true;
            for (Node adjacentRoom : currentRoom.adjacentRooms) {
                if (!visited[adjacentRoom.id]) {
                    if (!adjacentRoom.occupied) {
                        queue.add(adjacentRoom);
                        nextNodes[adjacentRoom.id] = currentRoom;
                    }
                }
            }
        }

        return nextNodes;
    }

    // if search, then one clean action is actually all clean Actions for a room
    private VCState resolveNextState(VCState parentState, VCAction action, boolean search) {
        // TODO: consider the update of gValues here (constructor)
        VCState nextState = null;
        switch (action.action) {
            case CLEAN:
                if (search) {
                    nextState = new VCState(parentState, VCActions.CLEAN.actionCost * parentState.getRemStepsToClean());
                    nextState.doSearchCleanStep();

                } else {
                    nextState = new VCState(parentState, VCActions.CLEAN.actionCost);
                    nextState.doCleanStep();
                }
                break;
            case MOVE:
                nextState = new VCState(parentState, VCActions.MOVE.actionCost);
                nextState.currentRoom = action.nextNode;
                break;
            case DUMP:
                nextState = new VCState(parentState, VCActions.DUMP.actionCost);
                nextState.load = 0;
                break;
            case STAY:
                nextState = new VCState(parentState, VCActions.CLEAN.actionCost);
                break;
        }

        return nextState;
    }

    private static class SearchStateInfo {
        boolean inClosed;
        int stateGValue;
        VCState parentState;
        VCAction parentAction;
        SearchStateInfo(boolean inOpen, int gValue, VCState parentState, VCAction parentAction) {
            this.inClosed = inOpen;
            this.stateGValue = gValue;
            this.parentState = parentState;
            this.parentAction = parentAction;
        }
    }

    public static void main(String[] args) {
        // test! TODO: remove
        Map<VCState, SearchStateInfo> map = new HashMap<>();
        SmartHomeMap shMap = new SmartHomeMap();
        shMap.addRoom(true, true);
        shMap.addRoom(true, false);

        VCState state1 = new VCState(shMap, 1);
        VCState state2 = new VCState(state1, 10);
        map.put(state1, new SearchStateInfo(false, 0, null, null));
        System.out.println("Contains state 2: " + map.containsKey(state2));
        SearchStateInfo info = map.get(state2);
        info.inClosed = true;
        map.put(state2, info);
        for (Map.Entry<VCState, SearchStateInfo> entry : map.entrySet()) {
            System.out.println("Entry gValue: " + entry.getKey().gValue);
            System.out.println("Entry inclosed: " + entry.getValue().inClosed);
        }

    }
}

class VCPercept {
    List<Node> changedRooms;
    boolean requestedTransfer;

    public VCPercept(List<Node> changedRooms, boolean requestedTransfer) {
        this.changedRooms = changedRooms;
        this.requestedTransfer = requestedTransfer;
    }
}

class VCState implements Comparable<VCState> {
    private static final int EMPTY = 0;
    private static final int DIRTY = 1;
    private static final int OCCUPIED = 2;
    private static final int OCC_DIRTY = 3;

    public Node currentRoom;
    public int load;
    public int roomsToClean;
    public int gValue;
    public int totalCleanStepsToBeTaken;

    public int [] remainingStepsToClean;

    private int [] roomStates;

    public VCState(SmartHomeMap map, int initialRoomId) {
        roomStates = new int[map.size];
        remainingStepsToClean = new int[map.size];
        for (Node room : map.rooms) {
            roomStates[room.id] = roomStatus(room);
            remainingStepsToClean[room.id] = VacuumCleaner.OVERALL_STEPS_TO_CLEAN;
        }
        this.currentRoom = map.findNodeWithId(initialRoomId);
        this.roomsToClean = map.size - 1;
        this.load = 0;
        gValue = 0;
        totalCleanStepsToBeTaken = (map.size - 1) * VacuumCleaner.OVERALL_STEPS_TO_CLEAN;
    }

    public VCState(VCState otherState, int actionCost) {
        this.roomStates = otherState.roomStates.clone();
        this.remainingStepsToClean = otherState.remainingStepsToClean.clone();
        this.currentRoom = otherState.currentRoom;
        this.roomsToClean = otherState.roomsToClean;
        this.load = otherState.load;
        this.gValue = otherState.gValue + actionCost;
        this.totalCleanStepsToBeTaken = otherState.totalCleanStepsToBeTaken;
    }

    public void updateState(VCPercept percept) {
        for (Node changedRoom : percept.changedRooms) {
            if ((roomStates[changedRoom.id] != DIRTY) && (roomStates[changedRoom.id] != OCC_DIRTY) && changedRoom.dirty) {
                roomsToClean ++;
                remainingStepsToClean[changedRoom.id] = VacuumCleaner.OVERALL_STEPS_TO_CLEAN;
                totalCleanStepsToBeTaken += VacuumCleaner.OVERALL_STEPS_TO_CLEAN;
            }
            if (changedRoom.dirtyOccupied && remainingStepsToClean[changedRoom.id] < VacuumCleaner.OVERALL_STEPS_TO_CLEAN) {
                totalCleanStepsToBeTaken += VacuumCleaner.OVERALL_STEPS_TO_CLEAN - remainingStepsToClean[changedRoom.id];
                remainingStepsToClean[changedRoom.id] = VacuumCleaner.OVERALL_STEPS_TO_CLEAN;
            }
            roomStates[changedRoom.id] = roomStatus(changedRoom);
        }
    }

    public boolean isCurrentRoomDirty() {
        return (roomStates[currentRoom.id] == DIRTY || roomStates[currentRoom.id] == OCC_DIRTY);
    }

    public boolean isCurrentRoomOccupied() {
        return (roomStates[currentRoom.id] == OCCUPIED || roomStates[currentRoom.id] == OCC_DIRTY);
    }

    public int hValue() {
        return (roomsToClean - 1) * VCActions.MOVE.actionCost + totalCleanStepsToBeTaken * VCActions.CLEAN.actionCost;
    }

    public boolean doCleanStep() {
        int cleanStepsTaken = VacuumCleaner.OVERALL_STEPS_TO_CLEAN - remainingStepsToClean[currentRoom.id];
        if (cleanStepsTaken < VacuumCleaner.OVERALL_STEPS_TO_CLEAN) {
            load += VacuumCleaner.TRASH_PER_CLEAN_ACTION;
            cleanStepsTaken ++;
            remainingStepsToClean[currentRoom.id] --;
            if (cleanStepsTaken == VacuumCleaner.OVERALL_STEPS_TO_CLEAN) {
                updateStatusCleaned();
                currentRoom.dirty = false;
                roomsToClean --;
            }
            this.totalCleanStepsToBeTaken--;

            return true;
        }

        return false;
    }

    public boolean doSearchCleanStep() {
        if (remainingStepsToClean[currentRoom.id] > 0) {
            roomsToClean--;
            load += remainingStepsToClean[currentRoom.id] * VacuumCleaner.TRASH_PER_CLEAN_ACTION;
            totalCleanStepsToBeTaken -= remainingStepsToClean[currentRoom.id];
            remainingStepsToClean[currentRoom.id] = 0;
            updateStatusCleaned();
            return true;
        }

        return false;
    }

    public int getRemStepsToClean() {
        return remainingStepsToClean[currentRoom.id];
    }

    public void updateStatusCleaned() {
        if (roomStates[currentRoom.id] == DIRTY) {
            roomStates[currentRoom.id] = EMPTY;
        } else if (roomStates[currentRoom.id] == OCC_DIRTY){
            roomStates[currentRoom.id] = OCCUPIED;
        }
    }

    @Override
    public int compareTo(VCState other) {
        if (other == null) {
            return 1;
        }
        return ((Integer)(this.hValue() + this.gValue)).compareTo(other.gValue + other.hValue());
    }

    @Override
    public int hashCode() {
        int hashcode = 0;
        for (int i = 1; i < roomStates.length; i++) {
            int offset = ((i - 1) * 2) % 30;
            hashcode = (hashcode + (roomStates[i] << offset)) % Integer.MAX_VALUE;
        }

        return (hashcode + currentRoom.id + totalCleanStepsToBeTaken) % Integer.MAX_VALUE;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof VCState)) {
            return false;
        }

        VCState otherState = (VCState) obj;
        if (otherState.roomStates.length != this.roomStates.length) {
            return false;
        }

        for (int i = 1; i < this.roomStates.length; i++) {
            if (this.roomStates[i] != otherState.roomStates[i] || this.remainingStepsToClean[i] != otherState.remainingStepsToClean[i]) {
                return false;
            }
        }

        return (this.currentRoom.id == otherState.currentRoom.id && this.totalCleanStepsToBeTaken == otherState.totalCleanStepsToBeTaken);
    }

    public String toString() {
        return Arrays.toString(remainingStepsToClean) + " " + Arrays.toString(roomStates) + " " + currentRoom.id;
    }

    public int roomStatus(Node room) {
        if (room.dirty && room.occupied) {
            return OCC_DIRTY;
        } else if (room.dirty) {
            return DIRTY;
        } else if (room.occupied) {
            return OCCUPIED;
        } else {
            return EMPTY;
        }
    }

}
