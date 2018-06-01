package citibike.model;

import static java.lang.Math.PI;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javafx.util.Pair;

public class CityMap implements Cloneable, Iterable<Entry<Pair<String, String>, Double>> {

	private Map<String, Node> nodes;
	private List<Node> stations;
	private double weighting;
	private double threshold;

	public CityMap() {
		this(PI / 2, Double.MAX_VALUE);
	}

	public CityMap(double threshold) {
		this(PI / 2, threshold);
	}

	public CityMap(double weighting, double threshold) {
		if (weighting < 1)
			throw new IllegalArgumentException("Weighting must be at least 1.");
		if (threshold < 0)
			throw new IllegalArgumentException("Threshold must be positive.");
		this.threshold = threshold;
		this.weighting = 1 / weighting;
		nodes = new ConcurrentHashMap<>();
		stations = new LinkedList<>();
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
		Deque<Node> d = shortestPath(nodes.get(a), nodes.get(b));
		if (d.size() == 1)
			throw new IllegalArgumentException("No path exists.");
		double dist = 0;
		Node pop = d.pop();
		while (!d.isEmpty()) {
			Node u = d.pop();
			for (Road g : pop.roads()) {
				if (g.getDestination() == u) {
					dist += g.getDistance();
				}
			}
			pop = u;
		}
		return dist;
	}

	public double weightedLengthFriendliness(String a, String b) {
		Objects.requireNonNull(nodes.get(a), "Node a does not exist.");
		Objects.requireNonNull(nodes.get(b), "Node b does not exist.");
		Deque<Node> d = shortestPath(nodes.get(a), nodes.get(b));
		if (d.size() == 1)
			throw new IllegalArgumentException("No path exists.");
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
		Deque<Node> d = shortestPath0(nodes.get(a), nodes.get(b));
		if (d.size() == 1)
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
		List<Double> li = new ArrayList<Double>();
		HashSet<Node> vi = new HashSet<Node>();
		traverse1(nodes.get(a), 0, 0, li, vi, nodes.get(b), length / weighting);
		if (li.size() == 0)
			throw new IllegalArgumentException("Path is too complicated.");
		return li.stream().mapToDouble(Double::doubleValue).max().getAsDouble();
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
					if (i.getDistanceFrom(j) <= threshold) {
						double f = weightedLengthFriendliness(j.getId(), i.getId());
						k.put(new Pair<String, String>(j.getId(), i.getId()), f);
					}
				} catch (Exception e) {}
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
					if (i.getDistanceFrom(j) <= threshold) {
						double f = weightedDistanceFriendliness(j.getId(), i.getId());
						k.put(new Pair<String, String>(j.getId(), i.getId()), f);
					}
				} catch (Exception e) {}
			}
		}
		return k;
	}

	public void computeAllLengthFriendlinessConcurrently(Consumer<Double> onUpdate,
			Consumer<NavigableMap<Pair<String, String>, Double>> onFinished) {
		Thread t = new Thread(() -> {
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
						if (i.getDistanceFrom(j) <= threshold) {
							li.add(new Pair<String, String>(j.getId(), i.getId()));
						}
						onUpdate.accept((double) (counter.incrementAndGet())
								/ (stations.size() * stations.size() / 2) / 10);
					} catch (Exception e) {}
				}
			}
			counter.set(0);
			li.stream().unordered().parallel().forEach(l -> {
				onUpdate.accept(.1 + (double) (counter.incrementAndGet()) * .9 / li.size());
				try {
					double f = weightedLengthFriendliness(l.getKey(), l.getValue());
					k.put(l, f);
				} catch (Exception e) {}
			});
			onFinished.accept(k);
		});
		t.start();
	}

	public void computeAllDistanceFriendlinessConcurrently(Consumer<Double> onUpdate,
			Consumer<NavigableMap<Pair<String, String>, Double>> onFinished) {
		Thread t = new Thread(() -> {
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
						if (i.getDistanceFrom(j) <= threshold) {
							li.add(new Pair<String, String>(j.getId(), i.getId()));
						}
						onUpdate.accept((double) (counter.incrementAndGet())
								/ (stations.size() * stations.size() / 2) / 10);
					} catch (Exception e) {}
				}
			}
			counter.set(0);
			li.stream().unordered().parallel().forEach(l -> {
				onUpdate.accept(.1 + (double) (counter.incrementAndGet()) * .9 / li.size());
				try {
					double f = weightedDistanceFriendliness(l.getKey(), l.getValue());
					k.put(l, f);
				} catch (IllegalArgumentException e) {}
			});
			onFinished.accept(k);
		});
		t.start();
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

	private Deque<Node> shortestPath(Node destination, Node location) {
		if (destination.isStation() == false || location.isStation() == false) {
			throw new IllegalArgumentException("Stations must be supplied as arguments.");
		}
		Map<Node, DVertex> frontier = new HashMap<>();
		DVertex i = new DVertex();
		i.value = location;
		i.weight = 0;
		i.prev = null;
		frontier.put(location, i);
		DVertex r = traverse(i, destination, frontier, new HashSet<Node>());
		if (r == null)
			throw new IllegalArgumentException("No path exists.");
		Deque<Node> ret = new LinkedList<>();
		while (r != null) {
			ret.addFirst(r.value);
			r = r.prev;
		}
		return ret;
	}

	private Deque<Node> shortestPath0(Node destination, Node location) {
		if (destination.isStation() == false || location.isStation() == false) {
			throw new IllegalArgumentException("Stations must be supplied as arguments.");
		}
		Map<Node, DVertex> frontier = new HashMap<>();
		DVertex i = new DVertex();
		i.value = location;
		i.weight = 0;
		i.prev = null;
		frontier.put(location, i);
		DVertex r = traverse0(i, destination, frontier, new HashSet<Node>());
		if (r == null)
			throw new IllegalArgumentException("No path exists.");
		Deque<Node> ret = new LinkedList<>();
		while (r != null) {
			ret.addFirst(r.value);
			r = r.prev;
		}
		return ret;
	}

	private DVertex traverse(DVertex c, Node d, Map<Node, DVertex> frontier, Set<Node> visited) {
		if (c.value == d) {
			return c;
		}
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
			return traverse(next, d, frontier, visited);
		} else {
			return null;
		}
	}

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

	@SuppressWarnings("unchecked")
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
				+ nodes.values().stream().sorted((a, b) -> a.getId().compareTo(b.getId()))
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
		if (Double.doubleToLongBits(threshold) != Double.doubleToLongBits(other.threshold))
			return false;
		if (Double.doubleToLongBits(weighting) != Double.doubleToLongBits(other.weighting))
			return false;
		return true;
	}

	public Iterator<Entry<Pair<String, String>, Double>> iterator() {
		return computeAllLengthFriendliness().entrySet().iterator();
	}
}
