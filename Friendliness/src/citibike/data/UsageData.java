package citibike.data;

import static java.lang.Math.pow;
import static java.lang.Math.sqrt;
import static java.util.Collections.shuffle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import citibike.model.CityMap;

public class UsageData {

	private UsageData() {
	}

	public static void serializeData()
			throws IOException, URISyntaxException, ClassNotFoundException {
		serializeData(s -> true);
	}

	public static void serializeData(Function<String, Boolean> use, boolean taxi, double min)
			throws ClassNotFoundException, IOException, URISyntaxException {
		if (taxi)
			serializeDataWithTaxi(use, min);
		else
			serializeData(use);
	}

	public static void serializeData(Function<String, Boolean> use)
			throws IOException, URISyntaxException, ClassNotFoundException {
		BufferedReader br = Files
				.newBufferedReader(Paths.get(UsageData.class.getResource("usagedata").toURI()));
		Map<Integer, Map<Integer, Double>> n = new ConcurrentHashMap<>();
		br.lines().unordered().parallel().forEach(s -> {
			try {
				if (use.apply(s.split("\",\"")[1])) {
					int k1 = Integer.parseInt(s.split("\",\"")[3]);
					int k2 = Integer.parseInt(s.split("\",\"")[7]);
					if (k1 < k2) {
						int tmp = k1;
						k1 = k2;
						k2 = tmp;
					}
					n.computeIfAbsent(k1, k -> new ConcurrentHashMap<>());
					n.get(k1).compute(k2, (v, j) -> j == null ? 1 : j + 1);
				}
			} catch (Exception e) {
			}
		});
		ObjectOutputStream oos = new ObjectOutputStream(
				Files.newOutputStream(Paths.get("serializeddata")));
		oos.writeObject(n);
	}

