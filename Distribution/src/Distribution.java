import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class Distribution {

	private static final double HB = 0.001;
	private static final int BUS = 50;

	public static void main(String[] args) throws IOException {
		for (int i = 0; i < 51; i++)
			System.out.print("—");
		System.out.println("\nReading Data");
		BufferedReader br = Files.newBufferedReader(Paths.get("usagedata.txt"));
		Map<Integer, Map<Integer, Double>> n = new ConcurrentHashMap<>();
		BufferedReader br1 = Files.newBufferedReader(Paths.get("usagedata.txt"));
		double u0 = (double) br1.lines().unordered().parallel().count();
		AtomicInteger u = new AtomicInteger(0);
		Consumer<Double> onUpdate = Distribution::disp;
		onUpdate.accept(0.0);
		br.lines().unordered().parallel().forEach(s -> {
			try {
				onUpdate.accept(u.incrementAndGet() / u0);
				int k1 = Integer.parseInt(s.split("\",\"")[3]);
				int k2 = Integer.parseInt(s.split("\",\"")[7]);
				if (k1 > k2) {
					int tmp = k1;
					k1 = k2;
					k2 = tmp;
				}
				if (n.get(k1) == null)
					n.put(k1, new ConcurrentHashMap<>());
				n.get(k1).compute(k2, (v, j) -> j == null ? 1 : j + 1);
			} catch (Exception e) {
			}
		});
		NavigableMap<Integer, Integer> buckets = new TreeMap<>();
		for (int i = 0; i < 10_000; i += BUS) {
			buckets.put(i, 0);
		}
		NavigableMap<Integer, Integer> h2 = new TreeMap<>();
		PrintWriter pw = new PrintWriter(new File("discrete.txt"));
		for (Map<Integer, Double> b : n.values()) {
			for (double y : b.values()) {
				buckets.compute(buckets.floorKey((int) y), (k, v) -> v + 1);
				h2.put((int) y, 0);
			}
		}
		{
			double c = 0;
			PrintWriter pw1 = new PrintWriter(new File("percent.txt"));
			double lb = 0.0;
			double hb = HB;
			int sum = 0;
			for (Map.Entry<Integer, Integer> e1 : h2.entrySet()) {
				if (c++ / h2.size() > hb) {
					double l = hb - lb;
					pw1.print((int) (lb * 1000) / 10.0);
					pw1.print("%\t");
					pw1.println(sum);
					sum = 0;
					hb += l;
					lb += l;
				}
				sum += e1.getKey();
			}
			double l = hb - lb;
			pw1.print((int) (lb * 1000) / 10.0);
			pw1.print("%\t");
			pw1.println(sum);
			sum = 0;
			hb += l;
			lb += l;
			pw1.flush();
		}
		{
			double c = 0;
			PrintWriter pw1 = new PrintWriter(new File("percent_cumulative.txt"));
			double lb = 0.0;
			double hb = HB;
			int sum = 0;
			for (Map.Entry<Integer, Integer> e1 : h2.entrySet()) {
				if (c++ / h2.size() > hb) {
					double l = hb - lb;
					pw1.print((int) (lb * 1000) / 10.0);
					pw1.print("%\t");
					pw1.println(sum);
					hb += l;
					lb += l;
				}
				sum += e1.getKey();
			}
			double l = hb - lb;
			pw1.print((int) (lb * 1000) / 10.0);
			pw1.print("%\t");
			pw1.println(sum);
			sum = 0;
			hb += l;
			lb += l;
			pw1.flush();
		}
		for (int j : buckets.values()) {
			pw.println(j);
		}
		pw.flush();
		System.out.println("Done");
	}

	static int cnt = 0;

	public static void disp(Double i) {
		if (i * 50 >= cnt) {
			System.out.print("\u2588");
			cnt++;
		}
		if (i >= 1) {
			System.out.println();
			cnt = 10000;
		}
	}
}
