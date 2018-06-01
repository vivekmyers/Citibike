package citibike.model;

class BikeLane extends Road {

	private double weighting;

	public BikeLane(double distance, Node destination, double weighting) {
		super(distance, destination);
		this.weighting = weighting;
	}

	@Override
	protected double getWeighting() {
		return weighting;
	}
}
