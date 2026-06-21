package eviction_policies;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class LFUEvictionPolicy<K> implements EvictionPolicy<K>{

    private static class DLL<T>{

        private final Node<T> head;
        private final Node<T> tail;


        public DLL() {
            this.head = new Node<>(null);
            this.tail = new Node<>(null);
            head.next = tail;
            tail.prev = head;
        }

        public void detachNode(Node<T> node){
            node.prev.next = node.next;
            node.next.prev = node.prev;
            node.prev = node.next = null;
        }

        public Node<T> detachAndLastNode(){
            Node<T> node = tail.prev;
            detachNode(node);
            return node;
        }

        public void addFront(Node<T> node){
            Node<T> temp= head.next;
            head.next = node;
            node.prev = head;
            node.next= temp;
            temp.prev = node;
        }

        public boolean isEmpty(){
            return head.next == tail && tail.prev == head;
        }
    }

    private final Map<K, Node<K>> keyToNode;
    private final Map<K , Integer> keyToFreq;
    private final Map<Integer, DLL<K>> freqToList;
    private int minFreq = -1;

    public LFUEvictionPolicy() {
        this.keyToFreq = new HashMap<>();
        this.keyToNode = new HashMap<>();
        this.freqToList = new HashMap<>();
    }

    private static class Node<T>{
        T key;
        Node<T> next;
        Node<T> prev;

        public Node(T key) {
            this.key = key;
        }
    }

    @Override
    public void keyAdded(K key) {
        if(keyToNode.containsKey(key)){
           return; // this method's responsibility is to add a new key not update older one
        }
        Node<K> newNode = new Node<>(key);
        keyToNode.put(key,newNode);
        keyToFreq.put(key,1 );
        freqToList.computeIfAbsent(1, (k) -> new DLL<>());
        freqToList.get(1).addFront(newNode);
        minFreq = 1;
    }

    @Override
    public void keyAccessedOrUpdated(K key) {
        if(!keyToNode.containsKey(key)){
            return; // this method's responsibility is to update the old key, not add new one;
        }
        Node<K> node = keyToNode.get(key);
        int freq = keyToFreq.get(key);
        DLL<K> dll = freqToList.get(freq);
        dll.detachNode(node);
        if(freq == minFreq && dll.isEmpty()){
            minFreq++;
            freqToList.remove(freq);
        }
        freqToList.computeIfAbsent(freq +1 , (k) -> new DLL<>());
        freqToList.get(freq+1).addFront(node);
        keyToFreq.remove(key);
        keyToFreq.put(key, freq +1);
    }

    @Override
    public void keyRemoved(K key) {
        if(!keyToNode.containsKey(key)){
            return;
        }
        Node<K> node = keyToNode.get(key);
        int freq = keyToFreq.get(key);
        DLL<K> dll = freqToList.get(freq);
        dll.detachNode(node);
        keyToFreq.remove(key);
        keyToNode.remove(key);
        if(dll.isEmpty()){
            freqToList.remove(freq);
        }
        if(freq == minFreq){
            minFreq = freqToList.isEmpty() ? -1 : Collections.min(freqToList.keySet());
        }

    }

    @Override
    public K evictKey() {
        if(keyToNode.isEmpty()){
            return null;
        }
       Node<K> node = freqToList.get(minFreq).detachAndLastNode();
       keyToNode.remove(node.key);
       keyToFreq.remove(node.key);
       if(freqToList.get(minFreq).isEmpty()){
           freqToList.remove(minFreq);
       }
       return node.key;
    }
}
