package citibike.model;

import static java.lang.Math.PI;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import gurobi.GRB;
import gurobi.GRBEnv;
import gurobi.GRBException;
import gurobi.GRBLinExpr;
import gurobi.GRBModel;
import gurobi.GRBVar;
import javafx.util.Pair;

public class CityMap implements Cloneable, Iterable<Entry<Pair<String, String>, Double>> {

	private static final int STACK_DEPTH = (int) Math.pow(2, 20);
	private GRBEnv env;
	private Map<String, Node> nodes;
	private List<Node> stations;
	private double weighting;
	private double threshold;
	private double minlen;

	public CityMap() {
		this(PI / 2, Double.MAX_VALUE);
	}

	public CityMap(double threshold) {
		this(PI / 2, threshold);
	}

	public CityMap(double weighting, double threshold) {
		this(weighting, threshold, 0);
	}

	public CityMap(double weighting, double threshold, double minlen) {
		if (weighting < 1)
			throw new IllegalArgumentException("Weighting must be at least 1.");
		if (threshold < 0)
			throw new IllegalArgumentException("Threshold must be positive.");
		if (minlen < 0)
			throw new IllegalArgumentException("Minlen must be positive.");
		if (minlen > threshold)
			throw new IllegalArgumentException("Threshold must be greater than minlen.");
		try {
			env = new GRBEnv();
		} catch (GRBException e) {
			e.printStackTrace();
		}
		this.minlen = minlen;
		this.threshold = threshold;
		this.weighting = 1 / weighting;
		nodes = new ConcurrentHashMap<>();
		stations = new LinkedList<>();
	}

	public CityMap reset(double weighting, double threshold, double minlen) {
		CityMap n = new CityMap(weighting, threshold);
		n.nodes = new HashMap<>(nodes);
		n.stations = new LinkedList<>(stations);
		n.minlen = minlen;
		return n;
	}

	public CityMap reset(double weighting, double threshold) {
		CityMap n = new CityMap(weighting, threshold);
		n.nodes = new HashMap<>(nodes);
		n.stations = new LinkedList<>(stations);
		return n;
	}

	public CityMap reset(double threshold) {
		CityMap n = new CityMap(threshold);
		n.nodes = new HashMap<>(nodes);
		n.stations = new LinkedList<>(stations);
		return n;
	}

	public CityMap clone() {
		try {
			CityMap n = (CityMap) super.clone();
			n.nodes = new HashMap<>(nodes);
			n.stations = new LinkedList<>(stations);
			return n;
		} catch (CloneNotSupportedException e) {
			return null;
		}
	}

	public void addNode(String name, double x, double y) {
		if (nodes.containsKey(name)) {
			throw new IllegalArgumentException("Node alreaady on map.");
		}
		nodes.put(name, new Node(x, y, name));
	}

	public void addStation(String name, double x, double y) {
		if (nodes.containsKey(name)) {
			throw new IllegalArgumentException("Node alreaady on map.");
		}
		nodes.put(name, new Node(x, y, name, true));
		stations.add(nodes.get(name));
	}

	public void roadConnect(String a, String b) {
		Node a1 = Objects.requireNonNull(nodes.get(a), "Node a does not exist.");
		Node b1 = Objects.requireNonNull(nodes.get(b), "Node b does not exist.");
		a1.connect(b1);
		b1.connect(a1);
	}

	// public void roadConnect(String a, String b, double dist) {
	// Node a1 = Objects.requireNonNull(nodes.get(a), "Node a does not exist.");
	// Node b1 = Objects.requireNonNull(nodes.get(b), "Node b does not exist.");
	// a1.connect(b1, dist);
	// b1.connect(a1, dist);
	// }
	public void bikeConnect(String a, String b) {
		Node a1 = Objects.requireNonNull(nodes.get(a), "Node a does not exist.");
		Node b1 = Objects.requireNonNull(nodes.get(b), "Node b does not exist.");
		a1.bconnect(b1, weighting);
		b1.bconnect(a1, weighting);
	}

	public double distance(String a, String b) {
		Objects.requireNonNull(nodes.get(a), "Node a does not exist.");
		Objects.requireNonNull(nodes.get(b), "Node b does not exist.");
		return sqrt(pow(nodes.get(a).getX() - nodes.get(b).getX(), 2)
				+ pow(nodes.get(a).getY() - nodes.get(b).getY(), 2));
	}

	public double length(String a, String b) {
		Objects.requireNonNull(nodes.get(a), "Node a does not exist.");
		Objects.requireNonNull(nodes.get(b), "Node b does not exist.");
		LinkedList<Node> d = shortestPath(nodes.get(a), nodes.get(b));
		if (d.size() == 1)
			throw new IllegalArgumentException("No path exists.");
		return len(d);
	}

	public double weightedLengthFriendliness(String a, String b) {
		Objects.requireNonNull(nodes.get(a), "Node a does not exist.");
		Objects.requireNonNull(nodes.get(b), "Node b does not exist.");
		LinkedList<Node> d = shortestPath(nodes.get(a), nodes.get(b));
		if (d.size() == 1 || d.size() == 0) {
			//System.out.println(d);
			throw new IllegalArgumentException("No path exists.");
		}
		double dist = 0;
		double length = 0;
		Node pop = d.pop();
		while (!d.isEmpty()) {
			Node u = d.pop();
			for (Road g : pop.roads()) {
				if (g.getDestination() == u) {
					length += g.getDistance();
					if (g instanceof BikeLane) {
						dist += g.getDistance();
					}
				}
			}
			pop = u;
		}
		return dist / length;
	}

