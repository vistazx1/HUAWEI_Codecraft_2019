package com.huawei;

import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.DirectedWeightedMultigraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.PriorityQueue;

public class TrafficMap {
    private Graph<CrossRoads, DefaultWeightedEdge> graph =
            new DirectedWeightedMultigraph<>(DefaultWeightedEdge.class);

    private PriorityQueue<Car> priorityQueue = new PriorityQueue<>();

    private HashMap<Integer, CrossRoads> crossMap = new HashMap<>();
    private HashMap<Integer, Road> roads = new HashMap<>();
    private HashMap<Integer, Car> cars = new HashMap<>();


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


    public void addCarPath(Car car, GraphPath path) {
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
        while (!priorityQueue.isEmpty()) {
            time++;
            count = 0;
            while (true) {
                Car car = priorityQueue.peek();
                if (car == null || car.getPlanTime() > time || count >= 11)
                    break;

                car.setStartTime(time);

                GraphPath path = shortestDistancePath(car.getFrom(), car.getTo());
                addCarPath(car, path);

                priorityQueue.remove(car);
                count++;
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


