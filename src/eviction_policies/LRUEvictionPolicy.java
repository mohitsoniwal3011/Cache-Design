package eviction_policies;

import java.util.HashMap;
import java.util.Map;

public class LRUEvictionPolicy<K> implements EvictionPolicy<K>{

    public static class Node<T> {

        private final T key;
        Node<T> next;
        Node<T> prev;
        public Node(T key) {
            this.key = key;
        }

    }

    private final Node<K> head;
    private final Node<K> tail;
    private final Map<K, Node<K>> map;
    public LRUEvictionPolicy() {
        this.map = new HashMap<>();
        head = new Node<>(null); // dummy head
        tail = new Node<>(null);
        head.next = tail;
        tail.prev = head;
    }

    @Override
    public void keyAdded(K key) {
        Node<K> newNode = new Node<>(key);
        addFront(newNode);
        map.put(key, newNode);
    }

    @Override
    public void keyAccessedOrUpdated(K key) {
        //first remove it from its current place
        Node<K> accessedNode = map.get(key);
        if(accessedNode == null) {
            return;
        }
        removeNode(accessedNode);

        // then insert at front
        addFront(accessedNode);
    }

    @Override
    public void keyRemoved(K key) {
        Node<K> node = map.get(key);
        if(node == null){
            return;
        }
        removeNode(node);
        map.remove(key);
    }

    @Override
    public K evictKey() {
        if(map.isEmpty()){
            return null;
        }
        Node<K> node = tail.prev;
        removeNode(node);
        map.remove(node.key);
        return node.key;
    }

    void addFront(Node<K> node){
        Node<K> temp = head.next;
        head.next = node;
        node.prev = head;
        node.next = temp;
        temp.prev = node;
    }

    void removeNode(Node<K> node){
        node.prev.next =node.next;
        node.next.prev = node.prev;
    }


}
