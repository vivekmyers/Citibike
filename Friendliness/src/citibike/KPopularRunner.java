package citibike;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.TimeZone;

import citibike.data.CityMapInitializer;
import citibike.data.UsageData;

public class KPopularRunner {

    private static final int HEAVY = 200;
    private static final double DISP = 4;
    public static final float PATH_WIDTH = 11f;
    public static final float ROAD_WIDTH = 2f;
    public static final int MULT_IMG = 0x8000;
    public static final double LINK_DIST = 0.0006;

    public static void main(String[] args)
            throws ClassNotFoundException, IOException, URISyntaxException {
        for (int i = 0; i < 51; i++) {
            System.out.print('-');
        }
        System.out.println();
        for (int i : new int[]{14, 17}) {
            final int h = i;
            UsageData.serializeData(s -> isHour(s, h));
            CityMapInitializer.altMapRoutes(HEAVY, " FF th200 Heavily used - hour " + i);
            disp();
        }
        UsageData.serializeData(KPopularRunner::weekend);
        for (int k : new int[]{50}) {
            CityMapInitializer.mapRoutes(k, "../" + k + " FF most used - weekend");
            disp();
        }
        UsageData.serializeData(KPopularRunner::weekday);
        for (int k : new int[]{50}) {
            CityMapInitializer.mapRoutes(k, "../" + k + " FF most used - weekday");
            disp();
        }
    }

    private static void altGif() throws IOException, URISyntaxException, ClassNotFoundException {
        for (int i = 0; i < 24; i++) {
            final int h = i;
            UsageData.serializeData(s -> isHour(s, h));
            CityMapInitializer.altMapRoutes(HEAVY, "Heavily used - hour " + i);
            disp();
        }
    }

    private static void makeGif() throws IOException, URISyntaxException, ClassNotFoundException {
        for (int k : new int[]{10, 50, 200}) {
            for (int i = 0; i < 24; i++) {
                final int h = i;
                UsageData.serializeData(s -> isHour(s, h));
                CityMapInitializer.mapRoutes(k, k + " most used - hour " + i);
                disp();
            }
        }
    }

    private static void permuteTimes()
            throws IOException, URISyntaxException, ClassNotFoundException {
        {
            UsageData.serializeData(KPopularRunner::morning);
            for (int k : new int[]{50, 200}) {
                CityMapInitializer.mapRoutes(k, k + " most used - morning");
                disp();
            }
            UsageData.serializeData(KPopularRunner::afternoon);
            for (int k : new int[]{50, 200}) {
                CityMapInitializer.mapRoutes(k, k + " most used - afternoon");
                disp();
            }
            UsageData.serializeData(KPopularRunner::weekend);
            for (int k : new int[]{50, 200}) {
                CityMapInitializer.mapRoutes(k, k + " most used - weekend");
                disp();
            }
            UsageData.serializeData(KPopularRunner::weekday);
            for (int k : new int[]{50, 200}) {
                CityMapInitializer.mapRoutes(k, k + " most used - weekday");
                disp();
            }
        }
        {
            UsageData.serializeData(s -> morning(s) && weekday(s));
            for (int k : new int[]{10, 50, 200}) {
                CityMapInitializer.mapRoutes(k, k + " most used - morning and weekday");
                disp();
            }
            UsageData.serializeData(s -> afternoon(s) && weekday(s));
            for (int k : new int[]{10, 50, 200}) {
                CityMapInitializer.mapRoutes(k, k + " most used - afternoon and weekday");
                disp();
            }
            UsageData.serializeData(s -> morning(s) && weekend(s));
            for (int k : new int[]{10, 50, 200}) {
                CityMapInitializer.mapRoutes(k, k + " most used - morning and weekend");
                disp();
            }
            UsageData.serializeData(s -> afternoon(s) && weekend(s));
            for (int k : new int[]{10, 50, 200}) {
                CityMapInitializer.mapRoutes(k, k + " most used - afternoon and weekend");
                disp();
            }
        }
        {
            UsageData.serializeData(KPopularRunner::summer);
            for (int k : new int[]{10, 50, 200}) {
                CityMapInitializer.mapRoutes(k, k + " most used - summer");
                disp();
            }
            UsageData.serializeData(s -> !summer(s));
            for (int k : new int[]{10, 50, 200}) {
                CityMapInitializer.mapRoutes(k, k + " most used - not summer");
                disp();
            }
            UsageData.serializeData(s -> true);
            for (int k : new int[]{10, 50, 200}) {
                CityMapInitializer.mapRoutes(k, k + " most used - overall");
                disp();
            }
        }
    }

    private static volatile int cnt = 0;
    private static double at = 0;

    public static void disp1(double i) {
        if (i * 50 >= cnt) {
            System.out.print("\u2588");
            cnt++;
        }
    }

    public static void disp() {
        double i = at++ / DISP;
        for (int z = 0; z < 60; z++) {
            disp1(i);
        }
    }

    private static Calendar parse(String s) {
        Calendar pms = Calendar.getInstance(TimeZone.getTimeZone("EST"));
        int[] c = Arrays.stream(s.split("\\/|\\s|:")).mapToInt(Integer::parseInt).toArray();
        pms.set(c[2], c[0] - 1, c[1], c[3], c[4], c[5]);
        // System.out.println(pms.getTime());
        return pms;
    }

    private static Boolean isHour(String s, int h) {
        return parse(s).get(Calendar.HOUR_OF_DAY) == h;
    }

    private static Boolean morning(String s) {
        return parse(s).get(Calendar.AM_PM) == Calendar.AM;
    }

    private static Boolean summer(String s) {
        return Arrays.asList(Calendar.JUNE, Calendar.JULY, Calendar.AUGUST)
                .contains(parse(s).get(Calendar.MONTH));
    }

    private static Boolean afternoon(String s) {
        return parse(s).get(Calendar.AM_PM) == Calendar.PM;
    }

    private static Boolean weekend(String s) {
        return parse(s).get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
                || parse(s).get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY;
    }

    private static Boolean weekday(String s) {
        return !weekend(s);
    }
}
