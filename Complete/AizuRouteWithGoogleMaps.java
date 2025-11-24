import java.net.*;
import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import com.google.gson.*;

// google maps APIで全地点の組み合わせ28ペアの経路取得
public class AizuRouteWithGoogleMaps {
    static final String API_KEY = "ここにAPIキーを入れる";

    static class Location {
        String name;
        double lat, lon;

        Location(String name, double lat, double lon) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }
    }

    public static void main(String[] args) throws Exception {
        Location[] locations = {
                new Location("会津若松駅", 37.5080, 139.9304),
                new Location("鶴ヶ城", 37.4883, 139.9296),
                new Location("東山温泉", 37.4803, 139.9603),
                new Location("飯盛山", 37.5057, 139.9559),
                new Location("七日町通り", 37.4972, 139.9283),
                new Location("さざえ堂", 37.5048, 139.9538),
                new Location("会津大学", 37.5241, 139.9374),
                new Location("武家屋敷", 37.4853, 139.9536)
        };

        int n = locations.length;
        Map<String, List<double[]>> allRoutes = new HashMap<>();
        double[][] distances = new double[n][n];
        // Mapは0→１など文字列にアクセス.allRoutesは0→1などに対応したlat,lonを入れていく

        // 全組み合わせの経路get!
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                System.out.printf("場所: %s → %s\n",
                        locations[i].name, locations[j].name);

                List<double[]> route = getRealRoute(locations[i], locations[j]);

                if (route != null) {
                    String key = i + "-" + j;
                    allRoutes.put(key, route);
                    // routeがnullじゃなければallRoutesに0-1などをput

                    // 距離を計算
                    double totalDist = calculateRouteDistance(route);
                    distances[i][j] = totalDist;
                    distances[j][i] = totalDist;

                    System.out.printf("距離: %.2f km\n",
                            totalDist);
                } else {
                    System.out.println("  → 取得失敗");
                    distances[i][j] = Double.POSITIVE_INFINITY;
                    distances[j][i] = Double.POSITIVE_INFINITY;
                } // 失敗のとき無限のほうが処理しやすい

                // APIサーバーの負荷の保護のために1秒確保
                Thread.sleep(1000);
            }
        }

        // 同じ場所同士は消す
        for (int i = 0; i < n; i++) {
            distances[i][i] = 0;
        }

        // JSONファイルにデータをしまっとく。javaとjsファイルは直接やり取りできないため
        System.out.println("\nJSONファイルに保存");
        saveToJSON(locations, distances, allRoutes);
        System.out.println("完了!aizu_data.json を生成しました");
    }

    // 経路の総距離を計算
    static double calculateRouteDistance(List<double[]> route) {
        double total = 0;
        for (int i = 0; i < route.size() - 1; i++) {
            double[] p1 = route.get(i);
            double[] p2 = route.get(i + 1);

            double dx = (p2[1] - p1[1]) * 111.0 * Math.cos(Math.toRadians(p1[0]));
            double dy = (p2[0] - p1[0]) * 111.0;
            total += Math.hypot(dx, dy);
        }
        return total;
    }

    // Google Mapsで実際の経路を取得
    static List<double[]> getRealRoute(Location start, Location end) throws Exception {
        String urlStr = String.format(
                "　　",
                start.lat, start.lon, end.lat, end.lon, API_KEY);

        URL url = new URL(urlStr);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");

        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            response.append(line);
        }
        in.close();

        JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();

        String status = json.get("status").getAsString();
        if (!status.equals("OK")) {
            System.err.println("APIエラー: " + status);
            return null;
        }

        JsonArray routes = json.getAsJsonArray("routes");
        if (routes.size() == 0)
            return null;

        String polyline = routes.get(0).getAsJsonObject()
                .getAsJsonObject("overview_polyline")
                .get("points").getAsString();

        return decodePolyline(polyline);
    }

    // Googleの特殊な文字列をlat,lonのリストに変換(デコード)
    static List<double[]> decodePolyline(String encoded) {
        List<double[]> points = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            points.add(new double[] { lat / 1E5, lng / 1E5 });
        }

        return points;
    }

    // JSONに保存
    static void saveToJSON(Location[] locations, double[][] distances,
            Map<String, List<double[]>> routes) throws Exception {
        PrintWriter out = new PrintWriter(
                new OutputStreamWriter(
                        new FileOutputStream("aizu_data.json"),
                        StandardCharsets.UTF_8));

        out.println("{");

        // locations位置
        out.println("  \"locations\": [");
        for (int i = 0; i < locations.length; i++) {
            out.printf("    {\"name\": \"%s\", \"lat\": %.6f, \"lon\": %.6f}%s\n",
                    locations[i].name, locations[i].lat, locations[i].lon,
                    (i < locations.length - 1) ? "," : "");
        }
        out.println("  ],");

        // distances距離
        out.println("  \"distances\": [");
        for (int i = 0; i < distances.length; i++) {
            out.print("    [");
            for (int j = 0; j < distances[i].length; j++) {
                if (distances[i][j] == Double.POSITIVE_INFINITY) {
                    out.print("null");
                } else {
                    out.printf("%.2f", distances[i][j]);
                }
                if (j < distances[i].length - 1)
                    out.print(", ");
            }
            out.println(i < distances.length - 1 ? "]," : "]");
        }
        out.println("  ],");

        // routes 実際の道路
        out.println("  \"routes\": {");
        int count = 0;
        int total = routes.size();

        for (Map.Entry<String, List<double[]>> entry : routes.entrySet()) {
            out.printf("    \"%s\": [", entry.getKey());

            List<double[]> coords = entry.getValue();
            for (int i = 0; i < coords.size(); i++) {
                out.printf("[%.6f, %.6f]", coords.get(i)[0], coords.get(i)[1]);
                if (i < coords.size() - 1)
                    out.print(", ");
            }

            out.print("]");
            count++;
            if (count < total)
                out.println(",");
            else
                out.println();
        }
        out.println("  }");
        out.println("}");

        out.close();
    }
}