	public double weightedDistanceFriendliness(String a, String b) {
		Objects.requireNonNull(nodes.get(a), "Node a does not exist.");
		Objects.requireNonNull(nodes.get(b), "Node b does not exist.");
		LinkedList<Node> d = shortestPath0(nodes.get(a), nodes.get(b));
		if (d.size() == 1 || d.size() == 0)
			throw new IllegalArgumentException("No path exists.");
		double length = 0;
		Node pop = d.pop();
		while (!d.isEmpty()) {
			Node u = d.pop();
			for (Road g : pop.roads()) {
				if (g.getDestination() == u) {
					length += g.getDistance();
				}
			}
			pop = u;
		}
		double tau = length / weighting;
		double f = gurobiFriendliness(a, b, tau);
		return f / length(a, b);
	}

	public double kPathsFriendliness(String a, String b) {
		Objects.requireNonNull(nodes.get(a), "Node a does not exist.");
		Objects.requireNonNull(nodes.get(b), "Node b does not exist.");
		LinkedList<Node> pth = shortestPath0(nodes.get(b), nodes.get(a));
		if (pth.size() == 1 || pth.size() == 0)
			throw new IllegalArgumentException("No path exists");
		int c = (int) (1 / weighting);
		List<LinkedList<Node>> d = yenKSP(nodes.get(a), nodes.get(b), c++);
		return d.stream().map(this::fr).mapToDouble(Double::valueOf).max().getAsDouble();
	}

	public NavigableMap<Pair<String, String>, Double> computeAllLengthFriendliness() {
		TreeMap<Pair<String, String>, Double> k = new TreeMap<>(
				(a, b) -> a.getKey().compareTo(b.getKey()) != 0 ? a.getKey().compareTo(b.getKey())
						: a.getValue().compareTo(b.getValue()));
		for (int j1 = 0; j1 < stations.size(); j1++) {
			for (int i1 = j1; i1 < stations.size(); i1++) {
				try {
					Node j = stations.get(j1);
					Node i = stations.get(i1);
					if (i.getDistanceFrom(j) <= threshold)
						if (i.getDistanceFrom(j) >= minlen) {
							double f = weightedLengthFriendliness(j.getId(), i.getId());
							k.put(new Pair<>(j.getId(), i.getId()), f);
						}
				} catch (IllegalArgumentException e) {
				}
			}
		}
		return k;
	}

	public NavigableMap<Pair<String, String>, Double> computeAllKPathsFriendliness() {
		TreeMap<Pair<String, String>, Double> k = new TreeMap<>(
				(a, b) -> a.getKey().compareTo(b.getKey()) != 0 ? a.getKey().compareTo(b.getKey())
						: a.getValue().compareTo(b.getValue()));
		for (int j1 = 0; j1 < stations.size(); j1++) {
			for (int i1 = j1; i1 < stations.size(); i1++) {
				try {
					Node j = stations.get(j1);
					Node i = stations.get(i1);
					if (i.getDistanceFrom(j) <= threshold)
						if (i.getDistanceFrom(j) >= minlen) {
							double f = kPathsFriendliness(j.getId(), i.getId());
							k.put(new Pair<>(j.getId(), i.getId()), f);
						}
				} catch (IllegalArgumentException e) {
				}
			}
		}
		return k;
	}

	public NavigableMap<Pair<String, String>, Double> computeAllDistanceFriendliness() {
		TreeMap<Pair<String, String>, Double> k = new TreeMap<>(
				(a, b) -> a.getKey().compareTo(b.getKey()) != 0 ? a.getKey().compareTo(b.getKey())
						: a.getValue().compareTo(b.getValue()));
		for (int j1 = 0; j1 < stations.size(); j1++) {
			for (int i1 = j1; i1 < stations.size(); i1++) {
				try {
					Node j = stations.get(j1);
					Node i = stations.get(i1);
					if (i.getDistanceFrom(j) <= threshold)
						if (i.getDistanceFrom(j) >= minlen) {
							double f = weightedDistanceFriendliness(j.getId(), i.getId());
							k.put(new Pair<>(j.getId(), i.getId()), f);
						}
				} catch (IllegalArgumentException e) {
				}
			}
		}
		return k;
	}