	public static void serializeDataWithTaxi(Function<String, Boolean> use, double minimum)
			throws IOException, URISyntaxException, ClassNotFoundException {
		CityMap cm = CityMapInitializer.get();
		BufferedReader br = Files
				.newBufferedReader(Paths.get(UsageData.class.getResource("usagedata").toURI()));
		BufferedReader tr = Files
				.newBufferedReader(Paths.get(UsageData.class.getResource("taxidata.csv").toURI()));
		Map<Integer, Map<Integer, Double>> n0 = new ConcurrentHashMap<>();
		Map<Integer, Map<Integer, Double>> n = new ConcurrentHashMap<>();
		BiFunction<Double, Double, String> get = (a, b) -> {
			double min = Double.MAX_VALUE;
			String stat = cm.getStations().get(0);
			for (String st : cm.getStations()) {
				double x = cm.getX(st);
				double y = cm.getY(st);
				double dist = sqrt(pow((a - x), 2) + pow((b - y), 2));
				// System.out.println(x + ", " + y);
				if (dist < min) {
					min = dist;
					stat = st;
				}
			}
			return min <= minimum ? stat : null;
		};
		tr.lines().unordered().parallel().forEach(s -> {
			out: try {
				if (!s.startsWith("VendorID") && use.apply(s.split(",")[1].replace("/", "-"))) {
					double k1 = new Double(s.split(",")[5]);
					double k2 = new Double(s.split(",")[6]);
					double k3 = new Double(s.split(",")[9]);
					double k4 = new Double(s.split(",")[10]);
					String a = get.apply(k2, k1);
					String b = get.apply(k4, k3);
					if (a == null || b == null) {
						break out;
					}
					// System.out.println(a);
					// System.out.println(b);
					int a0 = Integer.parseInt(a);
					int b0 = Integer.parseInt(b);
					n0.computeIfAbsent(a0, k -> new ConcurrentHashMap<>());
					n0.get(a0).compute(b0, (v, j) -> j == null ? 1 : j + 1);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		br.lines().unordered().parallel().forEach(s -> {
			try {
				if (use.apply(s.split("\",\"")[1])) {
					int k1 = Integer.parseInt(s.split("\",\"")[3]);
					int k2 = Integer.parseInt(s.split("\",\"")[7]);
					if (k1 < k2) {
						int tmp = k1;
						k1 = k2;
						k2 = tmp;
					}
					n.computeIfAbsent(k1, k -> new ConcurrentHashMap<>());
					n.get(k1).compute(k2, (v, j) -> j == null ? 1 : j + 1);
				}
			} catch (Exception e) {
			}
		});
		Map<Integer, Map<Integer, Double>> n2 = new ConcurrentHashMap<>();
		n.forEach((a, m0) -> m0.forEach((b, c) -> {
            n2.computeIfAbsent(a, k -> new ConcurrentHashMap<>());
            try {
                n2.get(a).put(b, c / (n0.get(a).get(b)));
            } catch (Exception e) {
                // TODO Auto-generated catch block
                // e.printStackTrace();
            }
        }));
		ObjectOutputStream oos = new ObjectOutputStream(
				Files.newOutputStream(Paths.get("serializeddata")));
		oos.writeObject(n2);
	}

	// public static void serializeDataWithDuration(Function<String, Boolean>
	// use)
	// throws IOException, URISyntaxException, ClassNotFoundException {
	// BufferedReader br = Files
	// .newBufferedReader(Paths.get(UsageData.class.getResource("usagedata").toURI()));
	// Map<Integer, Map<Integer, Double> n = new ConcurrentHashMap<>();
	// br.lines().unordered().parallel().forEach(s -> {
	// try {
	// if (use.apply(s.split("\",\"")[1])) {
	// int k1 = Integer.parseInt(s.split("\",\"")[3]);
	// int k2 = Integer.parseInt(s.split("\",\"")[7]);
	// if (k1 < k2) {
	// int tmp = k1;
	// k1 = k2;
	// k2 = tmp;
	// }
	// if (n.get(k1) == null)
	// n.put(k1, new ConcurrentHashMap<>());
	// n.get(k1).compute(k2, (v, j) -> j == null ? 1 : j + 1);
	// }
	// } catch (Exception e) {
	// }
	// });
	// ObjectOutputStream oos = new ObjectOutputStream(
	// Files.newOutputStream(Paths.get("serializeddata")));
	// oos.writeObject(n);
	// }
	public static List<String> getK(int k, CityMap cm) throws IOException, ClassNotFoundException {
		Map<Integer, Map<Integer, Double>> n = get();
		NavigableMap<Double, String> ret = new TreeMap<>((a, b) -> -Double.compare(a, b));
		HashSet<String> hs = new HashSet<>(cm.getNodes());
		n.entrySet().forEach(y -> {
			for (Entry<Integer, Double> e1 : y.getValue().entrySet()) {
				if (hs.contains("" + y.getKey()) && hs.contains("" + e1.getKey())
						&& !y.getKey().equals(e1.getKey())) {
					// if (y.getKey() == 2006 ) {
					// System.out.println(y.getKey());
					// System.out.println(e1.getKey());
					// }
					ret.put(e1.getValue() + Math.random(), y.getKey() + "\0" + e1.getKey());
					// System.out.println(e1.getValue());
				}
			}
		});
		List<String> ret0 = new ArrayList<>();
		Iterator<Entry<Double, String>> itr = ret.entrySet().iterator();
		// System.out.println(ret.size());
		for (int i = 0; i < k; i++) {
			Entry<Double, String> in = itr.next();
			String v = in.getValue();
			// System.out.println(in.getKey());
			// System.out.println(v.split("\0")[0] + " - " + v.split("\0")[1]);
			ret0.add(v);
		}
		// System.out.println(ret0.size());
		return ret0;
	}

	@SuppressWarnings("unchecked")
	public static Map<Integer, Map<Integer, Double>> get()
			throws IOException, ClassNotFoundException {
		ObjectInputStream ois = new ObjectInputStream(
				Files.newInputStream(Paths.get("serializeddata")));
		Map<Integer, Map<Integer, Double>> n = new ConcurrentHashMap<>();
		n.putAll((Map<Integer, Map<Integer, Double>>) ois.readObject());
		return n;
	}

	public static String generate(Map<Integer, Map<Integer, Double>> k, Consumer<Double> onUpdate,
			int mufp, int pl, boolean usd) throws ClassNotFoundException {
		if (k.size() == 0) {
			for (double i = 0.0; i <= 1.01; i += .01)
				onUpdate.accept(i);
			return "";
		}
		BufferedReader br = null;
		BufferedReader br1 = null;
		try {
			br = Files
					.newBufferedReader(Paths.get(UsageData.class.getResource("usagedata").toURI()));
			Map<Integer, Map<Integer, Double>> n = new ConcurrentHashMap<>();
			br1 = Files
					.newBufferedReader(Paths.get(UsageData.class.getResource("usagedata").toURI()));
			AtomicInteger u = new AtomicInteger(0);
			if (usd) {
				n.putAll(get());
			} else {
				double u0 = (double) br1.lines().unordered().parallel().count();
				onUpdate.accept(0.0);
				br.lines().unordered().parallel().forEach(s -> {
					try {
						int k1 = Integer.parseInt(s.split("\",\"")[3]);
						int k2 = Integer.parseInt(s.split("\",\"")[7]);
						n.computeIfAbsent(k1, k3 -> new ConcurrentHashMap<>());
						n.get(k1).compute(k2, (v, j) -> j == null ? 1 : j + 1);
						onUpdate.accept(u.incrementAndGet() / u0 * 0.9);
					} catch (Exception e) {
					}
				});
			}
			ObjectOutputStream oos = new ObjectOutputStream(
					Files.newOutputStream(Paths.get("serializeddata")));
			oos.writeObject(n);
			List<String> pairs = new ArrayList<>();
			double u1 = (double) k.values().stream().flatMap(h -> h.entrySet().stream()).count();
			u.set(0);
			for (Entry<Integer, Map<Integer, Double>> i0 : k.entrySet()) {
				for (int j : i0.getValue().keySet()) {
					int i = i0.getKey();
					double v00 = ((n.get(j) == null || n.get(j).get(i) == null ? 0
							: n.get(j).get(i))
							+ (n.get(i) == null || n.get(i).get(j) == null ? 0 : n.get(i).get(j)));
					if (v00 >= mufp)
						pairs.add(k.get(i).get(j) + "\t" + v00);
					onUpdate.accept(0.9 + u.incrementAndGet() / u1 * .1);
				}
			}
			shuffle(pairs);
			return pairs.stream().limit(pl).collect(Collectors.joining("\n"));
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

	public static String r(String g) {
		List<Double> xs0 = new ArrayList<>();
		List<Double> ys0 = new ArrayList<>();
		for (String o : g.split("\\n")) {
			xs0.add(Double.parseDouble(o.split("\\t")[0]));
			ys0.add(Double.parseDouble(o.split("\\t")[1]));
		}
		Double[] xs = xs0.toArray(new Double[0]), ys = ys0.toArray(new Double[0]);
		double sx = 0.0;
		double sy = 0.0;
		double sxx = 0.0;
		double syy = 0.0;
		double sxy = 0.0;
		int n = xs.length;
		for (int i = 0; i < n; ++i) {
			double x = xs[i];
			double y = ys[i];
			sx += x;
			sy += y;
			sxx += x * x;
			syy += y * y;
			sxy += x * y;
		}
		double cov = sxy / n - sx * sy / n / n;
		double sigmax = Math.sqrt(sxx / n - sx * sx / n / n);
		double sigmay = Math.sqrt(syy / n - sy * sy / n / n);
		return String.format(" %.3f", cov / sigmax / sigmay);
	}

	public static List<String> getG(int intis, CityMap cm)
			throws IOException, ClassNotFoundException {
		Map<Integer, Map<Integer, Double>> n = get();
		NavigableMap<Double, String> ret = new TreeMap<>((a, b) -> -Double.compare(a, b));
		HashSet<String> hs = new HashSet<>(cm.getNodes());
		n.entrySet().forEach(y -> {
			for (Entry<Integer, Double> e1 : y.getValue().entrySet()) {
				if (hs.contains("" + y.getKey()) && hs.contains("" + e1.getKey())
						&& !y.getKey().equals(e1.getKey())) {
					// if (y.getKey() == 2006 ) {
					// System.out.println(y.getKey());
					// System.out.println(e1.getKey());
					// }
					ret.put(e1.getValue() + Math.random(), y.getKey() + "\0" + e1.getKey());
					// System.out.println(e1.getValue());
				}
			}
		});
		List<String> ret0 = new ArrayList<>();
		Iterator<Entry<Double, String>> itr = ret.entrySet().iterator();
		// System.out.println(ret.size());
		for (;;) {
			Entry<Double, String> in = itr.next();
			String v = in.getValue();
			if (in.getKey() < intis)
				break;
			// System.out.println(v.split("\0")[0] + " - " + v.split("\0")[1]);
			ret0.add(v);
		}
		// System.out.println(ret0.size());
		return ret0;
	}

	public static void serializeTaxiData(Function<String, Boolean> use, double minimum)
			throws IOException, URISyntaxException {
		CityMap cm = CityMapInitializer.get();
		BufferedReader tr = Files
				.newBufferedReader(Paths.get(UsageData.class.getResource("taxidata.csv").toURI()));
		Map<Integer, Map<Integer, Double>> n0 = new ConcurrentHashMap<>();
		BiFunction<Double, Double, String> get = (a, b) -> {
			double min = Double.MAX_VALUE;
			String stat = cm.getStations().get(0);
			for (String st : cm.getStations()) {
				double x = cm.getX(st);
				double y = cm.getY(st);
				double dist = sqrt(pow((a - x), 2) + pow((b - y), 2));
				// System.out.println(x + ", " + y);
				if (dist < min) {
					min = dist;
					stat = st;
				}
			}
			return min <= minimum ? stat : null;
		};
		tr.lines().unordered().parallel().forEach(s -> {
			out: try {
				if (!s.startsWith("VendorID") && use.apply(s.split(",")[1].replace("/", "-"))) {
					double k1 = new Double(s.split(",")[5]);
					double k2 = new Double(s.split(",")[6]);
					double k3 = new Double(s.split(",")[9]);
					double k4 = new Double(s.split(",")[10]);
					String a = get.apply(k2, k1);
					String b = get.apply(k4, k3);
					if (a == null || b == null) {
						break out;
					}
					// System.out.println(a);
					// System.out.println(b);
					int a0 = Integer.parseInt(a);
					int b0 = Integer.parseInt(b);
					n0.computeIfAbsent(a0, k -> new ConcurrentHashMap<>());
					n0.get(a0).compute(b0, (v, j) -> j == null ? 1 : j + 1);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		ObjectOutputStream oos = new ObjectOutputStream(
				Files.newOutputStream(Paths.get("serializedtaxidata")));
		oos.writeObject(n0);
	}

	public static List<String> getKTaxi(int k, CityMap cm)
			throws ClassNotFoundException, IOException {
		Map<Integer, Map<Integer, Double>> n = getTaxi();
		NavigableMap<Double, String> ret = new TreeMap<>((a, b) -> -Double.compare(a, b));
		HashSet<String> hs = new HashSet<>(cm.getNodes());
		n.entrySet().forEach(y -> {
			for (Entry<Integer, Double> e1 : y.getValue().entrySet()) {
				if (hs.contains("" + y.getKey()) && hs.contains("" + e1.getKey())
						&& !y.getKey().equals(e1.getKey())) {
					// if (y.getKey() == 2006 ) {
					// System.out.println(y.getKey());
					// System.out.println(e1.getKey());
					// }
					ret.put(e1.getValue() + Math.random(), y.getKey() + "\0" + e1.getKey());
					// System.out.println(e1.getValue());
				}
			}
		});
		List<String> ret0 = new ArrayList<>();
		Iterator<Entry<Double, String>> itr = ret.entrySet().iterator();
		// System.out.println(ret.size());
		for (int i = 0; i < k; i++) {
			Entry<Double, String> in = itr.next();
			String v = in.getValue();
			// System.out.println(in.getKey());
			// System.out.println(v.split("\0")[0] + " - " + v.split("\0")[1]);
			ret0.add(v);
		}
		// System.out.println(ret0.size());
		return ret0;
	}

	@SuppressWarnings("unchecked")
	public static Map<Integer, Map<Integer, Double>> getTaxi()
			throws IOException, ClassNotFoundException {
		ObjectInputStream ois = new ObjectInputStream(
				Files.newInputStream(Paths.get("serializedtaxidata")));
		Map<Integer, Map<Integer, Double>> n = new ConcurrentHashMap<>();
		n.putAll((Map<Integer, Map<Integer, Double>>) ois.readObject());
		return n;
	}
}
