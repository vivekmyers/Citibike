package citibike.data;

import static java.util.stream.Collectors.joining;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.imageio.ImageIO;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import citibike.KPopularRunner;
import citibike.model.CityMap;
import javafx.util.Pair;

public class CityMapInitializer {

	private static final double LAT_LONG = 1 / 52.47 * 69;
	private static final int S = Color.RED.brighter().getRGB();
	private static final int R = Color.GRAY.getRGB();
	private static final int B = Color.getHSBColor(0.5f / 3f, (float) 0.7, 0.8f).brighter().getRGB();
	private static final int SZ = 5;
	private static final Supplier<Color> RT = () -> Color.getHSBColor(
			(float) ((Math.random() * 40 + 180) / 360.0), (float) (Math.random() * .2 + .8f),
			(float) (Math.random() * .5 + .5f)).brighter();
	private static final Supplier<Color> TRT = () -> Color.getHSBColor(
			(float) ((Math.random() * 40.0 + 30) / 360), (float) (Math.random() * .5 + .5f),
			(float) (Math.random() * .5 + .5f));

	private CityMapInitializer() {
	}

	public static CityMap init(Consumer<Double> onUpdate, int res, double blld, double prob)
			throws IOException, URISyntaxException {
		if (res <= 0)
			throw new IllegalArgumentException("Resolution must be on (0,1].");
		CityMap cm = stations(prob);
		roads(cm, onUpdate, blld);
		// lanes(cm, onUpdate, res, blld);
		cm.optimize();
		drawCity(cm);
		return cm;
	}

	public static void altMapRoutes(int intis, String string)
			throws IOException, URISyntaxException, ClassNotFoundException {
		CityMap cm = stations(1);
		roads(cm, d -> {
		}, KPopularRunner.LINK_DIST);
		drawHeavyUseRoutes(intis, cm, string);
	}

