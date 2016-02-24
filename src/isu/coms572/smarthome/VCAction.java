package isu.coms572.smarthome;

enum VCActions{
    STAY(0),
    CLEAN(10),
    MOVE(10),
    DUMP(1);

    public int actionCost;
    VCActions(int cost) {
        this.actionCost = cost;
    }
}

public class VCAction {
    VCActions action;
    Node nextNode;
    int VCLoad;

    public VCAction(VCActions action, int currentLoad) {
        this.action = action;
        this.VCLoad = currentLoad;
    }

    public VCAction(VCActions action, int currentLoad, Node nextNode) {
        this(action, currentLoad);
        this.nextNode = nextNode;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof VCAction)) {
            return false;
        }

        VCAction otherAction = (VCAction) other;
        return (this.action == otherAction.action) && (this.VCLoad == otherAction.VCLoad) &&
                (this.nextNode == otherAction.nextNode);
    }

    public String log(Node currentNode) {
        String summary =  "#VC# [" + action.toString() + "; location: " + currentNode.id + "; current load: " + VCLoad;
        if (action == VCActions.MOVE) {
            summary += "; move to room: " + nextNode.id;
        }
        summary += "]";

        return summary;
    }
}
