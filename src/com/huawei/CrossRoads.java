package com.huawei;

import java.util.ArrayList;
import java.util.HashMap;

public class CrossRoads {
    private int id;
    private ArrayList<Integer> roadIds = new ArrayList<>();

    public CrossRoads(int id) {
        this.id = id;
    }

    public CrossRoads(String line) {
        String[] vars = line.split(",");
        this.id = Integer.parseInt(vars[0]);
        for (int i = 1; i < vars.length; i++) {
            if (Integer.parseInt(vars[i]) > 0) {
                roadIds.add(Integer.parseInt(vars[i]));
            }
        }
    }

    public ArrayList<Integer> getRoadIds() {
        return roadIds;
    }

    //    public void addRoad(Road road) {
//        roads.put(road.getId(), road);
//    }
//
//    public Road getRoad(int roadId) {
//        return roads.get(roadId);
//    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}