	/**
	 * Creates an image using serializeddata and serializedtaxidata showing the
	 * most-used bike and taxi routes.
	 * @param i the number of routes of each type to show
	 * @param name the name of the image to be generated
	 */
	public static void mapRoutesWithTaxi(int i, String name) {
		try {
			CityMap cm = stations(1);
			roads(cm, d -> {
			}, KPopularRunner.LINK_DIST);
			drawTaxiBikeRoutes(cm, i, name);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	static int cnt = 0;

	@SuppressWarnings("unchecked")
	private static void roads(CityMap cm, Consumer<Double> onUpdate, double blld)
			throws IOException, URISyntaxException {
		onUpdate.accept(0.0);
		// System.out.println(CityMapInitializer.class.getResource("allroads.geojson"));
		// System.out.println(CityMapInitializer.class.getResource("/citibike/data/stations.json"));
		URL resource = CityMapInitializer.class.getResource("/citibike/data/allroads.json");
		// System.out.println(resource);
		String data = Files.newBufferedReader(Paths.get(resource.toURI())).lines()
				.collect(joining());
		Gson gson = new Gson();
		Map<String, Object> st = gson.fromJson(data, new TypeToken<Map<String, Object>>() {
		}.getType());
		Map<Pair<Double, Double>, String> prs = new HashMap<>();
		List<Double> c1 = new ArrayList<>();
		List<Double> c2 = new ArrayList<>();
		AtomicInteger num = new AtomicInteger(0);
		((List<Map<String, Object>>) st.get("features")).forEach(k -> ((List<List<Double>>) ((Map<String, Object>) k.get("geometry")).get("coordinates"))
                .forEach(z -> {
                    String name = "" + --cnt;
                    Double zg0 = z.get(0);
                    Double zg1 = z.get(1);
                    cm.addNode(name, zg1, zg0);
                    c1.add(zg0);
                    c2.add(zg1);
                    prs.put(new Pair<>(zg0, zg1), name);
                    num.incrementAndGet();
                }));
		AtomicInteger num0 = new AtomicInteger(0);
		cm.addNode("ref1", 40.776457, -73.970676);
		cm.addNode("ref2", 40.787739, -73.960144);
		((List<Map<String, Object>>) st.get("features")).forEach(k -> {
			List<Double> old = null;
			for (List<Double> z : ((List<List<Double>>) ((Map<String, Object>) k.get("geometry"))
					.get("coordinates"))) {
				if (old != null) {
					// System.out.println(k.get("properties"));
					// System.out.println(cm.distance(
					// prs.get(new Pair<Double, Double>(old.get(0),
					// old.get(1))), "ref"));
					boolean sg = 0.013 > cm.distance(
							prs.get(new Pair<>(old.get(0), old.get(1))), "ref1")
							|| 0.013 > cm.distance(
									prs.get(new Pair<>(old.get(0), old.get(1))),
									"ref2");
					if (((Map<String, Object>) k.get("properties")).get("type").equals("cycleway")
							|| (((Map<String, Object>) k.get("properties")).get("type")
									.equals("footway") && sg))
						cm.bikeConnect(prs.get(new Pair<>(old.get(0), old.get(1))),
								prs.get(new Pair<>(z.get(0), z.get(1))));
					else
						cm.roadConnect(prs.get(new Pair<>(old.get(0), old.get(1))),
								prs.get(new Pair<>(z.get(0), z.get(1))));
				}
				old = z;
			}
		});
		cm.remove("ref1");
		cm.remove("ref2");
		// System.out.println(c1.stream().mapToDouble(Double::valueOf).sum() /
		// c1.size());
		// System.out.println(c2.stream().mapToDouble(Double::valueOf).sum() /
		// c2.size());
		cm.getStations().forEach(s -> {
			double i = num0.incrementAndGet();
			onUpdate.accept(i / cm.getStations().size());
			web(s, cm, blld);
		});
	}

	private static CityMap stations(double prob) throws IOException, URISyntaxException {
		String data = Files
				.newBufferedReader(
						Paths.get(CityMapInitializer.class.getResource("stations.json").toURI()))
				.lines().collect(joining());
		Gson gson = new Gson();
		Map<String, Object> st = gson.fromJson(data, new TypeToken<Map<String, Object>>() {
		}.getType());
		List<Double> c3 = new ArrayList<>();
		List<Double> c4 = new ArrayList<>();
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> stat = (List<Map<String, Object>>) st.get("stationBeanList");
		CityMap cm = new CityMap();
		for (Map<String, Object> mp : stat) {
			double x = (double) mp.get("latitude");
			double y = (double) mp.get("longitude");
			if (x > 40.699372 && y > -74.024346)
				if (x > 40.705638 || y < -73.999070)
					if (x > 40.712010 || y < -73.975296)
						if (x > 40.743250 || y < -73.969086)
							if (x > 40.754640 || y < -73.959559) {
								// if (Math.random() < prob)
								c3.add(x);
								c4.add(y);
								cm.addStation(String.valueOf((int) ((double) mp.get("id"))), x, y);
								// else
								// cm.addNode(String.valueOf((int) ((double)
								// mp.get("id"))), x, y);
							}
		}
		// System.out.println(c3.stream().mapToDouble(Double::valueOf).sum() /
		// c3.size());
		// System.out.println(c4.stream().mapToDouble(Double::valueOf).sum() /
		// c4.size());
		// String connections = Files
		// .newBufferedReader(
		// Paths.get(CityMapInitializer.class.getResource("distances07.json").toURI()))
		// .lines().collect(joining());
		// Map<String, Map<String, Integer>> con = gson.fromJson(connections,
		// new TypeToken<Map<String, Map<String, Integer>>>() {
		// }.getType());
		// for (Entry<String, Map<String, Integer>> j : con.entrySet()) {
		// for (String z : j.getValue().keySet()) {
		// if (cm.contains(j.getKey()) && cm.contains(z))
		// cm.roadConnect(j.getKey(), z);
		// }
		// }
		return cm;
	}

	public static void mapRoutes(int k, String string)
			throws IOException, URISyntaxException, ClassNotFoundException {
		CityMap cm = stations(1);
		roads(cm, d -> {
		}, KPopularRunner.LINK_DIST);
		drawPopularBikeRoutes(cm, k, string);
	}

	public static void showSelfConnections(List<int[]> k, String string, int num)
			throws IOException, URISyntaxException, ClassNotFoundException {
		CityMap cm = stations(1);
		roads(cm, d -> {
		}, KPopularRunner.LINK_DIST);
		image2(cm, k, string);
	}

	public static CityMap get() throws IOException, URISyntaxException {
		CityMap cm = stations(1);
		roads(cm, d -> {
		}, KPopularRunner.LINK_DIST);
		return cm;
	}

	private static class BufferedImage extends java.awt.image.BufferedImage {

		BufferedImage(int a, int b, int type) {
			super((int) (a * LAT_LONG), b, type);
		}
	}

	/**
	 * Draw the {@code k} most popular bike routes and send output to a file.
	 * @param outputFilename Name of file to send output to.
	 */
	private static void drawPopularBikeRoutes(CityMap cm, int k, String outputFilename)
			throws IOException, ClassNotFoundException {
		// System.out.println("Writing results to city_route_map.png");
		double dy = -74.068;
		double dx = 40.895;
		BufferedImage img = new BufferedImage(0xec0 * KPopularRunner.MULT_IMG / 0x4000,
				0xbc0 * KPopularRunner.MULT_IMG / 0x4000, BufferedImage.TYPE_INT_RGB);
		BiFunction<Double, Double, Integer[]> trans = (x, y) -> {
			y = y - dy;
			x = x - dx;
			x = -x;
			x *= KPopularRunner.MULT_IMG;
			y *= KPopularRunner.MULT_IMG;
			x *= LAT_LONG;
			y = img.getHeight() - y - 1;
			return new Integer[] { (int) x.doubleValue(), (int) y.doubleValue() };
		};
		for (String n : cm.getNodes()) {
			double x = cm.getX(n);
			double y = cm.getY(n);
			// System.out.println(x);
			// System.out.println(y);
			x = trans.apply(x, y)[0];
			y = trans.apply(x, y)[1];
			Graphics2D grph = img.createGraphics();
			grph.setStroke(new BasicStroke(KPopularRunner.ROAD_WIDTH));
			for (String k1 : cm.getConnections(n)) {
				if (cm.getStations().contains(n) || cm.getStations().contains(k1)) {
					continue;
				}
				int x0 = trans.apply(cm.getX(k1), cm.getY(k1))[0];
				int y0 = trans.apply(cm.getX(k1), cm.getY(k1))[1];
				if (cm.isBike(n, k1)) {
					grph.setColor(new Color(B));
					grph.drawLine((int) x, (int) y, x0, y0);
				} else {
					grph.setColor(new Color(R));
					grph.drawLine((int) x, (int) y, x0, y0);
				}
			}
			// img.setRGB((int) x, (int) y, cm.hasBike(n) ? B : R);
		}
		// Function<Double, Integer> f = x -> (int) (x * 256);
		Graphics2D g2 = img.createGraphics();
		// AtomicInteger c = new AtomicInteger(0);
		// AtomicInteger ai = new AtomicInteger(0);
		UsageData.getK(k, cm).forEach(s -> {
			// String h = "" + c.incrementAndGet();
			// System.out.println(h + "S");
			String a0 = s.split("\0")[0];
			String b0 = s.split("\0")[1];
			String prev = null;
			LinkedList<String> path = cm.path(a0, b0);
			// System.out.println(a0 + " <> " + b0);
			// System.out.println(cm.path(a0,b0));
			// System.out.println(cm.weightedLengthFriendliness(a0,b0));
			// System.out.println(ai.incrementAndGet());
			// System.out.println(a0);
			// System.out.println(b0);
			// System.out.println(path);
			// System.out.println(cm.path("387", "2006"));
			// System.out.println(s.replace("\0", "++"));
			// System.out.println(path);
			synchronized (g2) {
				g2.setColor(RT.get());
				g2.setStroke(new BasicStroke(KPopularRunner.PATH_WIDTH));
				for (String u : path) {
					if (prev != null) {
						Integer[] c1 = trans.apply(cm.getX(prev), cm.getY(prev));
						Integer[] c2 = trans.apply(cm.getX(u), cm.getY(u));
						// System.out.println(c1[0] + "}" + c1[1]);
						g2.drawLine(c1[0], c1[1], c2[0], c2[1]);
					}
					prev = u;
				}
			}
			Integer[] c1 = trans.apply(cm.getX(path.getFirst()), cm.getY(path.getFirst()));
			Integer[] c2 = trans.apply(cm.getX(path.getLast()), cm.getY(path.getLast()));
			img.setRGB(c1[0], c1[1], S);
			img.setRGB(c2[0], c2[1], S);
			// System.out.println(h + "F");
		});
		for (String n : cm.getStations()) {
			double x = cm.getX(n);
			double y = cm.getY(n);
			x = trans.apply(x, y)[0];
			y = trans.apply(x, y)[1];
			Graphics2D grp = img.createGraphics();
			grp.setColor(new Color(S));
			grp.fillRect((int) (x - SZ), (int) (y - SZ), SZ * 2 + 1, SZ * 2 + 1);
			img.setRGB((int) x, (int) y, S);
		}
		double rotationRequired = Math.toRadians(90);
		double locationX = img.getWidth() / 2;
		double locationY = img.getHeight() / 2;
		AffineTransform tx = AffineTransform.getRotateInstance(rotationRequired, locationX,
				locationY);
		AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_BILINEAR);
		java.awt.image.BufferedImage img1 = op.filter(img, null);
		ImageIO.write(img1, "png", new File(outputFilename + ".png"));
	}

	private static void image2(CityMap cm, List<int[]> k, String string)
			throws IOException, ClassNotFoundException {
		// System.out.println("Writing results to city_route_map.png");
		double dy = -74.068;
		double dx = 40.895;
		BufferedImage img = new BufferedImage(0xec0 * KPopularRunner.MULT_IMG / 0x4000,
				0xbc0 * KPopularRunner.MULT_IMG / 0x4000, BufferedImage.TYPE_INT_RGB);
		BiFunction<Double, Double, Integer[]> trans = (x, y) -> {
			y = y - dy;
			x = x - dx;
			x = -x;
			x *= KPopularRunner.MULT_IMG;
			y *= KPopularRunner.MULT_IMG;
			y = img.getHeight() - y - 1;
			x *= LAT_LONG;
			return new Integer[] { (int) x.doubleValue(), (int) y.doubleValue() };
		};
		for (String n : cm.getNodes()) {
			double x = cm.getX(n);
			double y = cm.getY(n);
			// System.out.println(x);
			// System.out.println(y);
			x = trans.apply(x, y)[0];
			y = trans.apply(x, y)[1];
			Graphics2D grph = img.createGraphics();
			grph.setStroke(new BasicStroke(KPopularRunner.ROAD_WIDTH));
			for (String k1 : cm.getConnections(n)) {
				if (cm.getStations().contains(n) || cm.getStations().contains(k1)) {
					continue;
				}
				int x0 = trans.apply(cm.getX(k1), cm.getY(k1))[0];
				int y0 = trans.apply(cm.getX(k1), cm.getY(k1))[1];
				if (cm.isBike(n, k1)) {
					grph.setColor(new Color(B));
					grph.drawLine((int) x, (int) y, x0, y0);
				} else {
					grph.setColor(new Color(R));
					grph.drawLine((int) x, (int) y, x0, y0);
				}
			}
			// img.setRGB((int) x, (int) y, cm.hasBike(n) ? B : R);
		}
		// Function<Double, Integer> f = x -> (int) (x * 256);
		Graphics2D g2 = img.createGraphics();
		// AtomicInteger c = new AtomicInteger(0);
		// int b = 0;
		for (int[] z : k) {
			if (!cm.getStations().contains("" + z[0]))
				continue;
			// if (b++ >= num)
			// break;
			int x0 = trans.apply(cm.getX("" + z[0]), cm.getY("" + z[0]))[0];
			int y0 = trans.apply(cm.getX("" + z[0]), cm.getY("" + z[0]))[1];
			int w = (int) (Math.sqrt(z[1]) / 5);
			if (z[2] / 60.0f / 60.0f > 1) {
				z[2] = 1;
			}
			Color c = Color.getHSBColor(z[2] / 60.0f / 60.0f / 3.5f + 1f / 2.5f, 0.8f, 0.8f);
			g2.setColor(c);
			g2.fillOval(x0 - w, y0 - w, 2 * w, 2 * w);
		}
		for (String n : cm.getStations()) {
			double x = cm.getX(n);
			double y = cm.getY(n);
			x = trans.apply(x, y)[0];
			y = trans.apply(x, y)[1];
			Graphics2D grp = img.createGraphics();
			grp.setColor(new Color(S));
			grp.fillRect((int) (x - SZ), (int) (y - SZ), SZ * 2 + 1, SZ * 2 + 1);
			img.setRGB((int) x, (int) y, S);
		}
		double rotationRequired = Math.toRadians(90);
		double locationX = img.getWidth() / 2;
		double locationY = img.getHeight() / 2;
		AffineTransform tx = AffineTransform.getRotateInstance(rotationRequired, locationX,
				locationY);
		AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_BILINEAR);
		java.awt.image.BufferedImage img1 = op.filter(img, null);
		ImageIO.write(img1, "png", new File(string + ".png"));
	}

