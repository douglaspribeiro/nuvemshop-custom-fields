import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class Healthcheck {
    public static void main(String[] args) {
        boolean probe = args.length > 0 && "--probe".equals(args[0]);
        String url = args.length > (probe ? 1 : 0) ? args[probe ? 1 : 0] : "http://localhost:8080/actuator/health";
        long started = System.nanoTime();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (probe) {
                System.out.printf(java.util.Locale.ROOT, "%d %.6f%n", response.statusCode(),
                        (System.nanoTime() - started) / 1_000_000_000.0);
            }
            if (response.statusCode() >= 200 && response.statusCode() < (probe ? 400 : 300)) {
                return;
            }
            System.err.println("Healthcheck failed: HTTP " + response.statusCode());
        } catch (Exception ex) {
            System.err.println("Healthcheck failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
        System.exit(1);
    }
}
