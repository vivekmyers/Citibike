package citibike.model;

import static java.util.stream.Collectors.joining;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

class Node implements Iterable<Road> {

	private String id;
	private boolean station;
	private List<Road> next;
	private double x;
	private double y;

	public Node(double x, double y, String id, boolean station) {
		this.station = station;
		this.id = id;
		this.x = x;
		this.y = y;
		this.next = Collections.synchronizedList(new ArrayList<>());
	}

	public Node(double x, double y, String id) {
		this(x, y, id, false);
	}

	public void connect(Node v) {
		if (v == this)
			return;
		double distance = Math.sqrt((x - v.x) * (x - v.x) + (y - v.y) * (y - v.y));
		Road r = new Road(distance, v);
		if (!next.contains(r))
			next.add(r);
	}

	public void bconnect(Node v, double w) {
		if (v == this)
			return;
		double distance = Math.sqrt((x - v.x) * (x - v.x) + (y - v.y) * (y - v.y));
		Road r = new BikeLane(distance, v, w);
		if (!next.contains(r))
			next.add(r);;
	}

	public void disconnect(Node v) {
		double distance = getDistanceFrom(v);
		Road r = new Road(distance, v);
		if (!next.remove(r))
			throw new IllegalArgumentException("No road to remove.");
	}

	public List<Road> roads() {
		return Collections.unmodifiableList(next);
	}

	public double getDistanceFrom(Node v) {
		return Math.sqrt((x - v.x) * (x - v.x) + (y - v.y) * (y - v.y));
	}

	public boolean hasNext(Node v) {
		for (Road r : this) {
			if (r.getDestination() == v)
				return true;
		}
		return false;
	}

	public double getY() {
		return y;
	}

	public double getX() {
		return x;
	}

	public String toString() {
		return id + " -> [" + next.stream().map(r -> r.getDestination()).map(n -> n.getId())
				.collect(joining(", ")) + ']';
	}

	public String getId() {
		return id;
	}

	public Iterator<Road> iterator() {
		return next.iterator();
	}

	public boolean isStation() {
		return station;
	}
}
