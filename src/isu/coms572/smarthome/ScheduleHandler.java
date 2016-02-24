package isu.coms572.smarthome;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ScheduleHandler {
    private static final float CONTINUE_OCCUPANCY = 0.95f;
    private static final float START_OCCUPANCY = 0.05f;
    private static final float BECOMES_DIRTY = 0.1f;
    private static final int TIME_STEPS = 1440;

    public static Schedule generateSchedule() {
        Schedule schedule = new Schedule();
        boolean occupied = false;
        int occupancyStart = -1;
        boolean occupancyDirty = false;
        for (int t = 1; t <= TIME_STEPS; t++) {
            if ( occupied) {
                boolean stillOccupied = randomBinary(CONTINUE_OCCUPANCY);
                if (!stillOccupied) {
                    schedule.add(occupancyStart, t - 1, true, occupancyDirty);
                    occupied = false;
                    occupancyDirty = false;
                } else {
                    occupancyDirty = occupancyDirty || randomBinary(BECOMES_DIRTY);
                }
            } else {
                boolean becomesOccupied = randomBinary(START_OCCUPANCY);
                if (becomesOccupied) {
                    occupied = true;
                    occupancyStart = t;
                    occupancyDirty = randomBinary(BECOMES_DIRTY);
                }
            }
        }

        if (occupied) {
            schedule.add(occupancyStart, TIME_STEPS, true, occupancyDirty);
        }

        return schedule;
    }

    public static void printSchedules(List<Schedule> schedules, String fileName) {
        int i = 1;
        for (Schedule schedule : schedules) {
            System.out.println(i++ + " " + schedule.toString());
        }
    }

    private static boolean randomBinary(float truthProbability) {
        Random rand = new Random();
        return (rand.nextFloat() < truthProbability);
    }


    public static void main(String [] args) {
        int size = 10;
        List<Schedule> schedules = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            schedules.add(generateSchedule());
        }

        printSchedules(schedules, null);
    }

}
