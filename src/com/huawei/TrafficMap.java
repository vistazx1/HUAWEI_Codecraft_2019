package com.huawei;

import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;


public class TrafficMap {
    private Graph<CrossRoads, RoadEdge> graph =
            new SimpleDirectedWeightedGraph<>(RoadEdge.class);

    public static final int DIRECTION = 3;
    // Direction = 1 north south
    // Direction = 2 east west
    // Direction = 3 both

    public TrafficMap(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    private Scheduler scheduler;

    DijkstraShortestPath dijkstraShortestPath = new DijkstraShortestPath(graph);

    private PriorityQueue<Car> priorityQueue = new PriorityQueue<>();
    private ArrayList<Car> carOrderByStartList = new ArrayList<>();

    private HashMap<Integer, CrossRoads> crossMap = new HashMap<>();
    private HashMap<Integer, Road> roads = new HashMap<>();
    private HashMap<Integer, Car> cars = new HashMap<>();

    public void initGraphEdge() {

        crossMap.forEach((cross, crossObj) -> graph.addVertex(crossObj));
        roads.forEach((roadId, road) -> {
            CrossRoads from = crossMap.get(road.getStart());
            CrossRoads to = crossMap.get(road.getEnd());
            graph.removeAllEdges(from, to);

            RoadEdge roadEdge = new RoadEdge(road, from, to);
            graph.setEdgeWeight(roadEdge, roadEdge.road.getLen());
            graph.addEdge(from, to, roadEdge);

            if (road.isBidirectional()) {
                graph.removeAllEdges(to, from);
                RoadEdge roadEdge1 = new RoadEdge(road, to, from);
                graph.setEdgeWeight(roadEdge1, roadEdge1.road.getLen());
                graph.addEdge(to, from, roadEdge1);
            }
        });
    }

    public void initGraphEdge(double[] weights) {
        crossMap.forEach((cross, crossObj) -> graph.addVertex(crossObj));
        Iterator<Integer> it = roads.keySet().iterator();
        int i = 0;
        while (it.hasNext()) {
            Integer roadId = it.next();
            Road road = roads.get(roadId);
            CrossRoads from = crossMap.get(road.getStart());
            CrossRoads to = crossMap.get(road.getEnd());
            graph.removeAllEdges(from, to);

            RoadEdge roadEdge = new RoadEdge(road, from, to);
            graph.setEdgeWeight(roadEdge, weights[i]);
            graph.addEdge(from, to, roadEdge);

            if (road.isBidirectional()) {
                graph.removeAllEdges(to, from);
                RoadEdge roadEdge1 = new RoadEdge(road, to, from);
                graph.setEdgeWeight(roadEdge1, weights[i]);
                graph.addEdge(to, from, roadEdge1);
            }
            i++;
        }
    }

    public double[] readGraphEdgeWeight() {
        crossMap.forEach((cross, crossObj) -> graph.addVertex(crossObj));
        double[] weights = new double[roads.size()];

        Iterator<Integer> it = roads.keySet().iterator();
        int i = 0;
        while (it.hasNext()) {
            Integer roadId = it.next();
            Road road = roads.get(roadId);
            CrossRoads from = crossMap.get(road.getStart());
            CrossRoads to = crossMap.get(road.getEnd());
            weights[i] = graph.getEdgeWeight(graph.getEdge(from, to));

            i++;
        }
        return weights;
    }


    public void initGraphEdgeBySpeed() {

        crossMap.forEach((cross, crossObj) -> graph.addVertex(crossObj));
        roads.forEach((roadId, road) -> {
            CrossRoads from = crossMap.get(road.getStart());
            CrossRoads to = crossMap.get(road.getEnd());
            graph.removeAllEdges(from, to);

            RoadEdge roadEdge = new RoadEdge(road, from, to);
            graph.setEdgeWeight(roadEdge, roadEdge.road.getLen() / roadEdge.road.getTopSpeed());
            graph.addEdge(from, to, roadEdge);

            if (road.isBidirectional()) {
                graph.removeAllEdges(to, from);
                RoadEdge roadEdge1 = new RoadEdge(road, to, from);
                graph.setEdgeWeight(roadEdge1, roadEdge1.road.getLen() / roadEdge.road.getTopSpeed());
                graph.addEdge(to, from, roadEdge1);
            }
        });
    }

    public void updateGraphEdge() {
        crossMap.forEach((cross, crossObj) -> graph.addVertex(crossObj));
        roads.forEach((roadId, road) -> {
            CrossRoads from = crossMap.get(road.getStart());
            CrossRoads to = crossMap.get(road.getEnd());
            RoadEdge roadEdge = graph.getEdge(from, to);
            graph.setEdgeWeight(roadEdge, roadEdge.getWeight(graph.getEdgeWeight(roadEdge)));
            if (road.isBidirectional()) {
                RoadEdge roadEdge1 = graph.getEdge(to, from);
                graph.setEdgeWeight(roadEdge1, roadEdge1.getWeight(graph.getEdgeWeight(roadEdge1)));
            }
        });
    }


    public GraphPath shortestDistancePath(Graph graphToCompute, int from, int to) {
        return DijkstraShortestPath.findPathBetween(graphToCompute, crossMap.get(from), crossMap.get(to));
    }


    public void setCarPath(Car car, GraphPath path) {
        // Clean original path
        car.getPath().clear();
        //Find the road between two crossroads
        path.getEdgeList().forEach(edge -> {
            car.addPath(((RoadEdge) edge).road.getId());
        });
    }


    public Long scheduleTest(int carFlowLimit) {
        scheduler.reset();
        updateGraphEdge();

        HashMap<Integer, Integer> carPlanTime = new HashMap<>();


        // 先把优先车辆放入车库
        cars.forEach((carId,car)->{
            if(car.isPreset()){
                scheduler.addToGarage(car);
            }
        });

        cars.forEach((carId, car) -> {
            carPlanTime.put(carId, car.getPlanTime());
        });

        int time = 0;
        int count = 0;

        HashMap<Integer, PriorityQueue<Car>> carDirection = directionClassification(DIRECTION);

        for (int i = 0; i <= 1; i++) {

            PriorityQueue<Car> carQueue = carDirection.get(i);

            time += 2;

            while (!carQueue.isEmpty()) {
                time++;
                count = 0;

                while (true) {
                    Car car = carQueue.peek();
                    if (car == null || carPlanTime.get(car.getId()) > time || count >= carFlowLimit)
                        break;

                    GraphPath path = shortestDistancePath(graph, car.getFrom(), car.getTo());
                    setCarPath(car, path);

                    boolean hasBusyPath = false;
                    for (Object edge : path.getEdgeList()) {
                        if (((RoadEdge) edge).calculateLoad() > 0.9) {
                            hasBusyPath = true;
                            break;
                        }
                    }
                    if (hasBusyPath) {
                        carPlanTime.put(car.getId(), carPlanTime.get(car.getId()) + 1);
                        continue;
                    }
//                    for (int roadId : car.getPath()) {
//                        if (roads.get(roadId).calculateLoad() > 0.90) {
//                            hasBusyPath = true;
//                        }
//                    }


                    car.setStartTime(time);
                    scheduler.addToGarage(car);
                    carQueue.remove(car);
                    count++;

                }
                if (!scheduler.step())
                    return -1L;
                updateGraphEdge();
            }

        }


        if (!scheduler.stepUntilFinish())
            return -1L;
        scheduler.printCarStates();
        return scheduler.getSystemScheduleTime();
    }


    public Long scheduleTest2(int carFlowLimit) {
        scheduler.reset();
        updateGraphEdge();

        HashMap<Integer, Integer> carPlanTime = new HashMap<>();


        cars.forEach((carId, car) -> {
            carPlanTime.put(carId, car.getPlanTime());
        });

        int time = 0;
        int count = 0;
        int dynamicFlow = carFlowLimit;

        HashMap<Integer, PriorityQueue<Car>> carDirection = directionClassification(DIRECTION);

        for (int i = 0; i <= 1; i++) {

            PriorityQueue<Car> carQueue = carDirection.get(i);

            time += 2;

            while (!carQueue.isEmpty()) {
                time++;
                count = 0;

                boolean exit = false;
                while (!exit) {

                    System.out.println("Time: " + time + " Trying dynamicflow " + dynamicFlow);
                    scheduler.saveSchedulerState(time);
                    while (true) {
                        Car car = carQueue.peek();
                        if (car == null || carPlanTime.get(car.getId()) > time || count >= dynamicFlow) {
                            exit = true;
                            break;
                        }

                        GraphPath path = shortestDistancePath(graph, car.getFrom(), car.getTo());
                        setCarPath(car, path);

                        boolean hasBusyPath = false;
                        for (Object edge : path.getEdgeList()) {
                            if (((RoadEdge) edge).calculateLoad() > 0.9) {
                                hasBusyPath = true;
                                break;
                            }
                        }
                        if (hasBusyPath) {
                            carPlanTime.put(car.getId(), carPlanTime.get(car.getId()) + 1);
                            continue;
                        }
//                    for (int roadId : car.getPath()) {
//                        if (roads.get(roadId).calculateLoad() > 0.90) {
//                            hasBusyPath = true;
//                        }
//                    }


                        car.setStartTime(time).setState(CarState.IN_GARAGE);
                        scheduler.addToGarage(car);
                        carQueue.remove(car);
                        count++;

                    }

                    if (!scheduler.stepUntilFinish()) {
                        exit = true;
                        scheduler.restoreSchedulerState(time);
                        System.err.println("Dead lock happened, restore state.");
                        dynamicFlow -= 2;
                    }
                    dynamicFlow++;
                }
                if (!scheduler.step())
                    return -1L;
                updateGraphEdge();
            }

        }


        if (!scheduler.stepUntilFinish())
            return -1L;
        scheduler.printCarStates();
        return scheduler.getSystemScheduleTime();
    }



    public void pathClassification() {
        priorityQueue.clear();

        HashMap<Integer, ConcurrentLinkedQueue<Car>> crossHashMap = new HashMap<>();

        for (CrossRoads cross : crossMap.values()) {
            crossHashMap.put(cross.getId(), new ConcurrentLinkedQueue<>());
        }

        this.getCars().forEach(
                (carId, car) -> priorityQueue.offer(car)
        );

        while (!priorityQueue.isEmpty()) {
            Car car = priorityQueue.remove();
            crossHashMap.get(car.getFrom()).offer(car);
        }

        ConcurrentLinkedQueue<Car> carQueue = new ConcurrentLinkedQueue<>();
        while (true) {
            boolean empty = true;
            PriorityQueue<Car> temp = new PriorityQueue<>();
            for (CrossRoads cross : crossMap.values()) {

                if (crossHashMap.get(cross.getId()).isEmpty())
                    continue;
                Car car = crossHashMap.get(cross.getId()).remove();
                if (car != null) {
                    empty = false;
                    temp.offer(car);
                }
            }
            while (!temp.isEmpty())
                carOrderByStartList.add(temp.remove());

            if (empty)
                break;
        }

    }

    public HashMap<Integer, PriorityQueue<Car>> directionClassification(int direction) {
        // Direction = 1 north south
        // Direction = 2 east west
        // Direction = 3 both

        priorityQueue.clear();

        // 0 is north, 1 is south
        HashMap<Integer, PriorityQueue<Car>> directionMap = new HashMap<>();

        directionMap.put(1, new PriorityQueue<>());
        directionMap.put(0, new PriorityQueue<>());

        this.getCars().forEach(
                (carId, car) -> priorityQueue.offer(car)
        );

        while (!priorityQueue.isEmpty()) {
            Car car = priorityQueue.remove();
            GraphPath<CrossRoads, RoadEdge> path = shortestDistancePath(graph, car.getFrom(), car.getTo());

            double directionSum = 0;
            for (RoadEdge roadEdge : path.getEdgeList()) {
                CrossRoads.RoadPosition roadPosition = roadEdge.getSource().getRoadDirection().get(roadEdge.road.getId());
                if (roadPosition == CrossRoads.RoadPosition.NORTH && direction == 1 || direction == 3) {
                    directionSum += roadEdge.road.getLen();
                } else if (roadPosition == CrossRoads.RoadPosition.SOUTH && direction == 1 || direction == 3)
                    directionSum -= roadEdge.road.getLen();
                else if (roadPosition == CrossRoads.RoadPosition.EAST && direction == 2 || direction == 3) {
                    directionSum += roadEdge.road.getLen();
                } else if (roadPosition == CrossRoads.RoadPosition.WEST && direction == 2 || direction == 3)
                    directionSum -= roadEdge.road.getLen();
            }
            if (directionSum <= 0)
                // D1
                directionMap.get(1).offer(car);
            else
                // D2
                directionMap.get(0).offer(car);

        }
        return directionMap;

    }

    public ArrayList<Car> pathLengthClassification() {
        priorityQueue.clear();

        this.getCars().forEach(
                (carId, car) -> priorityQueue.offer(car)
        );
        ArrayList<Car> carList = new ArrayList<>();

        carList.addAll(cars.values());

        carList.sort(new Comparator<Car>() {
            @Override
            public int compare(Car car1, Car car2) {
                return shortestDistancePath(graph, car1.getFrom(), car1.getTo()).getLength() - shortestDistancePath(graph, car2.getFrom(), car2.getTo()).getLength();
            }
        });

        return carList;

    }

    public long preSchedule(int max_car_limit) {
        scheduler.reset();

//        ConcurrentLinkedQueue<Car> carQueue = new ConcurrentLinkedQueue<>();
//        carQueue.addAll(carOrderByStartList);
//

        priorityQueue.clear();
        this.getCars().forEach(
                (carId, car) -> priorityQueue.offer(car)
        );

        PriorityQueue<Car> carQueue = priorityQueue;

        //Busy path counter
        TreeMap<Integer, Integer> roadCounter = new TreeMap<>();

        int time = 0;
        int count = 0;
        while (!carQueue.isEmpty()) {
            time++;
            count = 0;
            while (true) {
                Car car = carQueue.peek();
                if (car == null || car.getPlanTime() > time || count >= max_car_limit)
                    break;


                GraphPath path = shortestDistancePath(graph, car.getFrom(), car.getTo());
                setCarPath(car, path);

                // 计算每一时间单位最忙的路
                car.getPath().forEach(road -> {
                    if (roads.get(road).calculateLoad() > 0.50) {
                        if (roadCounter.containsKey(road))
                            roadCounter.put(road, roadCounter.get(road) + 1);
                        else
                            roadCounter.put(road, 1);
                    }
                });
                car.setStartTime(time).setState(CarState.IN_GARAGE);
                scheduler.addToGarage(car);
                carQueue.remove(car);

                count++;
            }
            if (!scheduler.step())
                return -1;
        }
        if (!scheduler.stepUntilFinish())
            return -1;
        scheduler.printCarStates();

        List<Map.Entry<Integer, Integer>> list = new ArrayList<>(roadCounter.entrySet());

        Collections.sort(list, new Comparator<Map.Entry<Integer, Integer>>() {
            public int compare(Map.Entry<Integer, Integer> o1, Map.Entry<Integer, Integer> o2) {
                return o2.getValue().compareTo(o1.getValue());
            }
        });

        int numOfRoadsToAdjust = roadCounter.size();
        for (int i = 0; i < numOfRoadsToAdjust; i++) {
            Road road = roads.get(list.get(i).getKey());
            CrossRoads from = crossMap.get(road.getStart());
            CrossRoads to = crossMap.get(road.getEnd());
            RoadEdge edge = graph.getEdge(from, to);
            graph.setEdgeWeight(edge, road.getLen() * 2.0);
            if (road.isBidirectional()) {
                RoadEdge opposeEdge = graph.getEdge(to, from);
                graph.setEdgeWeight(opposeEdge, road.getLen() * 2.0);
            }
        }

        return Scheduler.systemScheduleTime;
    }

    public long preScheduleDirection(int max_car_limit) {
        scheduler.reset();


        HashMap<Integer, PriorityQueue<Car>> carDirection = directionClassification(DIRECTION);


        //Busy path counter
        TreeMap<Integer, Integer> roadCounter = new TreeMap<>();

        int time = 0;
        int count = 0;
        for (int i = 0; i <= 1; i++) {

            PriorityQueue<Car> carQueue = carDirection.get(i);
            while (!carQueue.isEmpty()) {
                time++;
                count = 0;
                while (true) {
                    Car car = carQueue.peek();
                    if (car == null || car.getPlanTime() > time || count >= max_car_limit)
                        break;


                    GraphPath path = shortestDistancePath(graph, car.getFrom(), car.getTo());
                    setCarPath(car, path);

                    // 计算每一时间单位最忙的路
                    car.getPath().forEach(road -> {
                        if (roads.get(road).calculateLoad() > 0.5) {
                            if (roadCounter.containsKey(road))
                                roadCounter.put(road, roadCounter.get(road) + 1);
                            else
                                roadCounter.put(road, 1);
                        }
                    });
                    car.setStartTime(time).setState(CarState.IN_GARAGE);
                    scheduler.addToGarage(car);
                    carQueue.remove(car);

                    count++;
                }
                if (!scheduler.step())
                    return -1;
            }
        }
        if (!scheduler.stepUntilFinish())
            return -1;
        scheduler.printCarStates();

        List<Map.Entry<Integer, Integer>> list = new ArrayList<>(roadCounter.entrySet());

        Collections.sort(list, new Comparator<Map.Entry<Integer, Integer>>() {
            public int compare(Map.Entry<Integer, Integer> o1, Map.Entry<Integer, Integer> o2) {
                return o2.getValue().compareTo(o1.getValue());
            }
        });

        int numOfRoadsToAdjust = roadCounter.size();
        for (int i = 0; i < numOfRoadsToAdjust; i++) {
            Road road = roads.get(list.get(i).getKey());
            CrossRoads from = crossMap.get(road.getStart());
            CrossRoads to = crossMap.get(road.getEnd());
            RoadEdge edge = graph.getEdge(from, to);
            graph.setEdgeWeight(edge, road.getLen() * 6.5);
            if (road.isBidirectional()) {
                RoadEdge opposeEdge = graph.getEdge(to, from);
                graph.setEdgeWeight(opposeEdge, road.getLen() * 6.5);
            }
        }

        return Scheduler.systemScheduleTime;
    }

    public long preScheduleAccurateTest(int carFlowLimit) {
        scheduler.reset();

        HashMap<Integer, PriorityQueue<Car>> carDirection = directionClassification(DIRECTION);


        //Busy path recorder
        ArrayList<RoadEdge> busyEdges = new ArrayList<>();

        //  出发时间
        int time = 0;
        //　车流计数器
        int count = 0;

        ArrayList<Car> carNotSent = new ArrayList<>();

        for (int i = 0; i <= 1; i++) {
            // 获取一个分类
            PriorityQueue<Car> carQueue = carDirection.get(i);

            //　暂停两个时间片，让反向车流先走再发车
            time += 2;

            while (!carQueue.isEmpty()) {
                time++;
                count = 0;
                carNotSent.clear();

                while (true) {

                    Car car = carQueue.peek();
                    if (car == null || count >= carFlowLimit)
                        break;

                    if (car.getPlanTime() > time) {
                        carNotSent.add(car);
                        carQueue.remove(car);
                        continue;
                    }

                    GraphPath path = shortestDistancePath(graph, car.getFrom(), car.getTo());
                    setCarPath(car, path);

                    for (Object edge : path.getEdgeList()) {
                        if (((RoadEdge) edge).calculateLoad() > 0.50) {
                            busyEdges.add(((RoadEdge) edge));
                            break;
                        }
                    }


                    car.setStartTime(time).setState(CarState.IN_GARAGE);
                    scheduler.addToGarage(car);
                    carQueue.remove(car);
                    count++;
                }


                carQueue.addAll(carNotSent);
                if (!scheduler.step()) {
                    return -1L;
                }
            }
        }
        if (!scheduler.stepUntilFinish())
            return -1;
        scheduler.printCarStates();

        for (RoadEdge edge : busyEdges) {
            graph.setEdgeWeight(edge, edge.road.getLen() * 1.5);
        }

        return Scheduler.systemScheduleTime;
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

    public Graph getGraph() {
        return graph;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public HashMap<Integer, Road> getRoads() {
        return roads;
    }
}


