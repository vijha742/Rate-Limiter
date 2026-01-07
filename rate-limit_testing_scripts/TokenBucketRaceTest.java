import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class TokenBucketRaceTest {

    private static final String URL = "http://localhost:8080/api/test";
    private static final int USERS = 30;

    public static void main(String[] args) throws Exception {

        ExecutorService executor = Executors.newFixedThreadPool(USERS);
        CountDownLatch ready = new CountDownLatch(USERS);
        CountDownLatch start = new CountDownLatch(1);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        HttpClient client = HttpClient.newHttpClient();

        for (int i = 0; i < USERS; i++) {
            executor.submit(
                    () -> {
                        try {
                            ready.countDown(); // signal ready
                            start.await(); // wait for simultaneous start

                            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(URL)).GET().build();

                            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                            if (response.statusCode() == 200) {
                                success.incrementAndGet();
                            } else if (response.statusCode() == 429) {
                                rejected.incrementAndGet();
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
        }

        ready.await(); // wait till all threads are ready
        start.countDown(); // fire all at once

        executor.shutdown();

        Thread.sleep(1000); // allow completion

        System.out.println("SUCCESS = " + success.get());
        System.out.println("REJECTED = " + rejected.get());
    }
}
