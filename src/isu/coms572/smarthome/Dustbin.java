package isu.coms572.smarthome;

import java.util.ArrayDeque;
import java.util.Queue;

public class Dustbin {
    public SmartHomeMap map;

    private int load;
    private int VCCapacity;
    private int DstBCapacity;
    private Node[] pathToDumpRoom;
    private Node currentNode;
    private Queue<Node> actionPlan = null;

    public String log(DstBAction action) {
        return "#DstB# [Room " + this.currentNode.id + ", Load = " + this.load + ", Capacity = " + this.DstBCapacity +
                ", action taken: " + action.action.toString() + "]";

    }

    public Dustbin(SmartHomeMap map, int initialRoomId, int DstBCapacity, int VCCapacity) {
        this.map = map;
        this.load = 0;
        this.VCCapacity = VCCapacity;
        this.DstBCapacity = DstBCapacity;
        this.pathToDumpRoom = findPathToDumpRoom();
        this.currentNode = map.findNodeWithId(initialRoomId);
        
        System.out.println("INIT #DstB# [Room " + this.currentNode.id + ", Load = " + this.load + ", Capacity = " + this.DstBCapacity + "]");
    }

    public DstBAction action(DstBPercept percept) {
        if (this.load + VCCapacity > DstBCapacity) {
            actionPlan = null;
            if (currentNode == map.dumpRoom) {
                load = 0;
                DstBAction action = new DstBAction(DstBActions.DUMP);
                System.out.println(log(action));
                return action;
            } else {
                currentNode = pathToDumpRoom[currentNode.id];
                DstBAction action = new DstBAction(DstBActions.MOVE, currentNode);
                System.out.println(log(action));
                return action;
            }
        } else if (percept.VCLoad + VacuumCleaner.TRASH_PER_CLEAN_ACTION > VCCapacity)  {
            if (percept.VCLocation.id == currentNode.id) {
                load += VCCapacity;
                DstBAction action =  new DstBAction(DstBActions.LOAD, currentNode);
                System.out.println(log(action));
                return action;
            }
            if (actionPlan == null || actionPlan.isEmpty()) {
            	System.out.println("DstB goto VC: " + percept.VCLocation.id);
                actionPlan = findPathToVC(percept.VCLocation);
            }
            if (!actionPlan.isEmpty()) {
                currentNode = actionPlan.remove();
                DstBAction action = new DstBAction(DstBActions.MOVE, currentNode);
                System.out.println(log(action));
                return action;
            } else {
                return new DstBAction(DstBActions.STAY);
            }

        } else {
            actionPlan = null;
            return new DstBAction(DstBActions.STAY);
        }
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
                    queue.add(adjacentRoom);
                    nextNodes[adjacentRoom.id] = currentRoom;
                }
            }
        }

        return nextNodes;
    }

    private Queue<Node> findPathToVC(Node VCLocation) {
        Queue<Node> path = new ArrayDeque<>();

        Queue<Node> queue = new ArrayDeque<>();

        boolean [] visited = new boolean[map.size];
        Node [] parentNodes = new Node[map.size];
        queue.add(map.dumpRoom);
        while(!queue.isEmpty() && VCLocation != currentNode) {
            Node currentRoom = queue.remove();
            visited[currentRoom.id] = true;
            for (Node adjacentRoom : currentRoom.adjacentRooms) {
                if (!visited[adjacentRoom.id]) {
                    parentNodes[currentRoom.id] = adjacentRoom;
                    queue.add(adjacentRoom);
                }
            }

        }

        if (visited[VCLocation.id]) {
            currentNode = VCLocation;
            while (currentNode != null) {
                path.add(currentNode);
                currentNode = parentNodes[currentNode.id];
            }
        }

        return path;
    }

}

class DstBPercept {
    Node VCLocation;
    int VCLoad;

    public DstBPercept(Node VCLocation, int VCLoad) {
        this.VCLoad = VCLoad;
        this.VCLocation = VCLocation;
    }
}
