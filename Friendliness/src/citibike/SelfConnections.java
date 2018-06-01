package citibike;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import citibike.data.CityMapInitializer;
import citibike.data.UsageData;
import citibike.model.CityMap;
import javafx.util.Pair;

public class SelfConnections {

	private static final int NUM = 40;

	public static void main(String[] args)
			throws ClassNotFoundException, IOException, URISyntaxException {
		BufferedReader br = Files.newBufferedReader(
				Paths.get(UsageData.class.getResource("/citibike/data/usagedata.txt").toURI()));
		Map<Integer, Map<Integer, Pair<Double, Double>>> n = new ConcurrentHashMap<>();
		br.lines().unordered().parallel().forEach(s -> {
			try {
				if (true) {
					int k1 = Integer.parseInt(s.split("\",\"")[3]);
					int k2 = Integer.parseInt(s.split("\",\"")[7]);
					if (k1 < k2) {
						int tmp = k1;
						k1 = k2;
						k2 = tmp;
					}
					Double t = (double) Integer.parseInt(s.split("\",\"")[0].substring(1));
                    n.computeIfAbsent(k1, k -> new ConcurrentHashMap<>());
					if (n.get(k1).get(k2) == null) {
						n.get(k1).put(k2, new Pair<>(1.0, t));
					} else {
						Pair<Double, Double> p = n.get(k1).get(k2);
						n.get(k1).put(k2, new Pair<>(p.getKey() + 1,
								(p.getValue() * p.getKey() + t) / (p.getKey() + 1)));
					}
				}
			} catch (Exception e) {
				// e.printStackTrace();
			}
		});
		List<int[]> ar = new ArrayList<>();
		n.forEach((a, b) -> b.entrySet().stream().filter(e -> {
            // System.out.println(e.getKey().equals(a));
            return e.getKey().equals(a);
        }).forEach(e -> {
            // System.out.println(5);
            ar.add(new int[] { e.getKey(), e.getValue().getKey().intValue(),
                    e.getValue().getValue().intValue() });
        }));
		ar.sort((a, b) -> b[1] - a[1]);
		CityMapInitializer.showSelfConnections(ar, "city_self_loop_map", 40);
		AtomicInteger u = new AtomicInteger(0);
		CityMap cm = CityMapInitializer.get();
		ar.forEach(i -> {
			if (cm.getStations().contains(i[0] + "") && u.incrementAndGet() <= NUM) {
				System.out.print(u + "- Station " + i[0] + ": ");
				System.out.println(
						i[1] + " trips, " + i[2] / 60 + ":" + i[2] % 60 + " average duration");
			}
		});
	}
}
