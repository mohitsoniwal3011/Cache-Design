package storage;

public interface Storage<K,V> {
    V get(K key);
    V remove(K key);
    boolean contains(K key);
    void put(K key, V value);
    int size();
}