	public void computeAllLengthFriendlinessConcurrently(Consumer<Double> onUpdate,
			Consumer<NavigableMap<Pair<String, String>, Double>> onFinished) {
		if (threshold == 0) {
			onFinished.accept(new TreeMap<>());
			return;
		}
		Runnable r = () -> {
			ConcurrentHashMap<Pair<String, String>, Double> h1 = new ConcurrentHashMap<>();
			TreeMap<Pair<String, String>, Double> k = new TreeMap<>(
					(a, b) -> a.getKey().compareTo(b.getKey()) != 0
							? a.getKey().compareTo(b.getKey())
							: a.getValue().compareTo(b.getValue()));
			List<Pair<String, String>> li = new LinkedList<>();
			onUpdate.accept((double) 0);
			AtomicInteger counter = new AtomicInteger(0);
			for (int j1 = 0; j1 < stations.size(); j1++) {
				for (int i1 = j1; i1 < stations.size(); i1++) {
					try {
						Node j = stations.get(j1);
						Node i = stations.get(i1);
						if (i.getDistanceFrom(j) <= threshold)
							if (i.getDistanceFrom(j) >= minlen) {
								li.add(new Pair<>(j.getId(), i.getId()));
							}
						onUpdate.accept((double) (counter.incrementAndGet())
								/ (stations.size() * stations.size() / 2) / 10);
					} catch (IllegalArgumentException e) {
					}
				}
			}
			counter.set(0);
			li.stream().unordered().parallel().forEach(l -> {
				onUpdate.accept(.1 + (double) (counter.incrementAndGet()) * .9 / li.size());
				try {
					double f = weightedLengthFriendliness(l.getKey(), l.getValue());
					h1.put(l, f);
				} catch (IllegalArgumentException e) {
				}
			});
			k.putAll(h1);
			onFinished.accept(k);
		};
		Thread t = new Thread(Thread.currentThread().getThreadGroup(), r, "" + this.hashCode(),
				STACK_DEPTH);
		t.start();
	}

	public void computeAllKPathsFriendlinessConcurrently(Consumer<Double> onUpdate,
			Consumer<NavigableMap<Pair<String, String>, Double>> onFinished) {
		if (threshold == 0) {
			onFinished.accept(new TreeMap<>());
			return;
		}
		Runnable r = () -> {
			ConcurrentHashMap<Pair<String, String>, Double> h1 = new ConcurrentHashMap<>();
			TreeMap<Pair<String, String>, Double> k = new TreeMap<>(
					(a, b) -> a.getKey().compareTo(b.getKey()) != 0
							? a.getKey().compareTo(b.getKey())
							: a.getValue().compareTo(b.getValue()));
			List<Pair<String, String>> li = new LinkedList<>();
			onUpdate.accept((double) 0);
			AtomicInteger counter = new AtomicInteger(0);
			for (int j1 = 0; j1 < stations.size(); j1++) {
				for (int i1 = j1; i1 < stations.size(); i1++) {
					try {
						Node j = stations.get(j1);
						Node i = stations.get(i1);
						if (i.getDistanceFrom(j) <= threshold)
							if (i.getDistanceFrom(j) >= minlen) {
								li.add(new Pair<>(j.getId(), i.getId()));
							}
						onUpdate.accept((double) (counter.incrementAndGet())
								/ (stations.size() * stations.size() / 2) / 10);
					} catch (IllegalArgumentException e) {
					}
				}
			}
			counter.set(0);
			li.stream().unordered().parallel().forEach(l -> {
				onUpdate.accept(.1 + (double) (counter.incrementAndGet()) * .9 / li.size());
				try {
					double f = kPathsFriendliness(l.getKey(), l.getValue());
					h1.put(l, f);
				} catch (IllegalArgumentException e) {
				}
			});
			k.putAll(h1);
			onFinished.accept(k);
		};
		Thread t = new Thread(Thread.currentThread().getThreadGroup(), r, "" + this.hashCode(),
				STACK_DEPTH);
		t.start();
	}

	public void computeAllDistanceFriendlinessConcurrently(Consumer<Double> onUpdate,
			Consumer<NavigableMap<Pair<String, String>, Double>> onFinished) {
		if (threshold == 0) {
			onFinished.accept(new TreeMap<>());
			return;
		}
		Runnable r = () -> {
			ConcurrentHashMap<Pair<String, String>, Double> h1 = new ConcurrentHashMap<>();
			TreeMap<Pair<String, String>, Double> k = new TreeMap<>(
					(a, b) -> a.getKey().compareTo(b.getKey()) != 0
							? a.getKey().compareTo(b.getKey())
							: a.getValue().compareTo(b.getValue()));
			List<Pair<String, String>> li = new LinkedList<>();
			onUpdate.accept((double) 0);
			AtomicInteger counter = new AtomicInteger(0);
			for (int j1 = 0; j1 < stations.size(); j1++) {
				for (int i1 = j1; i1 < stations.size(); i1++) {
					try {
						Node j = stations.get(j1);
						Node i = stations.get(i1);
						if (i.getDistanceFrom(j) <= threshold)
							if (i.getDistanceFrom(j) >= minlen) {
								li.add(new Pair<>(j.getId(), i.getId()));
							}
						onUpdate.accept((double) (counter.incrementAndGet())
								/ (stations.size() * stations.size() / 2) / 10);
					} catch (IllegalArgumentException e) {
					}
				}
			}
			counter.set(0);
			li.stream().unordered().parallel().forEach(l -> {
				onUpdate.accept(.1 + (double) (counter.incrementAndGet()) * .9 / li.size());
				try {
					double f = weightedDistanceFriendliness(l.getKey(), l.getValue());
					h1.put(l, f);
				} catch (IllegalArgumentException e) {
				}
			});
			k.putAll(h1);
			onFinished.accept(k);
		};
		Thread t = new Thread(Thread.currentThread().getThreadGroup(), r, "" + this.hashCode(),
				STACK_DEPTH);
		t.start();
	}