	/**
	 * Draw all bike routes whose usage is above a given threshold.
	 * 
	 * @param intis minimum usage threshold for display
	 * @param cm
	 * @param outputFilename name of file to set output to.
	 */
	private static void drawHeavyUseRoutes(int intis, CityMap cm, String outputFilename)
			throws ClassNotFoundException, IOException {
		// System.out.println("Writing results to city_route_map.png");
		double dy = -74.068;
		double dx = 40.895;
		BufferedImage img = new BufferedImage(0xec0 * KPopularRunner.MULT_IMG / 0x4000,
				0xbc0 * KPopularRunner.MULT_IMG / 0x4000, BufferedImage.TYPE_INT_RGB);
		PrintWriter log = new PrintWriter(outputFilename + ".log");
		BiFunction<Double, Double, Integer[]> trans = (x, y) -> {
			y = y - dy;
			x = x - dx;
			x = -x;
			x *= KPopularRunner.MULT_IMG;
			y *= KPopularRunner.MULT_IMG;
			x *= LAT_LONG;
			y = img.getHeight() - y - 1;
			return new Integer[] { (int) x.doubleValue(), (int) y.doubleValue() };
		};
		for (String n : cm.getNodes()) {
			double x = cm.getX(n);
			double y = cm.getY(n);
			// System.out.println(x);
			// System.out.println(y);
			x = trans.apply(x, y)[0];
			y = trans.apply(x, y)[1];
			Graphics2D grph = img.createGraphics();
			grph.setStroke(new BasicStroke(KPopularRunner.ROAD_WIDTH));
			log.append(KPopularRunner.ROAD_WIDTH + " setlinewidth\n");
			for (String k1 : cm.getConnections(n)) {
				if (cm.getStations().contains(n) || cm.getStations().contains(k1)) {
					continue;
				}
				int x0 = trans.apply(cm.getX(k1), cm.getY(k1))[0];
				int y0 = trans.apply(cm.getX(k1), cm.getY(k1))[1];
				if (cm.isBike(n, k1)) {
					grph.setColor(new Color(B));
					grph.drawLine((int) x, (int) y, x0, y0);
					log.append(String.valueOf(B)).append(" setcolor\n");
					log.append(String.valueOf(x)).append(" ").append(String.valueOf(y)).append(" ").append(String.valueOf(x0)).append(" ").append(String.valueOf(y0)).append(" drawLine\n");
				} else {
					grph.setColor(new Color(R));
					grph.drawLine((int) x, (int) y, x0, y0);
					log.append(String.valueOf(R)).append(" ").append(String.valueOf(x)).append(" ").append(String.valueOf(y)).append(" ").append(String.valueOf(x0)).append(" ").append(String.valueOf(y0)).append(" drawLine\n");
				}
			}
			// img.setRGB((int) x, (int) y, cm.hasBike(n) ? B : R);
		}
		// Function<Double, Integer> f = x -> (int) (x * 256);
		Graphics2D g2 = img.createGraphics();
		// AtomicInteger c = new AtomicInteger(0);
		UsageData.getG(intis, cm).stream().unordered().parallel().forEach(s -> {
			// String h = "" + c.incrementAndGet();
			// System.out.println(h + "S");
			String a0 = s.split("\0")[0];
			String b0 = s.split("\0")[1];
			String prev = null;
			LinkedList<String> path = cm.path(a0, b0);
			// System.out.println(a0);
			// System.out.println(b0);
			// System.out.println(path);
			// System.out.println(cm.path("387", "2006"));
			// System.out.println(s.replace("\0", "++"));
			// System.out.println(path);
			synchronized (g2) {
				g2.setColor(RT.get());
				g2.setStroke(new BasicStroke(KPopularRunner.PATH_WIDTH));
				log.append("startPath\n");
				log.append(KPopularRunner.PATH_WIDTH + " setlinewidth\n");
				for (String u : path) {
					if (prev != null) {
						Integer[] c1 = trans.apply(cm.getX(prev), cm.getY(prev));
						Integer[] c2 = trans.apply(cm.getX(u), cm.getY(u));
						// System.out.println(c1[0] + "}" + c1[1]);
						g2.drawLine(c1[0], c1[1], c2[0], c2[1]);
						log.append(String.valueOf(c1[0])).append(" ").append(String.valueOf(c1[1])).append(" ").append(String.valueOf(c2[0])).append(" ").append(String.valueOf(c2[1])).append(" drawLine\n");
					}
					prev = u;
				}
			}
			Integer[] c1 = trans.apply(cm.getX(path.getFirst()), cm.getY(path.getFirst()));
			Integer[] c2 = trans.apply(cm.getX(path.getLast()), cm.getY(path.getLast()));
			img.setRGB(c1[0], c1[1], S);
			img.setRGB(c2[0], c2[1], S);
			// System.out.println(h + "F");
		});
		for (String n : cm.getStations()) {
			double x = cm.getX(n);
			double y = cm.getY(n);
			x = trans.apply(x, y)[0];
			y = trans.apply(x, y)[1];
			Graphics2D grp = img.createGraphics();
			grp.setColor(new Color(S));
			grp.fillRect((int) (x - SZ), (int) (y - SZ), SZ * 2 + 1, SZ * 2 + 1);
			log.append(String.valueOf(x)).append(" ").append(String.valueOf(y)).append(" ").append(" drawStation\n");
			img.setRGB((int) x, (int) y, S);
		}
		double rotationRequired = Math.toRadians(90);
		double locationX = img.getWidth() / 2;
		double locationY = img.getHeight() / 2;
		AffineTransform tx = AffineTransform.getRotateInstance(rotationRequired, locationX,
				locationY);
		AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_BILINEAR);
		java.awt.image.BufferedImage img1 = op.filter(img, null);
		ImageIO.write(img1, "png", new File(outputFilename + ".png"));
		log.flush();
		log.close();
	}

	private static void drawTaxiBikeRoutes(CityMap cm, int k, String outputFilename)
			throws ClassNotFoundException, IOException {
		// System.out.println("Writing results to city_route_map.png");
		double dy = -74.068;
		double dx = 40.895;
		BufferedImage img = new BufferedImage(0xec0 * KPopularRunner.MULT_IMG / 0x4000,
				0xbc0 * KPopularRunner.MULT_IMG / 0x4000, BufferedImage.TYPE_INT_RGB);
		BiFunction<Double, Double, Integer[]> trans = (x, y) -> {
			y = y - dy;
			x = x - dx;
			x = -x;
			x *= KPopularRunner.MULT_IMG;
			y *= KPopularRunner.MULT_IMG;
			y = img.getHeight() - y - 1;
			x *= LAT_LONG;
			return new Integer[] { (int) x.doubleValue(), (int) y.doubleValue() };
		};
		for (String n : cm.getNodes()) {
			double x = cm.getX(n);
			double y = cm.getY(n);
			// System.out.println(x);
			// System.out.println(y);
			x = trans.apply(x, y)[0];
			y = trans.apply(x, y)[1];
			Graphics2D grph = img.createGraphics();
			grph.setStroke(new BasicStroke(KPopularRunner.ROAD_WIDTH));
			for (String k1 : cm.getConnections(n)) {
				if (cm.getStations().contains(n) || cm.getStations().contains(k1)) {
					continue;
				}
				int x0 = trans.apply(cm.getX(k1), cm.getY(k1))[0];
				int y0 = trans.apply(cm.getX(k1), cm.getY(k1))[1];
				if (cm.isBike(n, k1)) {
					grph.setColor(new Color(B));
					grph.drawLine((int) x, (int) y, x0, y0);
				} else {
					grph.setColor(new Color(R));
					grph.drawLine((int) x, (int) y, x0, y0);
				}
			}
			// img.setRGB((int) x, (int) y, cm.hasBike(n) ? B : R);
		}
		// Function<Double, Integer> f = x -> (int) (x * 256);
		Graphics2D g2 = img.createGraphics();
		// AtomicInteger c = new AtomicInteger(0);
		// AtomicInteger ai = new AtomicInteger(0);
		UsageData.getK(k, cm).forEach(s -> {
			// String h = "" + c.incrementAndGet();
			// System.out.println(h + "S");
			String a0 = s.split("\0")[0];
			String b0 = s.split("\0")[1];
			String prev = null;
			LinkedList<String> path = cm.path(a0, b0);
			// System.out.println(a0 + " <> " + b0);
			// System.out.println(cm.path(a0,b0));
			// System.out.println(cm.weightedLengthFriendliness(a0,b0));
			// System.out.println(ai.incrementAndGet());
			// System.out.println(a0);
			// System.out.println(b0);
			// System.out.println(path);
			// System.out.println(cm.path("387", "2006"));
			// System.out.println(s.replace("\0", "++"));
			// System.out.println(path);
			synchronized (g2) {
				g2.setColor(RT.get());
				g2.setStroke(new BasicStroke(KPopularRunner.PATH_WIDTH));
				for (String u : path) {
					if (prev != null) {
						Integer[] c1 = trans.apply(cm.getX(prev), cm.getY(prev));
						Integer[] c2 = trans.apply(cm.getX(u), cm.getY(u));
						// System.out.println(c1[0] + "}" + c1[1]);
						g2.drawLine(c1[0], c1[1], c2[0], c2[1]);
					}
					prev = u;
				}
			}
			Integer[] c1 = trans.apply(cm.getX(path.getFirst()), cm.getY(path.getFirst()));
			Integer[] c2 = trans.apply(cm.getX(path.getLast()), cm.getY(path.getLast()));
			img.setRGB(c1[0], c1[1], S);
			img.setRGB(c2[0], c2[1], S);
			// System.out.println(h + "F");
		});
		CityMap cm1 = cm.reset(1.0, 1);
		UsageData.getKTaxi(k, cm).forEach(s -> {
			// String h = "" + c.incrementAndGet();
			// System.out.println(h + "S");
			String a0 = s.split("\0")[0];
			String b0 = s.split("\0")[1];
			String prev = null;
			LinkedList<String> path = cm1.path(a0, b0);
			// System.out.println(a0 + " <> " + b0);
			// System.out.println(cm.path(a0,b0));
			// System.out.println(cm.weightedLengthFriendliness(a0,b0));
			// System.out.println(ai.incrementAndGet());
			// System.out.println(a0);
			// System.out.println(b0);
			// System.out.println(path);
			// System.out.println(cm.path("387", "2006"));
			// System.out.println(s.replace("\0", "++"));
			// System.out.println(path);
			synchronized (g2) {
				g2.setColor(TRT.get());
				g2.setStroke(new BasicStroke(KPopularRunner.PATH_WIDTH));
				for (String u : path) {
					if (prev != null) {
						Integer[] c1 = trans.apply(cm.getX(prev), cm.getY(prev));
						Integer[] c2 = trans.apply(cm.getX(u), cm.getY(u));
						// System.out.println(c1[0] + "}" + c1[1]);
						g2.drawLine(c1[0], c1[1], c2[0], c2[1]);
					}
					prev = u;
				}
			}
			Integer[] c1 = trans.apply(cm.getX(path.getFirst()), cm.getY(path.getFirst()));
			Integer[] c2 = trans.apply(cm.getX(path.getLast()), cm.getY(path.getLast()));
			img.setRGB(c1[0], c1[1], S);
			img.setRGB(c2[0], c2[1], S);
			// System.out.println(h + "F");
		});
		for (String n : cm.getStations()) {
			double x = cm.getX(n);
			double y = cm.getY(n);
			x = trans.apply(x, y)[0];
			y = trans.apply(x, y)[1];
			Graphics2D grp = img.createGraphics();
			grp.setColor(new Color(S));
			grp.fillRect((int) (x - SZ), (int) (y - SZ), SZ * 2 + 1, SZ * 2 + 1);
			img.setRGB((int) x, (int) y, S);
		}
		double rotationRequired = Math.toRadians(90);
		double locationX = img.getWidth() / 2;
		double locationY = img.getHeight() / 2;
		AffineTransform tx = AffineTransform.getRotateInstance(rotationRequired, locationX,
				locationY);
		AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_BILINEAR);
		java.awt.image.BufferedImage img1 = op.filter(img, null);
		ImageIO.write(img1, "png", new File(outputFilename + ".png"));
	}

	private static void drawCity(CityMap cm) throws IOException {
		System.out.println("Writing results to city_map.png");
		double dy = -74.068;
		double dx = 40.895;
		BufferedImage img = new BufferedImage(0xec0 * KPopularRunner.MULT_IMG / 0x4000,
				0xbc0 * KPopularRunner.MULT_IMG / 0x4000, BufferedImage.TYPE_INT_RGB);
		BiFunction<Double, Double, Integer[]> trans = (x, y) -> {
			y = y - dy;
			x = x - dx;
			x = -x;
			x *= KPopularRunner.MULT_IMG;
			y *= KPopularRunner.MULT_IMG;
			y = img.getHeight() - y - 1;
			x *= LAT_LONG;
			return new Integer[] { (int) x.doubleValue(), (int) y.doubleValue() };
		};
		for (String n : cm.getNodes()) {
			double x = cm.getX(n);
			double y = cm.getY(n);
			// System.out.println(x);
			// System.out.println(y);
			x = trans.apply(x, y)[0];
			y = trans.apply(x, y)[1];
			Graphics2D grph = img.createGraphics();
			grph.setStroke(new BasicStroke(KPopularRunner.ROAD_WIDTH));
			for (String k1 : cm.getConnections(n)) {
				if (cm.getStations().contains(n) || cm.getStations().contains(k1)) {
					continue;
				}
				int x0 = trans.apply(cm.getX(k1), cm.getY(k1))[0];
				int y0 = trans.apply(cm.getX(k1), cm.getY(k1))[1];
				if (cm.isBike(n, k1)) {
					grph.setColor(new Color(B));
					grph.drawLine((int) x, (int) y, x0, y0);
				} else {
					grph.setColor(new Color(R));
					grph.drawLine((int) x, (int) y, x0, y0);
				}
			}
			// img.setRGB((int) x, (int) y, cm.hasBike(n) ? B : R);
		}
		for (String n : cm.getStations()) {
			double x = cm.getX(n);
			double y = cm.getY(n);
			x = trans.apply(x, y)[0];
			y = trans.apply(x, y)[1];
			Graphics2D grp = img.createGraphics();
			grp.setColor(new Color(S));
			grp.fillRect((int) (x - SZ), (int) (y - SZ), SZ * 2 + 1, SZ * 2 + 1);
			img.setRGB((int) x, (int) y, S);
		}
		double rotationRequired = Math.toRadians(90);
		double locationX = img.getWidth() / 2;
		double locationY = img.getHeight() / 2;
		AffineTransform tx = AffineTransform.getRotateInstance(rotationRequired, locationX,
				locationY);
		AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_BILINEAR);
		java.awt.image.BufferedImage img1 = op.filter(img, null);
		ImageIO.write(img1, "png", new File("city_map.png"));
	}

	@SuppressWarnings("unused")
	private static void lanes(CityMap cm, Consumer<Double> onUpdate, int res, double blld) {
		int[][] pix = null;
		try {
			pix = compute(new File(CityMapInitializer.class.getResource("bikelanes.png").toURI()));
		} catch (URISyntaxException e) {
		}
		double x = -74.284558, y = 40.887235, dx = 0.527402, dy = -0.318511;
		AtomicInteger cnt = new AtomicInteger(0);
		onUpdate.accept(0.0);
		int v = 0;
		List<String> added = new ArrayList<>();
		for (int i = 0; i < pix.length; i++) {
			for (int j = 0; j < pix[0].length; j++) {
				Color c = new Color(pix[i][j]);
				if (c.getGreen() > c.getRed() && c.getGreen() > c.getBlue() && c.getRed() < 100
						&& v++ % res == 0) {
					added.add(add(x + dx * i / pix.length, y + dy * j / pix[0].length, cm));
				}
			}
		}
		added.stream().unordered().parallel().forEach(k -> {
			web(k, cm, blld);
			onUpdate.accept(((double) (cnt.incrementAndGet())) / added.size());
		});
	}

	private static int[][] compute(File file) {
		try {
			java.awt.image.BufferedImage img = ImageIO.read(file);
			Raster raster = img.getData();
			int w = raster.getWidth(), h = raster.getHeight();
			int pixels[][] = new int[w][h];
			for (int x = 0; x < w; x++) {
				for (int y = 0; y < h; y++) {
					pixels[x][y] = img.getRGB(x, y);
				}
			}
			return pixels;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private static int nnum = 0;

	private static String add(double x, double y, CityMap mp) {
		mp.addNode("" + --nnum, y, x);
		return "" + nnum;
	}

	private static void web(String s, CityMap mp, double blld) {
		for (String n1 : mp.getNodes()) {
			if (mp.distance(n1, s) < blld) {
				mp.roadConnect(n1, s);
			}
		}
	}
}
