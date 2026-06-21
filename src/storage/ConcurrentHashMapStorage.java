package storage;

import java.util.concurrent.ConcurrentHashMap;

public class ConcurrentHashMapStorage<K,V> implements Storage<K,V>{

    private final ConcurrentHashMap<K,V> map;

    public ConcurrentHashMapStorage(ConcurrentHashMap<K, V> map) {
        this.map = map;
    }

    @Override
    public V get(K key) {
        return map.get(key);
    }

    @Override
    public V  remove(K key) {
        return map.remove(key);
    }

    @Override
    public boolean contains(K key) {
        return map.containsKey(key);
    }

    @Override
    public void put(K key, V value) {
        map.put(key, value);
    }

    @Override
    public int size() {
        return map.size();
    }
}