	public void computeAllDistanceFriendlinessConcurrently(List<String> use,
			Consumer<Double> onUpdate,
			Consumer<NavigableMap<Pair<String, String>, Double>> onFinished) {
		Runnable r = () -> {
			ConcurrentHashMap<Pair<String, String>, Double> h1 = new ConcurrentHashMap<>();
			TreeMap<Pair<String, String>, Double> k = new TreeMap<>(
					(a, b) -> a.getKey().compareTo(b.getKey()) != 0
							? a.getKey().compareTo(b.getKey())
							: a.getValue().compareTo(b.getValue()));
			List<Pair<String, String>> li = new LinkedList<>();
			onUpdate.accept((double) 0);
			AtomicInteger counter = new AtomicInteger(0);
			for (int j1 = 0; j1 < stations.size(); j1++) {
				for (int i1 = j1; i1 < stations.size(); i1++) {
					try {
						if (use.contains(stations.get(j1).getId() + "\0" + stations.get(i1).getId())
								|| use.contains(stations.get(i1).getId() + "\0"
										+ stations.get(j1).getId())) {
							Node j = stations.get(j1);
							Node i = stations.get(i1);
							if (i.getDistanceFrom(j) <= threshold) {
								li.add(new Pair<>(j.getId(), i.getId()));
							}
							onUpdate.accept((double) (counter.incrementAndGet())
									/ (stations.size() * stations.size() / 2) / 10);
						}
					} catch (IllegalArgumentException e) {
					}
				}
			}
			counter.set(0);
			li.stream().unordered().parallel().forEach(l -> {
				onUpdate.accept(.1 + (double) (counter.incrementAndGet()) * .9 / li.size());
				try {
					double f = weightedDistanceFriendliness(l.getKey(), l.getValue());
					h1.put(l, f);
				} catch (IllegalArgumentException e) {
				}
			});
			k.putAll(h1);
			onFinished.accept(k);
		};
		Thread t = new Thread(Thread.currentThread().getThreadGroup(), r, "" + this.hashCode(),
				STACK_DEPTH);
		t.start();
	}

	public void computeAllLengthFriendlinessConcurrently(List<String> use,
			Consumer<Double> onUpdate,
			Consumer<NavigableMap<Pair<String, String>, Double>> onFinished) {
		Runnable r = () -> {
			ConcurrentHashMap<Pair<String, String>, Double> h1 = new ConcurrentHashMap<>();
			TreeMap<Pair<String, String>, Double> k = new TreeMap<>(
					(a, b) -> a.getKey().compareTo(b.getKey()) != 0
							? a.getKey().compareTo(b.getKey())
							: a.getValue().compareTo(b.getValue()));
			List<Pair<String, String>> li = new LinkedList<>();
			onUpdate.accept((double) 0);
			AtomicInteger counter = new AtomicInteger(0);
			for (int j1 = 0; j1 < stations.size(); j1++) {
				for (int i1 = j1; i1 < stations.size(); i1++) {
					try {
						if (use.contains(stations.get(j1).getId() + "\0" + stations.get(i1).getId())
								|| use.contains(stations.get(i1).getId() + "\0"
										+ stations.get(j1).getId())) {
							Node j = stations.get(j1);
							Node i = stations.get(i1);
							if (i.getDistanceFrom(j) <= threshold) {
								li.add(new Pair<>(j.getId(), i.getId()));
							}
							onUpdate.accept((double) (counter.incrementAndGet())
									/ (stations.size() * stations.size() / 2) / 10);
						}
					} catch (IllegalArgumentException e) {
					}
				}
			}
			counter.set(0);
			li.stream().unordered().parallel().forEach(l -> {
				onUpdate.accept(.1 + (double) (counter.incrementAndGet()) * .9 / li.size());
				try {
					double f = weightedLengthFriendliness(l.getKey(), l.getValue());
					h1.put(l, f);
				} catch (IllegalArgumentException e) {
				}
			});
			k.putAll(h1);
			onFinished.accept(k);
		};
		Thread t = new Thread(Thread.currentThread().getThreadGroup(), r, "" + this.hashCode(),
				STACK_DEPTH);
		t.start();
	}

	public void computeAllKPathsFriendlinessConcurrently(List<String> use,
			Consumer<Double> onUpdate,
			Consumer<NavigableMap<Pair<String, String>, Double>> onFinished) {
		Runnable r = () -> {
			ConcurrentHashMap<Pair<String, String>, Double> h1 = new ConcurrentHashMap<>();
			TreeMap<Pair<String, String>, Double> k = new TreeMap<>(
					(a, b) -> a.getKey().compareTo(b.getKey()) != 0
							? a.getKey().compareTo(b.getKey())
							: a.getValue().compareTo(b.getValue()));
			List<Pair<String, String>> li = new LinkedList<>();
			onUpdate.accept((double) 0);
			AtomicInteger counter = new AtomicInteger(0);
			for (int j1 = 0; j1 < stations.size(); j1++) {
				for (int i1 = j1; i1 < stations.size(); i1++) {
					try {
						if (use.contains(stations.get(j1).getId() + "\0" + stations.get(i1).getId())
								|| use.contains(stations.get(i1).getId() + "\0"
										+ stations.get(j1).getId())) {
							Node j = stations.get(j1);
							Node i = stations.get(i1);
							if (i.getDistanceFrom(j) <= threshold) {
								li.add(new Pair<>(j.getId(), i.getId()));
							}
							onUpdate.accept((double) (counter.incrementAndGet())
									/ (stations.size() * stations.size() / 2) / 10);
						}
					} catch (IllegalArgumentException e) {
					}
				}
			}
			counter.set(0);
			li.stream().unordered().parallel().forEach(l -> {
				onUpdate.accept(.1 + (double) (counter.incrementAndGet()) * .9 / li.size());
				try {
					double f = kPathsFriendliness(l.getKey(), l.getValue());
					h1.put(l, f);
				} catch (IllegalArgumentException e) {
				}
			});
			k.putAll(h1);
			onFinished.accept(k);
		};
		Thread t = new Thread(Thread.currentThread().getThreadGroup(), r, "" + this.hashCode(),
				STACK_DEPTH);
		t.start();
	}

