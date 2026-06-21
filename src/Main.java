import cache.Cache;
import eviction_policies.LFUEvictionPolicy;
import eviction_policies.LRUEvictionPolicy;
import storage.ConcurrentHashMapStorage;

import java.util.concurrent.ConcurrentHashMap;

public class Main {

    public static void main(String[] args) throws InterruptedException {
        testLRU();
        System.out.println("\n----------------------------------------\n");
        testLFU();
        System.out.println("\n----------------------------------------\n");
        testMultiThreaded();
    }

    private static void testLRU() {
        System.out.println("=== LRU Cache Test (capacity=3) ===");
        Cache<String, Integer> cache = new Cache<>(
                new ConcurrentHashMapStorage<>(new ConcurrentHashMap<>()),
                new LRUEvictionPolicy<>(),
                3
        );

        cache.put("A", 1);
        cache.put("B", 2);
        cache.put("C", 3);
        System.out.println("Put A=1, B=2, C=3");

        System.out.println("Get A: " + cache.get("A")); // hit, A becomes MRU
        cache.put("D", 4); // B should be evicted (LRU)
        System.out.println("Put D=4 → B should be evicted");

        System.out.println("Get B: " + cache.get("B")); // miss
        System.out.println("Get C: " + cache.get("C")); // hit
        System.out.println("Get D: " + cache.get("D")); // hit

        cache.remove("A");
        System.out.println("Remove A");
        System.out.println("Get A: " + cache.get("A")); // miss
    }

    private static void testLFU() {
        System.out.println("=== LFU Cache Test (capacity=3) ===");
        Cache<String, Integer> cache = new Cache<>(
                new ConcurrentHashMapStorage<>(new ConcurrentHashMap<>()),
                new LFUEvictionPolicy<>(),
                3
        );

        cache.put("A", 1);
        cache.put("B", 2);
        cache.put("C", 3);
        System.out.println("Put A=1, B=2, C=3");

        cache.get("A"); // freq A=2
        cache.get("A"); // freq A=3
        cache.get("B"); // freq B=2
        System.out.println("Access A twice, B once → freq: A=3, B=2, C=1");

        cache.put("D", 4); // C should be evicted (lowest freq=1)
        System.out.println("Put D=4 → C should be evicted (freq=1)");

        System.out.println("Get C: " + cache.get("C")); // miss
        System.out.println("Get A: " + cache.get("A")); // hit
        System.out.println("Get B: " + cache.get("B")); // hit
        System.out.println("Get D: " + cache.get("D")); // hit
    }

    private static void testMultiThreaded() throws InterruptedException {
        System.out.println("=== Multi-threaded Test (capacity=5) ===");
        Cache<String, Integer> cache = new Cache<>(
                new ConcurrentHashMapStorage<>(new ConcurrentHashMap<>()),
                new LRUEvictionPolicy<>(),
                5
        );

        Thread writer1 = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                cache.put("W1-" + i, i);
                System.out.println("Writer1 put W1-" + i);
            }
        });

        Thread writer2 = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                cache.put("W2-" + i, i * 10);
                System.out.println("Writer2 put W2-" + i);
            }
        });

        Thread reader = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                Integer val = cache.get("W1-" + i);
                System.out.println("Reader get W1-" + i + ": " + (val != null ? val : "MISS"));
            }
        });

        writer1.start();
        writer2.start();
        reader.start();

        writer1.join();
        writer2.join();
        reader.join();

        System.out.println("Multi-threaded test done — no exceptions = lock is working");
    }
}