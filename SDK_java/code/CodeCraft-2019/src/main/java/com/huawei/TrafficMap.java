package com.huawei;

import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.DirectedWeightedMultigraph;

import java.util.*;

public class TrafficMap {
    private Graph<CrossRoads, DefaultWeightedEdge> graph =
            new DirectedWeightedMultigraph<>(DefaultWeightedEdge.class);

    private PriorityQueue<Car> priorityQueue = new PriorityQueue<>();

    private HashMap<Integer, CrossRoads> crossMap = new HashMap<>();
    private HashMap<Integer, Road> roads = new HashMap<>();
    private HashMap<Integer, Car> cars = new HashMap<>();

    private final int MAX_CAR_LIMIT = 26;


    public void initGraph() {
        crossMap.forEach((cross, crossObj) -> graph.addVertex(crossObj));
        roads.forEach((roadId, road) -> {
            CrossRoads from = crossMap.get(road.getStart());
            CrossRoads to = crossMap.get(road.getEnd());
            DefaultWeightedEdge edge = graph.addEdge(from, to);
            graph.setEdgeWeight(edge, road.getLen());
            if (road.isBidirectional()) {
                DefaultWeightedEdge opposeEdge = graph.addEdge(to, from);
                graph.setEdgeWeight(opposeEdge, road.getLen());
            }
        });
    }

    public GraphPath shortestDistancePath(int from, int to) {
        return DijkstraShortestPath.findPathBetween(graph, crossMap.get(from), crossMap.get(to));
    }

    public void setCarPath(Car car, GraphPath path) {
        // Clean original path
        car.getPath().clear();
        //Add new path
        for (int i = 0; i < path.getLength(); i++) {
            ArrayList<Integer> list1 = ((CrossRoads) path.getVertexList().get(i)).getRoadIds();
            ArrayList<Integer> list2 = ((CrossRoads) path.getVertexList().get(i + 1)).getRoadIds();
            ArrayList<Integer> list3 = new ArrayList<>(list1);
            list3.retainAll(list2);
            int road = list3.get(0);
            car.addPath(road);
        }
    }

    public void schedule() {
        this.getCars().forEach(
                (carId, car) -> priorityQueue.offer(car)
        );
        int time = 0;
        int count = 0;
        int planTime = 1;
        while (!priorityQueue.isEmpty()) {
            time++;
            count = 0;
            while (true) {
                Car car = priorityQueue.peek();
                if (car == null || car.getPlanTime() > time || count >= MAX_CAR_LIMIT)
                    break;

                car.setStartTime(time);

                GraphPath path = shortestDistancePath(car.getFrom(), car.getTo());
                setCarPath(car, path);
                if (planTime<car.getPlanTime()){
                    planTime = car.getPlanTime();
                    time++;
                }
                priorityQueue.remove(car);
                count++;
            }
        }
    }

    public void preSchedule() {
        this.getCars().forEach(
                (carId, car) -> priorityQueue.offer(car)
        );

        //Busy path counter
        TreeMap<Integer, Integer> roadCounter = new TreeMap<>();

        int time = 0;
        int count = 0;
        while (!priorityQueue.isEmpty()) {
            time++;
            count = 0;
            while (true) {
                Car car = priorityQueue.peek();
                if (car == null || car.getPlanTime() > time || count >= MAX_CAR_LIMIT)
                    break;

                car.setStartTime(time);

                GraphPath path = shortestDistancePath(car.getFrom(), car.getTo());
                setCarPath(car, path);

                // 计算每一时间单位最忙的路
                car.getPath().forEach(road -> {
                    if (roadCounter.containsKey(road))
                        roadCounter.put(road, roadCounter.get(road) + 1);
                    else
                        roadCounter.put(road, 1);
                });


                priorityQueue.remove(car);
                count++;
            }
        }

        List<Map.Entry<Integer, Integer>> list = new ArrayList<>(roadCounter.entrySet());

        Collections.sort(list, new Comparator<Map.Entry<Integer, Integer>>() {
            //升序排序
            public int compare(Map.Entry<Integer, Integer> o1, Map.Entry<Integer, Integer> o2) {
                return o1.getValue().compareTo(o2.getValue());
            }
        });

        //调整1/2的路的长度为原来1/3
        int numOfRoadsToAdjust = roads.size() / 2;
        for (int i = 0; i < numOfRoadsToAdjust; i++) {
            Road road = roads.get(list.get(i).getKey());
            CrossRoads from = crossMap.get(road.getStart());
            CrossRoads to = crossMap.get(road.getEnd());
            DefaultWeightedEdge edge = graph.getEdge(from, to);
            graph.setEdgeWeight(edge, road.getLen() / 1.5);
            if (road.isBidirectional()) {
                DefaultWeightedEdge opposeEdge = graph.getEdge(to, from);
                graph.setEdgeWeight(opposeEdge, road.getLen() / 1.5);
            }
        }
    }

    public void addCross(CrossRoads cross) {
        crossMap.putIfAbsent(cross.getId(), cross);
    }

    public CrossRoads getCross(int crossId) {
        return crossMap.get(crossId);
    }

    public void addRoad(Road road) {
        roads.put(road.getId(), road);
    }

    public Road getRoad(int roadId) {
        return roads.get(roadId);
    }

    public void addCar(Car car) {
        cars.put(car.getId(), car);
    }

    public Car getCar(int carId) {
        return cars.get(carId);
    }

    public HashMap<Integer, Car> getCars() {
        return cars;
    }
}