	public void optimize() {
		boolean done = false;
		while (!done) {
			done = true;
			for (Node n : nodes.values()) {
				if (!n.isStation() && n.roads().size() == 2) {
					if (n.roads().get(0).getClass().equals(n.roads().get(1).getClass())) {
						done = false;
						boolean c = n.roads().get(0) instanceof BikeLane;
						Node a = n.roads().get(0).getDestination();
						Node b = n.roads().get(1).getDestination();
						double dist = n.roads().get(0).getDistance()
								+ n.roads().get(1).getDistance();
						// System.out.println(n);
						// System.out.println(b);
						if (c) {
							a.bconnect(b, weighting, dist);
							b.bconnect(a, weighting, dist);
						} else {
							a.connect(b, dist);
							b.connect(a, dist);
						}
						// System.out.println(b.getId());
						n.disconnect(b);
						n.disconnect(a);
						a.disconnect(n);
						b.disconnect(n);
						nodes.remove(n.getId());
					}
				}
			}
		}
	}

	public List<String> getNodes() {
		return nodes.values().stream().map(Node::getId).sorted().collect(toList());
	}

	public List<String> getStations() {
		return stations.stream().map(Node::getId).sorted().collect(toList());
	}

	public List<String> getConnections(String node) {
		Objects.requireNonNull(nodes.get(node), "Node does not exist.");
		return nodes.get(node).roads().stream().map(Road::getDestination).map(Node::getId).sorted()
				.collect(toList());
	}

	public boolean contains(String node) {
		return nodes.get(node) != null;
	}

	public double getX(String node) {
		Node g = Objects.requireNonNull(nodes.get(node), "Node does not exist.");
		return g.getX();
	}

	public double getY(String node) {
		Node g = Objects.requireNonNull(nodes.get(node), "Node does not exist.");
		return g.getY();
	}

	public boolean hasBike(String node) {
		Node g = Objects.requireNonNull(nodes.get(node), "Node does not exist.");
		for (Road r : g) {
			if (r instanceof BikeLane)
				return true;
		}
		return false;
	}

	public LinkedList<String> path(String a, String b) {
		Objects.requireNonNull(nodes.get(a), "Node a does not exist.");
		Objects.requireNonNull(nodes.get(b), "Node b does not exist.");
		LinkedList<Node> path = shortestPath(nodes.get(a), nodes.get(b));
		// System.out.println(path);
		return path.stream().map(Node::getId).collect(toCollection(LinkedList::new));
	}

	public boolean isConnected(String a, String b) {
		Objects.requireNonNull(nodes.get(a), "Node a does not exist.");
		Objects.requireNonNull(nodes.get(b), "Node b does not exist.");
		if (nodes.get(a).roads().stream().map(Road::getDestination)
				.anyMatch(k -> k.equals(nodes.get(b))))
			if (nodes.get(b).roads().stream().map(Road::getDestination)
					.anyMatch(k -> k.equals(nodes.get(a)))) {
				return true;
			}
		return false;
	}

	public boolean isBike(String a, String b) {
		Objects.requireNonNull(nodes.get(a), "Node a does not exist.");
		Objects.requireNonNull(nodes.get(b), "Node b does not exist.");
		Road r = nodes.get(a).roads().stream().filter(n -> n.getDestination() == nodes.get(b))
				.findAny()
				.orElseThrow(() -> new IllegalArgumentException("Nodes are not connected."));
		return r instanceof BikeLane;
	}

