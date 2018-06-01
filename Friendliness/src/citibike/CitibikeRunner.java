package citibike;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import com.google.gson.Gson;

import citibike.data.CityMapInitializer;
import citibike.data.UsageData;
import citibike.model.CityMap;
import javafx.util.Pair;

public class CitibikeRunner {

	public static final double SEARCH_THRESHOLD_LENGTH_ALGORITHM = 0;
	public static final double SEARCH_THRESHOLD_DISTANCE_ALGORITHM = 0.02;
	public static final double MINLEN_LENGTH_ALGORITHM = 0;
	public static final double MINLEN_DISTANCE_ALGORITHM = 0.018;
	public static final double BIKE_WEIGHTING_LENGTH_ALGORITHM = 1.2;
	public static final double BIKE_WEIGHTING_DISTANCE_ALGORITHM = 1.2;
	public static final double STATION_LINKING_DISTANCE = .0006;
	public static final int MINIMUM_USAGE_FOR_PAIRING = 200;
	public static final int PAIRING_LIMIT = 5000;
	public static final int USE_NUM = 800;
	public static final boolean USE_SERIALIZED_DATA = true;

	public static void main(String[] args)
			throws IOException, URISyntaxException, ClassNotFoundException {
		run(SEARCH_THRESHOLD_LENGTH_ALGORITHM, SEARCH_THRESHOLD_K_PATHS_ALGORITHM,
				SEARCH_THRESHOLD_DISTANCE_ALGORITHM, BIKE_WEIGHTING_LENGTH_ALGORITHM,
				K_PATHS_ALGORITHM_ITERATIONS, BIKE_WEIGHTING_DISTANCE_ALGORITHM,
				BIKE_LANE_INITIALIZATION_RESOLUTION, STATION_LINKING_DISTANCE,
				MINIMUM_USAGE_FOR_PAIRING, PAIRING_LIMIT, () -> {
				}, USE_SERIALIZED_DATA, PROBABILITY_USE_STATION, MINLEN_LENGTH_ALGORITHM,
				MINLEN_DISTANCE_ALGORITHM);
	}

