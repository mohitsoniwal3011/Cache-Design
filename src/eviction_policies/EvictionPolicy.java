package eviction_policies;

public interface EvictionPolicy<K> {
    void keyAdded(K key);
    void keyAccessedOrUpdated(K key);
    void keyRemoved(K key);
    K evictKey();
}
