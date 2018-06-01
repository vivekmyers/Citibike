package citibike.model;
class Road {

	private double distance;
	private Node destination;


	public Road(double distance, Node destination) {
		this.destination = destination;
		this.distance = distance;
	}


	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((destination == null) ? 0 : destination.hashCode());
		long temp;
		temp = Double.doubleToLongBits(distance);
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
		Road other = (Road) obj;
		if (destination == null) {
			if (other.destination != null)
				return false;
		} else if (!destination.equals(other.destination))
			return false;
		if (Double.doubleToLongBits(distance) != Double.doubleToLongBits(other.distance))
			return false;
		return true;
	}

	public double getDistance() {
		return distance;
	}

	public double getWeight() {
		return distance * getWeighting();
	}

	protected double getWeighting() {
		return 1.0;
	}

	public Node getDestination() {
		return destination;
	}
}