	public static void run(double d, double st, double std, double bwl, int kpi, double bwd,
			double blir, double blld, int mufp, int pl, Runnable onFinished, boolean usd,
			double prob, double minlenLengthAlgorithm, double minlenDistanceAlgorithm)
			throws IOException, URISyntaxException, ClassNotFoundException {
		for (int i = 0; i < 51; i++)
			System.out.print("—");
		System.out.println();
		System.out.println("Initializing city");
		CityMap cm = CityMapInitializer.init(CitibikeRunner::disp, (int) (1 / blir + 0.5), blld,
				prob);
		List<String> use = UsageData.getK(USE_NUM, cm);
		cnt = 0;
		if (d != 0)
			System.out.println("Computing friendliness with weighted length algorithm");
		Gson g = new Gson();
		cm.reset(bwl, d, minlenLengthAlgorithm).computeAllLengthFriendlinessConcurrently(
				CitibikeRunner::disp, k -> {
					l: {
						if (d == 0)
							break l;
						System.out.println("Writing results to length_friendliness.json");
						PrintWriter pwl;
						try {
							pwl = new PrintWriter(new File("length_friendliness.json"));
							pwl.println(g.toJson(remap(k)));
							pwl.flush();
						} catch (FileNotFoundException e) {
							e.printStackTrace();
						}
						System.out.println("Pairing results with usage data");
						cnt = 0;
						try {
							String sos = UsageData.generate(remap(k), CitibikeRunner::disp, mufp,
									pl, usd);
							cnt = 0;
							System.out.println("Writing results to length_pairing.txt (r="
									+ UsageData.r(sos) + ")");
							pwl = new PrintWriter(new File("length_pairing.txt"));
							pwl.println(sos);
							pwl.flush();
						} catch (FileNotFoundException | ClassNotFoundException e) {
							e.printStackTrace();
						}
                    }
					if (st != 0)
						System.out.println("Computing friendliness with kpaths algorithm");
					cm.reset(kpi, st).computeAllKPathsFriendlinessConcurrently(
							CitibikeRunner::disp, m -> {
								k: {
									if (st == 0)
										break k;
									System.out
											.println("Writing results to kpaths_friendliness.json");
									PrintWriter pwd;
									try {
										pwd = new PrintWriter(new File("kpaths_friendliness.json"));
										pwd.println(g.toJson(remap(m)));
										pwd.flush();
									} catch (FileNotFoundException e) {
										e.printStackTrace();
									}
									System.out.println("Pairing results with usage data");
									cnt = 0;
									try {
										String sos = UsageData.generate(remap(m),
												CitibikeRunner::disp, mufp, pl, usd);
										cnt = 0;
										System.out
												.println("Writing results to kpaths_pairing.txt (r="
														+ UsageData.r(sos) + ")");
										pwd = new PrintWriter(new File("kpaths_pairing.txt"));
										pwd.println(sos);
										pwd.flush();
									} catch (FileNotFoundException | ClassNotFoundException e) {
										e.printStackTrace();
									}
                                }
								if (std != 0)
									System.out.println(
											"Computing friendliness with max distance algorithm");
								cm.reset(bwd, std, minlenDistanceAlgorithm)
										.computeAllDistanceFriendlinessConcurrently(
												CitibikeRunner::disp, z0 -> {
													d: {
														// System.out.println(z0);
														// System.out.println(remap(z0));
														if (std == 0)
															break d;
														System.out.println(
																"Writing results to distance_friendliness.json");
														PrintWriter pwd1;
														try {
															pwd1 = new PrintWriter(new File(
																	"distance_friendliness.json"));
															pwd1.println(g.toJson(remap(z0)));
															pwd1.flush();
														} catch (FileNotFoundException e) {
															e.printStackTrace();
														}
														System.out.println(
																"Pairing results with usage data");
														cnt = 0;
														try {
															String sos = UsageData.generate(
																	remap(z0), CitibikeRunner::disp,
																	mufp, pl, usd);
															cnt = 0;
															System.out.println(
																	"Writing results to distance_pairing.txt (r="
																			+ UsageData.r(sos)
																			+ ")");
															pwd1 = new PrintWriter(new File(
																	"distance_pairing.txt"));
															pwd1.println(sos);
															pwd1.flush();
														} catch (FileNotFoundException | ClassNotFoundException e) {
															e.printStackTrace();
														}
                                                    }
													System.out.println("Done");
													onFinished.run();
												});
							});
				});
	}

	public static Map<Integer, Map<Integer, Double>> remap(Map<Pair<String, String>, Double> k) {
		Map<Integer, Map<Integer, Double>> u = new TreeMap<>();
		for (Pair<String, String> v : k.keySet()) {
			u.put(Integer.parseInt(v.getKey()), new TreeMap<>());
		}
		for (Entry<Pair<String, String>, Double> e : k.entrySet()) {
			u.get(Integer.parseInt(e.getKey().getKey()))
					.put(Integer.parseInt(e.getKey().getValue()), e.getValue());
		}
		return u;
	}

	private static volatile int cnt = 0;

	public static void disp(Double i) {
		while (i * 50 >= cnt) {
			System.out.print("\u2588");
			cnt++;
		}
		if (i >= 1) {
			System.out.println();
		}
	}

	public static void demo() {
		CityMap cm = new CityMap();
		cm.addStation("A", 0, 200);
		cm.addNode("B", 200, 0);
		cm.addStation("C", 200, 200);
		cm.addStation("D", 400, 400);
		cm.addNode("E", 0, 900);
		cm.roadConnect("A", "B");
		cm.bikeConnect("A", "C");
		cm.bikeConnect("C", "B");
		cm.roadConnect("B", "D");
		cm.bikeConnect("E", "A");
		cm.bikeConnect("E", "C");
		cm.bikeConnect("E", "D");
		System.out.println("Bike Friendliness: " + cm.computeAllLengthFriendliness());
		System.out.println("Bike Friendliness: " + cm.computeAllDistanceFriendliness());
		System.out.println(cm);
	}

	@Deprecated
	public static final double SEARCH_THRESHOLD_K_PATHS_ALGORITHM = 0;
	@Deprecated
	public static final int K_PATHS_ALGORITHM_ITERATIONS = 5;
	@Deprecated
	public static final double PROBABILITY_USE_STATION = 1;
	@Deprecated
	public static final double BIKE_LANE_INITIALIZATION_RESOLUTION = 0.1;

	private CitibikeRunner() {
	}
}
