package citibike.data;

import static java.util.Collections.shuffle;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import citibike.CitibikeRunner;

public class DataPairing {

	private DataPairing() {
	}

	public static String generate(Map<Integer, Map<Integer, Double>> k, Consumer<Double> onUpdate) {
		if (k.size() == 0) {
			for (double i = 0.0; i <= 1.01; i += .01)
				onUpdate.accept(i);
			return "";
		}
		BufferedReader br = null;
		BufferedReader br1 = null;
		try {
			br = Files.newBufferedReader(
					Paths.get(DataPairing.class.getResource("usagedata.txt").toURI()));
			Map<Integer, Map<Integer, Double>> n = new ConcurrentHashMap<>();
			br1 = Files.newBufferedReader(
					Paths.get(DataPairing.class.getResource("usagedata.txt").toURI()));
			double u0 = (double) br1.lines().unordered().parallel().count();
			AtomicInteger u = new AtomicInteger(0);
			onUpdate.accept(0.0);
			br.lines().unordered().parallel().forEach(s -> {
				try {
					int k1 = Integer.parseInt(s.split("\",\"")[3]);
					int k2 = Integer.parseInt(s.split("\",\"")[7]);
					if (n.get(k1) == null)
						n.put(k1, new ConcurrentHashMap<>());
					n.get(k1).compute(k2, (v, j) -> j == null ? 1 : j + 1);
					onUpdate.accept(u.incrementAndGet() / u0 * 0.9);
				} catch (Exception e) {
				}
			});
			List<String> pairs = new ArrayList<String>();
			double u1 = (double) k.values().stream().flatMap(h -> h.entrySet().stream()).count();
			u.set(0);
			for (Entry<Integer, Map<Integer, Double>> i0 : k.entrySet()) {
				for (int j : i0.getValue().keySet()) {
					int i = i0.getKey();
					int v00 = (int) ((n.get(j) == null || n.get(j).get(i) == null ? 0
							: n.get(j).get(i))
							+ (n.get(i) == null || n.get(i).get(j) == null ? 0 : n.get(i).get(j)));
					if (v00 >= CitibikeRunner.MINIMUM_USAGE_FOR_PAIRING)
						pairs.add(k.get(i).get(j) + "\t" + v00);
					onUpdate.accept(0.9 + u.incrementAndGet() / u1 * .1);
				}
			}
			shuffle(pairs);
			return pairs.stream().collect(Collectors.joining("\n"));
		} catch (IOException | URISyntaxException e) {
			e.printStackTrace();
			return null;
		} finally {
			try {
				br.close();
				br1.close();
			} catch (IOException e) {
			}
		}
	}
}
