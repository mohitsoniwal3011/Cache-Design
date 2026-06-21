package cache;

import eviction_policies.EvictionPolicy;
import storage.Storage;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Cache<K,V> {

    private final ReentrantReadWriteLock readWriteLock;
    private final Storage<K,V> storage;
    private final EvictionPolicy<K> evictionPolicy;
    private final int capacity;

    public Cache(Storage<K, V> storage, EvictionPolicy<K> evictionPolicy, int capacity) {
        this.readWriteLock = new ReentrantReadWriteLock();
        this.storage = storage;
        this.evictionPolicy = evictionPolicy;
        this.capacity = capacity;
    }

    public V get(K key){
        V value;
        try{
            readWriteLock.readLock().lock();
            value = storage.get(key);
        }finally{
            readWriteLock.readLock().unlock();
        }
        if(value == null){
            return null;
        }

        try{
            readWriteLock.writeLock().lock();
            // check during the context switch the key got removed or not
            if(storage.contains(key)){
                evictionPolicy.keyAccessedOrUpdated(key);
                return storage.get(key);
            }
            return null; // somebody deleted this right under our nose, now we are helpless but to return null
        }finally{
            readWriteLock.writeLock().unlock();
        }
    }

    public void put(K key,V value){
        try{
            readWriteLock.writeLock().lock();

            if(storage.contains(key)){
                this.evictionPolicy.keyAccessedOrUpdated(key);
                this.storage.put(key, value);
                return;
            }

            if(this.storage.size() == this.capacity){
                K evictedKey = this.evictionPolicy.evictKey();
                if(evictedKey != null){
                    storage.remove(evictedKey);
                }
            }
            this.storage.put(key, value);
            this.evictionPolicy.keyAdded(key);
        }finally{
            readWriteLock.writeLock().unlock();
        }
    }

    public boolean contains(K key){
        try{
            this.readWriteLock.readLock().lock();
            return this.storage.contains(key);
        }finally{
            this.readWriteLock.readLock().unlock();
        }
    }

    public V remove(K key){
        try{
            readWriteLock.writeLock().lock();
            if(!this.storage.contains(key)){
                return null;
            }
            this.evictionPolicy.keyRemoved(key);
            return this.storage.remove(key);
        }finally{
            readWriteLock.writeLock().unlock();
        }
    }
}
