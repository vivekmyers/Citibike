package citibike;

import static java.lang.Math.PI;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import com.google.gson.Gson;

import citibike.data.CityMapInitializer;
import citibike.data.DataPairing;
import citibike.model.CityMap;
import javafx.util.Pair;

public class CitibikeRunner {

	public static final double SEARCH_THRESHOLD_LENGTH_ALGORITHM = 0.05;
	public static final double SEARCH_THRESHOLD_DISTANCE_ALGORITHM = 0;
	public static final double BIKE_WEIGHTING_LENGTH_ALGORITHM = PI / 2;
	public static final double BIKE_WEIGHTING_DISTANCE_ALGORITHM = PI / 2;
	public static final double BIKE_LANE_INITIALIZATION_RESOLUTION = 0.3;
	public static final double BIKE_LANE_LINKING_DISTANCE = .003;
	public static final int MINIMUM_USAGE_FOR_PAIRING = 1000;

	public static void main(String[] args) throws IOException, URISyntaxException {
		for (int i = 0; i < 51; i++)
			System.out.print("—");
		System.out.println();
		System.out.println("Initializing city");
		CityMap cm = CityMapInitializer.init(CitibikeRunner::disp,
				(int) (1 / BIKE_LANE_INITIALIZATION_RESOLUTION + 0.5));
		cnt = 0;
		System.out.println("Computing friendliness with weighted length algorithm");
		Gson g = new Gson();
		cm.reset(BIKE_WEIGHTING_LENGTH_ALGORITHM, SEARCH_THRESHOLD_LENGTH_ALGORITHM)
				.computeAllLengthFriendlinessConcurrently(CitibikeRunner::disp, k -> {
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
						String sos = DataPairing.generate(remap(k), CitibikeRunner::disp);
						cnt = 0;
						System.out.println("Writing results to length_pairing.txt");
						pwl = new PrintWriter(new File("length_pairing.txt"));
						pwl.println(sos);
						pwl.flush();
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					}
					System.out.println("Computing friendliness with max distance algorithm");
					cm.reset(BIKE_WEIGHTING_DISTANCE_ALGORITHM, SEARCH_THRESHOLD_DISTANCE_ALGORITHM)
							.computeAllDistanceFriendlinessConcurrently(CitibikeRunner::disp, m -> {
								System.out.println("Writing results to distance_friendliness.json");
								PrintWriter pwd;
								try {
									pwd = new PrintWriter(new File("distance_friendliness.json"));
									pwd.println(g.toJson(remap(m)));
									pwd.flush();
								} catch (FileNotFoundException e) {
									e.printStackTrace();
								}
								System.out.println("Pairing results with usage data");
								cnt = 0;
								try {
									String sos = DataPairing.generate(remap(m),
											CitibikeRunner::disp);
									cnt = 0;
									System.out.println("Writing results to distance_pairing.txt");
									pwd = new PrintWriter(new File("distance_pairing.txt"));
									pwd.println(sos);
									pwd.flush();
								} catch (FileNotFoundException e) {
									e.printStackTrace();
								}
								System.out.println("Done");
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
		if (i * 50 >= cnt) {
			System.out.print("\u2588");
			cnt++;
		}
		if (i >= 1)
			System.out.println();
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
		System.out.println(cm);
	}
}