	private double gurobiFriendliness(String a, String b, double tau) {
		try {
			double dist = distance(a, b);
			env.set(GRB.IntParam.OutputFlag, 0);
			GRBModel model = new GRBModel(env);
			GRBLinExpr minroad = new GRBLinExpr();
			GRBLinExpr lesstotal = new GRBLinExpr();
			Map<String, GRBVar> mp = new HashMap<>();
			for (Node t : nodes.values())
				if (distance(t.getId(), a) < dist || distance(t.getId(), b) < dist) {
					for (Road g : t.roads()) {
						Node bu = g.getDestination();
						String name = t.getId() + "\0" + bu.getId();
						GRBVar rd = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, name);
						mp.put(name, rd);
						lesstotal.addTerm(g.getDistance(), rd);
						if (!(g instanceof BikeLane)) {
							minroad.addTerm(g.getDistance(), rd);
						}
					}
				}
			for (Node t : nodes.values())
				if (distance(t.getId(), a) < dist || distance(t.getId(), b) < dist) {
					List<GRBVar> out = new ArrayList<>();
					List<GRBVar> in = new ArrayList<>();
					for (Road g : t.roads()) {
						Node bu = g.getDestination();
						if (distance(bu.getId(), a) < dist || distance(bu.getId(), b) < dist) {
							String name1 = t.getId() + "\0" + bu.getId();
							String name2 = bu.getId() + "\0" + t.getId();
							out.add(mp.get(name1));
							in.add(mp.get(name2));
						}
					}
					GRBLinExpr sum = new GRBLinExpr();
					in.forEach(k -> sum.addTerm(-1.0, k));
					out.forEach(k -> sum.addTerm(1.0, k));
					if (t.getId().equals(a)) {
						model.addConstr(sum, GRB.EQUAL, 1.0, t.getId());
					} else if (t.getId().equals(b)) {
						model.addConstr(sum, GRB.EQUAL, -1.0, t.getId());
					} else
						model.addConstr(sum, GRB.EQUAL, 0.0, t.getId());
				}
			model.setObjective(minroad, GRB.MINIMIZE);
			model.addConstr(lesstotal, GRB.LESS_EQUAL, tau, "c1");
			model.optimize();
			double all = 0;
			double bike = 0;
			// String k = "";
			// k += nodes.get(a).toString() + '\n';
			// k += nodes.get(b).toString() + '\n';
			for (Entry<String, GRBVar> e1 : mp.entrySet()) {
				if (e1.getValue().get(GRB.DoubleAttr.X) == 1) {
					// k += (e1.getKey().replace("U", "--")) + '\n';
					Node a1 = nodes.get(e1.getKey().split("\0")[0]);
					Node b1 = nodes.get(e1.getKey().split("\0")[1]);
					Road ro = a1.roads().stream().filter(r -> r.getDestination() == b1).findFirst()
							.get();
					all += ro.getDistance();
					if (ro instanceof BikeLane) {
						bike += ro.getDistance();
					}
				}
			}
			// System.out.println(k);
			// System.out.println();
			model.dispose();
			// env.dispose();
			return (bike / all) == Double.NaN ? 0 : bike / all;
		} catch (GRBException e) {
			// e.printStackTrace();
			throw new IllegalArgumentException("No valid path found.");
		}
	}

	@SuppressWarnings("unused")
	private LinkedList<Node> gShortestPath(String a, String b) {
		return gShortestPathBase(a, b, Road::getWeight);
	}

	@SuppressWarnings("unused")
	private LinkedList<Node> gShortestPath0(String a, String b) {
		return gShortestPathBase(a, b, Road::getDistance);
	}

	private LinkedList<Node> gShortestPathBase(String a, String b, Function<Road, Double> wt) {
		try {
			env.set(GRB.IntParam.OutputFlag, 0);
			GRBModel model = new GRBModel(env);
			GRBLinExpr minroad = new GRBLinExpr();
			Map<String, GRBVar> mp = new HashMap<>();
			for (Node t : nodes.values()) {
				for (Road g : t.roads()) {
					Node bu = g.getDestination();
					String name = t.getId() + "\0" + bu.getId();
					GRBVar rd = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, name);
					mp.put(name, rd);
					minroad.addTerm(wt.apply(g), rd);
				}
			}
			for (Node t : nodes.values()) {
				List<GRBVar> out = new ArrayList<>();
				List<GRBVar> in = new ArrayList<>();
				for (Road g : t.roads()) {
					Node bu = g.getDestination();
					String name1 = t.getId() + "\0" + bu.getId();
					String name2 = bu.getId() + "\0" + t.getId();
					out.add(mp.get(name1));
					in.add(mp.get(name2));
				}
				GRBLinExpr sum = new GRBLinExpr();
				in.forEach(k -> sum.addTerm(-1.0, k));
				out.forEach(k -> sum.addTerm(1.0, k));
				if (t.getId().equals(a)) {
					model.addConstr(sum, GRB.EQUAL, 1.0, t.getId());
				} else if (t.getId().equals(b)) {
					model.addConstr(sum, GRB.EQUAL, -1.0, t.getId());
				} else
					model.addConstr(sum, GRB.EQUAL, 0.0, t.getId());
			}
			model.setObjective(minroad, GRB.MINIMIZE);
			model.optimize();
			// String k = "";
			// k += nodes.get(a).toString() + '\n';
			// k += nodes.get(b).toString() + '\n';
			Function<String, String> search = s -> {
				for (Entry<String, GRBVar> e1 : mp.entrySet()) {
					try {
						if (s.split("\0")[1].equals(e1.getKey().split("\0")[0])
								&& e1.getValue().get(GRB.DoubleAttr.X) == 1) {
							return e1.getKey();
						}
					} catch (GRBException e) {
						e.printStackTrace();
					}
				}
				return null;
			};
			LinkedList<Node> pth = new LinkedList<>();
			for (Entry<String, GRBVar> e1 : mp.entrySet()) {
				if (e1.getKey().split("\0")[0].equals(a)
						&& e1.getValue().get(GRB.DoubleAttr.X) == 1) {
					String st = e1.getKey();
					while (st != null) {
						if (!pth.isEmpty())
							pth.removeLast();
						pth.add(nodes.get(st.split("\0")[0]));
						pth.add(nodes.get(st.split("\0")[1]));
						st = search.apply(st);
					}
				}
			}
			// System.out.println(k);
			// System.out.println();
			model.dispose();
			// env.dispose();
			return pth;
		} catch (GRBException e) {
			// e.printStackTrace();
			throw new IllegalArgumentException("No valid path found.");
		}
	}

	private double len(LinkedList<Node> d) {
		double length = 0;
		Iterator<Node> itr = d.iterator();
		Node pop = itr.next();
		while (itr.hasNext()) {
			Node u = itr.next();
			for (Road g : pop.roads()) {
				if (g.getDestination() == u) {
					length += g.getDistance();
				}
			}
			pop = u;
		}
		return length;
	}

	private double fr(LinkedList<Node> d) {
		double length = 0;
		double b = 0;
		Iterator<Node> itr = d.iterator();
		Node pop = itr.next();
		while (itr.hasNext()) {
			Node u = itr.next();
			for (Road g : pop.roads()) {
				if (g.getDestination() == u) {
					length += g.getDistance();
					if (g instanceof BikeLane)
						b += g.getDistance();
				}
			}
			pop = u;
		}
		return b / length;
	}

	private LinkedList<Node> nsp(Node destination, Node location, Function<Road, Double> wt,
			Set<Road> rem0, Set<Node> rem1) {
		if (!destination.isStation() || !location.isStation()) {
			throw new IllegalArgumentException("Stations must be supplied as arguments.");
		}
		TreeSet<DVertex> q = new TreeSet<>((a, b) -> Double.compare(a.weight, b.weight) == 0
				? a.hashCode() - b.hashCode() : Double.compare(a.weight, b.weight));
		Map<Node, DVertex> mp = new HashMap<>();
		for (Node n : nodes.values())
			if (!rem1.contains(n)) {
				DVertex d = new DVertex();
				d.prev = null;
				d.value = n;
				d.weight = Double.MAX_VALUE;
				mp.put(n, d);
				q.add(d);
			}
		mp.get(location).weight = 0;
		q.remove(mp.get(location));
		q.add(mp.get(location));
		while (!q.isEmpty()) {
			DVertex u = q.pollFirst();
			if (u.value == destination) {
				LinkedList<Node> li = new LinkedList<>();
				while (u.prev != null) {
					li.addFirst(u.value);
					u = u.prev;
				}
				li.addFirst(u.value);
				return li;
			}
			for (Road d0 : u.value)
				if (!rem0.contains(d0) && !rem1.contains(d0.getDestination())) {
					double len = wt.apply(d0);
					DVertex v = mp.get(d0.getDestination());
					double alt = u.weight + len;
					if (alt < v.weight) {
						v.weight = alt;
						v.prev = u;
						q.remove(v);
						q.add(v);
					}
				}
		}
		throw new IllegalArgumentException(
				"No path found between " + location.getId() + " and " + destination.getId() + ".");
	}

	private List<LinkedList<Node>> yenKSP(Node source, Node sink, int K) {
		List<LinkedList<Node>> a = new ArrayList<>();
		List<Road> rem = new ArrayList<>();
		List<Node> rem1 = new ArrayList<>();
		a.add(shortestPath1(sink, source, rem, rem1));
		NavigableMap<Double, LinkedList<Node>> b = new TreeMap<>();
		for (int k = 1; k <= K; k++) {
			for (int i = 0; i < a.get(k - 1).size(); i++) {
				Node spurNode = a.get(k - 1).get(i);
				List<Node> rootPath = a.get(k - 1).subList(0, i + 1);
				for (List<Node> p : a) {
					if (p.size() >= i + 1 && rootPath.equals(p.subList(0, i + 1))) {
						try {
							Node a0 = p.get(i);
							Node b0 = p.get(i + 1);
							a0.roads().stream().filter(v -> v.getDestination() == b0)
									.forEach(rem::add);
						} catch (Exception e) {
						}
					}
				}
				for (Node n : rootPath)
					if (n != spurNode) {
						rem1.add(n);
					}
				try {
					List<Node> spurPath = shortestPath1(sink, spurNode, rem, rem1);
					LinkedList<Node> temp = new LinkedList<>();
					temp.addAll(rootPath);
					temp.addAll(spurPath);
					b.put(len(temp), temp);
				} catch (Exception e) {
				}
				rem.clear();
				rem1.clear();
			}
			if (b.size() == 0)
				break;
			a.sort((q, x) -> Double.compare(len(q), len(x)));
			a.add(k, b.pollFirstEntry().getValue());
		}
		return a;
	}

	private LinkedList<Node> shortestPath(Node destination, Node location) {
		return nsp(destination, location, Road::getWeight, new HashSet<>(), new HashSet<>());
	}

	private LinkedList<Node> shortestPath1(Node destination, Node location, List<Road> rem,
			List<Node> rem1) {
		return nsp(destination, location, Road::getDistance, new HashSet<>(rem),
				new HashSet<>(rem1));
	}

	private LinkedList<Node> shortestPath0(Node destination, Node location) {
		return nsp(destination, location, Road::getDistance, new HashSet<>(), new HashSet<>());
	}

	private DVertex traverse(DVertex c, Node d, Map<Node, DVertex> frontier, Set<Node> visited) {
		DVertex ret = null;
		if (c.value == d) {
			ret = c;
		} else {
			frontier.remove(c.value);
			visited.add(c.value);
			for (Road r : c.value) {
				if (frontier.containsKey(r.getDestination())) {
					DVertex p = frontier.get(r.getDestination());
					if (p.weight > c.weight + r.getWeight()) {
						p.weight = c.weight + r.getWeight();
						p.prev = c;
					}
				} else if (!visited.contains(r.getDestination())) {
					DVertex p = new DVertex();
					frontier.put(r.getDestination(), p);
					p.value = r.getDestination();
					p.weight = c.weight + r.getWeight();
					p.prev = c;
				}
			}
			if (frontier.values().size() > 0) {
				DVertex next = frontier.values().stream()
						.min((a, b) -> a.weight - b.weight < 0 ? -1 : 1).get();
				ret = traverse(next, d, frontier, visited);
			}
		}
		return ret;
	}

	@SuppressWarnings("unused")
	private DVertex traverse0(DVertex c, Node d, Map<Node, DVertex> frontier, Set<Node> visited) {
		if (c.value == d) {
			return c;
		}
		frontier.remove(c.value);
		visited.add(c.value);
		for (Road r : c.value) {
			if (frontier.containsKey(r.getDestination())) {
				DVertex p = frontier.get(r.getDestination());
				if (p.weight > c.weight + r.getDistance()) {
					p.weight = c.weight + r.getDistance();
					p.prev = c;
				}
			} else if (!visited.contains(r.getDestination())) {
				DVertex p = new DVertex();
				frontier.put(r.getDestination(), p);
				p.value = r.getDestination();
				p.weight = c.weight + r.getDistance();
				p.prev = c;
			}
		}
		if (frontier.values().size() > 0) {
			DVertex next = frontier.values().stream()
					.min((a, b) -> a.weight - b.weight < 0 ? -1 : 1).get();
			return traverse(next, d, frontier, visited);
		} else {
			return null;
		}
	}

	@SuppressWarnings("unused")
	private DVertex traverse2(DVertex c, Node d, Map<Node, DVertex> frontier, Set<Node> visited,
			List<Road> rem, List<Node> rem1) {
		if (c.value == d) {
			return c;
		}
		frontier.remove(c.value);
		visited.add(c.value);
		for (Road r : c.value)
			if (!rem.contains(r) && !rem1.contains(r.getDestination())) {
				if (frontier.containsKey(r.getDestination())) {
					DVertex p = frontier.get(r.getDestination());
					if (p.weight > c.weight + r.getDistance()) {
						p.weight = c.weight + r.getDistance();
						p.prev = c;
					}
				} else if (!visited.contains(r.getDestination())) {
					DVertex p = new DVertex();
					frontier.put(r.getDestination(), p);
					p.value = r.getDestination();
					p.weight = c.weight + r.getDistance();
					p.prev = c;
				}
			}
		if (frontier.values().size() > 0) {
			DVertex next = frontier.values().stream()
					.min((a, b) -> a.weight - b.weight < 0 ? -1 : 1).get();
			return traverse(next, d, frontier, visited);
		} else {
			return null;
		}
	}

	@SuppressWarnings({ "unchecked", "unused" })
	private void traverse1(Node node, double i, double j, List<Double> li, HashSet<Node> vi,
			Node dest, double thresh) {
		if (i > thresh)
			return;
		if (vi.contains(node))
			return;
		vi = (HashSet<Node>) vi.clone();
		vi.add(node);
		if (node == dest) {
			li.add(j / i);
		} else
			for (Road r : node.roads()) {
				double i0 = i + r.getDistance();
				double j0 = j + (r instanceof BikeLane ? r.getDistance() : 0);
				traverse1(r.getDestination(), i0, j0, li, vi, dest, thresh);
			}
	}

	private class DVertex {

		double weight = 0;
		Node value = null;
		DVertex prev = null;
	}

	public String toString() {
		return "CityMap " + hashCode() + ": \nnodes="
				+ nodes.values().stream().sorted(Comparator.comparing(Node::getId))
						.collect(toList())
				+ "\nstations=" + stations.stream().map(Node::getId).sorted().collect(toList())
				+ "\nweighting=" + 1 / weighting + "\nthreshold=" + threshold;
	}

	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((nodes == null) ? 0 : nodes.hashCode());
		result = prime * result + ((stations == null) ? 0 : stations.hashCode());
		long temp;
		temp = Double.doubleToLongBits(threshold);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		temp = Double.doubleToLongBits(weighting);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		return result;
	}

	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CityMap other = (CityMap) obj;
		if (nodes == null) {
			if (other.nodes != null)
				return false;
		} else if (!nodes.equals(other.nodes))
			return false;
		if (stations == null) {
			if (other.stations != null)
				return false;
		} else if (!stations.equals(other.stations))
			return false;
		return Double.doubleToLongBits(threshold) == Double.doubleToLongBits(other.threshold) && Double.doubleToLongBits(weighting) == Double.doubleToLongBits(other.weighting);
	}

	public Iterator<Entry<Pair<String, String>, Double>> iterator() {
		return computeAllLengthFriendliness().entrySet().iterator();
	}

	public void remove(String node) {
		Objects.requireNonNull(nodes.get(node), "Node does not exist.");
		nodes.get(node).roads().stream().map(Road::getDestination).forEach(n -> {
			n.disconnect(nodes.get(node));
			nodes.get(node).disconnect(n);
		});
		nodes.remove(node);
		if (stations.contains(node))
			stations.remove(node);
	}
}
