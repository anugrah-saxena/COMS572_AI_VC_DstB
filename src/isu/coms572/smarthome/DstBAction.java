package isu.coms572.smarthome;

enum DstBActions{
    STAY(0),
    MOVE(8),
    LOAD(1),
    DUMP(1);

    public int actionCost;
    DstBActions(int cost) {
        this.actionCost = cost;
    }
}

public class DstBAction {
    DstBActions action;
    Node nextNode;

    public DstBAction(DstBActions action) {
        this.action = action;
    }

    public DstBAction(DstBActions action, Node nextNode) {
        this.action = action;
        this.nextNode = nextNode;
    }
}
