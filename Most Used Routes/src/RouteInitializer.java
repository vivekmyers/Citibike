import static java.util.stream.Collectors.joining;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import citibike.model.CityMap;

public class RouteInitializer {

	private RouteInitializer() {
	}

	public static void main(String[] args) {
	}

	public static CityMap init(Consumer<Double> onUpdate, int res, double blld, double prob)
			throws IOException, URISyntaxException {
		if (res <= 0)
			throw new IllegalArgumentException("Resolution must be on (0,1].");
		String data = Files
				.newBufferedReader(
						Paths.get(RouteInitializer.class.getResource("stations.json").toURI()))
				.lines().collect(joining());
		Gson gson = new Gson();
		Map<String, Object> st = gson.fromJson(data, new TypeToken<Map<String, Object>>() {
		}.getType());
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> stat = (List<Map<String, Object>>) st.get("stationBeanList");
		CityMap cm = new CityMap();
		for (Map<String, Object> mp : stat) {
			double x = (double) mp.get("latitude");
			double y = (double) mp.get("longitude");
			if (y < -73.967247 && x > 40.699372 && y > -74.024346)
				if (x > 40.705638 || y < -73.999070)
					if (x > 40.712010 || y < -73.975296) {
						if (Math.random() < prob)
							cm.addStation(String.valueOf((int) ((double) mp.get("id"))), x, y);
						else
							cm.addNode(String.valueOf((int) ((double) mp.get("id"))), x, y);
					}
		}
		String connections = Files
				.newBufferedReader(
						Paths.get(RouteInitializer.class.getResource("distances07.json").toURI()))
				.lines().collect(joining());
		Map<String, Map<String, Integer>> con = gson.fromJson(connections,
				new TypeToken<Map<String, Map<String, Integer>>>() {
				}.getType());
		for (Entry<String, Map<String, Integer>> j : con.entrySet()) {
			for (String z : j.getValue().keySet()) {
				if (cm.contains(j.getKey()) && cm.contains(z))
					cm.roadConnect(j.getKey(), z);
			}
		}
		lanes(cm, onUpdate, res, blld);
		image(cm);
		return cm;
	}

	private static void image(CityMap cm) throws IOException {
		System.out.println("Writing results to city_map.png");
		BufferedImage img = new BufferedImage(319, 528, BufferedImage.TYPE_INT_RGB);
		for (String n : cm.getNodes()) {
			double x = cm.getX(n);
			double y = cm.getY(n);
			y = y - -74.284558;
			x = x - 40.887235;
			x = -x;
			x *= 1000;
			y *= 1000;
			y = img.getHeight() - y - 1;
			img.setRGB((int) x, (int) y, Color.GREEN.getRGB());
		}
		for (String n : cm.getStations()) {
			double x = cm.getX(n);
			double y = cm.getY(n);
			y = y - -74.284558;
			x = x - 40.887235;
			x = -x;
			x *= 1000;
			y *= 1000;
			y = img.getHeight() - y - 1;
			img.setRGB((int) x, (int) y, Color.BLUE.getRGB());
		}
		double rotationRequired = Math.toRadians(90);
		double locationX = img.getWidth() / 2;
		double locationY = img.getHeight() / 2;
		AffineTransform tx = AffineTransform.getRotateInstance(rotationRequired, locationX,
				locationY);
		AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_BILINEAR);
		BufferedImage img1 = op.filter(img, null);
		ImageIO.write(img1, "png", new File("city_map.png"));
	}

	private static void lanes(CityMap cm, Consumer<Double> onUpdate, int res, double blld) {
		int[][] pix = null;
		try {
			pix = compute(new File(RouteInitializer.class.getResource("bikelanes.png").toURI()));
		} catch (URISyntaxException e) {
		}
		double x = -74.284558, y = 40.887235, dx = 0.527402, dy = -0.318511;
		AtomicInteger cnt = new AtomicInteger(0);
		onUpdate.accept(0.0);
		int v = 0;
		List<String> added = new ArrayList<String>();
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
			BufferedImage img = ImageIO.read(file);
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
				mp.bikeConnect(n1, s);
			}
		}
	}
}
