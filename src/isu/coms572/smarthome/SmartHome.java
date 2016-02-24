// SmartHome.java

package isu.coms572.smarthome;

import java.io.File;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

public class SmartHome {
    private static VacuumCleaner vacuumCleaner;
    private static Dustbin dustbin;
    private static final int VCCapacity = 100;
    private static final int DstBCapacity = 500;
    // is Dustbin considered or not

    public static void main(String[] args) {
        if (args.length <= 0) {
            System.out.println("Provide the filename and/or dustbin presence (\"dstb\")");
            return;
        }
        String fileName = args[0];
        boolean isDustbin = (args.length > 1 && args[1].equals("dstb"));
        // minutes in a day, assuming that each action takes 1 minute
        int Timer = 1440;	// 1440 minutes
        // number of rooms which we get after parsing Schedule.txt
        // no_of_rooms + 1 since we want to not confuse our map with room0 i.e., dump room
        List<Schedule> roomSchedules = new ArrayList<>();
        SmartHomeMap home = parseFile(roomSchedules, fileName);
        vacuumCleaner = new VacuumCleaner(home, 1, isDustbin, VCCapacity);
        if (isDustbin) {
            dustbin = new Dustbin(home, 1, DstBCapacity, VCCapacity);
        }

        int actionsCost = 0;
        PrintWriter performanceFile = null;
        try {
            performanceFile = new PrintWriter("performance-log.txt", "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        double performance = 0.0;
        // Run Timer to send the actions after we have gathered all the data from Schedule.txt
        for(int cycle=0; cycle < Timer; ++cycle) {
            // Do send actions to VC and DstB as per the Schedule
        	System.out.println("### TIMER: " + cycle + " ###");
            List<Node> changedRooms = new ArrayList<>();
            for(int i = 1; i < home.size; ++i) {
                Node room = home.rooms.get(i);
                Integer element = roomSchedules.get(i).cloneTime.floor(cycle);
                boolean roomOccupied = false;
                boolean roomDirty = room.dirty;
                room.dirtyOccupied = false;
                if(element != null) {
                    // Room may be Occupied and may be dirty
                    // Send percept VC and DstB
                    int index = roomSchedules.get(i).startTime.indexOf(element);
//                    System.out.println("Floor value at Index " + index + " Room " + current + " Timer " + cycle);
                    if(cycle <= roomSchedules.get(i).endTime.get(index)) {
                        roomOccupied = true;
//                        System.out.println("EndTime " + roomSchedules.get(i).endTime.get(index) + " isDirty; maybe Occupied");
                        roomDirty = room.dirty || roomSchedules.get(i).isDirty.get(index);
                        if (roomSchedules.get(i).isDirty.get(index)) {
                            room.dirtyOccupied = true;
                        }
                    }

                }
                if (room.dirty != roomDirty || room.occupied != roomOccupied) {
                    changedRooms.add(room);
                }
                room.updateStatus(roomOccupied, roomDirty);
            }

            // Dustbin
            boolean dumpRequested = false;
            if (isDustbin) {
                DstBPercept dstBPercept = new DstBPercept(vacuumCleaner.getVCLocation(), vacuumCleaner.getVCLoad());
                DstBAction dstBAction = dustbin.action(dstBPercept);
                dumpRequested = dstBAction.action == DstBActions.LOAD;
                actionsCost += dstBAction.action.actionCost;
            }

            // VC
            VCPercept vcPercept = new VCPercept(changedRooms, dumpRequested);
            VCAction vcAction = vacuumCleaner.action(vcPercept);
            actionsCost += vcAction.action.actionCost;
            System.out.println("Rooms Cleaned: " + vacuumCleaner.getCleanRooms() + " Action Cost: " + actionsCost + "Performance Measure: " + performance);
            performance = vacuumCleaner.getCleanRooms() / Math.log(actionsCost);

            performanceFile.println(performance);
        }

        performanceFile.close();

        System.out.println("Final Performance Measure: " + performance);
    }


    public static SmartHomeMap parseFile(List<Schedule> schedules, String scheduleFileName) {
        schedules.add(null);
        File file = new File(scheduleFileName);
        SmartHomeMap map = new SmartHomeMap();
        Scanner scan = null;
        try {
            scan = new Scanner(file);
            
            boolean readEdges = false;
            while (scan.hasNextLine()) {
                if (!readEdges) {
                    String roomOrEdge = scan.next();
                    if (roomOrEdge.equals("Edge")) {
                        readEdges = true;
                        scan.nextLine();
                        continue;
                    }
                    
                    Schedule roomSchedule = new Schedule();
                    schedules.add(roomSchedule);
                    map.addRoom(false, true);
                    int roomNumber = Integer.parseInt(roomOrEdge);
                    String line = scan.nextLine();
                    for (String timeInterval : line.split(" ")) {
                        if (timeInterval.equals("")) {
                            continue;
                        }
                        String[] tokens = timeInterval.split("-");
                        boolean dirty = false;
                        if (tokens[1].charAt(tokens[1].length() - 1) == 'D') {
                            dirty = true;
                            tokens[1] = tokens[1].replace("D", "");
                        }
                        int startTime = Integer.parseInt(tokens[0]);
                        int endTime = Integer.parseInt(tokens[1]);
                        roomSchedule.add(startTime, endTime, true, dirty);
                    }
                } else {
                    String edges = scan.nextLine();
                    String[] token = edges.split(",");
                    int startRoom = Integer.parseInt(token[0]);
                    int endRoom = Integer.parseInt(token[1]);
                    map.addEdge(startRoom, endRoom);
                }
            }
            scan.close();
        } catch (Exception e) {
            System.out.println("Invalid file format: ");
            e.printStackTrace();
            System.exit(1);
        }
        
        return map;
    }

    protected static boolean isInt(String s) {
        try {
            Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }
}

class Schedule {
    TreeSet<Integer> cloneTime;

    List<Integer> startTime;
    List<Integer> endTime;
    List<Boolean> isDirty;
    List<Boolean> isOccupied;

    public Schedule() {
        this.cloneTime = new TreeSet<Integer>();

        this.startTime = new ArrayList<Integer>();
        this.endTime = new ArrayList<Integer>();
        this.isDirty = new ArrayList<Boolean>();
        this.isOccupied = new ArrayList<Boolean>();
    }

    public void add(int startTime, int endTime, boolean isOccupied, boolean isDirty) {
        this.cloneTime.add(startTime);

        this.startTime.add(startTime);
        this.endTime.add(endTime);
        this.isDirty.add(isDirty);
        this.isOccupied.add(isOccupied);
    }

    @Override
    public String toString() {
        return startTime.stream()
                .map(x -> x.toString() + "-" + endTime.get(startTime.indexOf(x)) + (isDirty.get(startTime.indexOf(x)) ? "D" : ""))
                .collect(Collectors.joining(" "));
    }

}