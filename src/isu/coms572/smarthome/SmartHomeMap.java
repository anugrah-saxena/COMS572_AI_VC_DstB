package isu.coms572.smarthome;

import java.util.ArrayList;
import java.util.List;

public class SmartHomeMap {
    public List<Node> rooms;
    public int size;
    public Node dumpRoom;

    public SmartHomeMap() {
        size = 1;
        rooms = new ArrayList<>();
        this.rooms.add(new Node(0, false, false));
        dumpRoom = rooms.get(0);
    }

    public void addRoom(boolean occupied, boolean dirty) {
        this.rooms.add(new Node(size, occupied, dirty));
        size++;
    }

    public void addEdge(int nodeId1, int nodeId2) {
        Node node1 = findNodeWithId(nodeId1);
        Node node2 = findNodeWithId(nodeId2);
        if (node1 == null || node2 == null) {
            return;
        }
        node1.adjacentRooms.add(node2);
        node2.adjacentRooms.add(node1);
    }

    public void updateRoomState(int roomId, boolean occupied, boolean dirty) {
        Node node = findNodeWithId(roomId);
        if (node != null) {
            node.updateStatus(occupied, dirty);
        }
    }

    public Node findNodeWithId(int id) {
        for (Node node : rooms) {
            if (node.id == id) {
                return node;
            }
        }

        return null;
    }

    public SmartHomeMap deepClone() {
        SmartHomeMap clone = new SmartHomeMap();
        for (Node room : this.rooms) {
            clone.addRoom(room.occupied, room.dirty);
        }
        for (Node room : this.rooms) {
            for (Node adjacentRoom : room.adjacentRooms) {
                if (adjacentRoom.id > room.id) {
                    clone.addEdge(room.id, adjacentRoom.id);
                }
            }
        }

        return clone;
    }
}


class Node {
    public List<Node> adjacentRooms = new ArrayList<>();
    public int id;
    public boolean occupied;
    public boolean dirty;

    public boolean dirtyOccupied = false;

    public Node(int id, boolean occupied, boolean dirty) {
        this.id = id;
        this.updateStatus(occupied, dirty);
    }

    public void updateStatus(boolean occupied, boolean dirty) {
        this.occupied = occupied;
        this.dirty = dirty;
    }
}
