# Cache Design (LLD)

## What is a Cache?

A cache is a layer between the application and the database.

We store values temporarily to avoid hitting the database repeatedly, reducing database load and improving response time.

### Basic Operations

- `get(key)`
- `put(key, value)`
- `remove(key)`
- `contains(key)`

Since a cache cannot store infinite data, it requires an **eviction policy** to remove entries when it reaches capacity.

---

# Requirements

## Functional Requirements

- Support:
    - `get(key)`
    - `put(key, value)`
    - `remove(key)`
    - `contains(key)`
- Evict entries when cache is full.
    - Default: **LRU**
    - Extensible to **LFU**, **FIFO**, **MRU**, etc.
- Updating an existing key should overwrite the value.
- Duplicate keys are not allowed.
- Track access/usage information required by the eviction strategy.

---

## Non-Functional Requirements

### Performance

| Operation | Complexity |
|------------|------------|
| `get(key)` | O(1) |
| `put(key, value)` | O(1) |

### Scalability

- Configurable capacity during cache creation.
- Efficient handling of large numbers of entries.

### Concurrency

- Multiple readers and writers should be supported.
- Data consistency must be maintained.

### Extensibility

- New eviction strategies should be pluggable.
- Storage implementation should be replaceable.

### Reliability

- Cache must never exceed configured capacity.
- No duplicate keys.
- Data must remain consistent after concurrent operations.

---

## Clarifying Questions

| Question | Answer |
|-----------|---------|
| In-memory only? | Yes |
| TTL-based eviction? | No |
| Persist after restart? | No |
| Distributed cache? | No |
| Capacity = 0? | Invalid |

---

# Entities and Classes

## CacheEntry<K, V>

Represents a cache record.

```java
class CacheEntry<K, V> {
    K key;
    V value;
}
```

---

## Storage<K, V>

Responsible for storing cache entries.

### Interface

```java
interface Storage<K, V> {
    V get(K key);
    void remove(K key);
    boolean contains(K key);
    void put(K key, V value);
    int size();
}
```

### Implementation

#### ConcurrentHashMapStorage<K, V>

```java
class ConcurrentHashMapStorage<K, V>
        implements Storage<K, V> {

    private final Map<K, CacheEntry<K, V>> map =
            new ConcurrentHashMap<>();
}
```

Uses `ConcurrentHashMap` for thread-safe storage.

---

## EvictionPolicy<K>

Responsible for deciding which key should be removed when the cache becomes full.

### Interface

```java
interface EvictionPolicy<K> {

    void keyAdded(K key);

    void keyAccessed(K key);

    void keyUpdated(K key);

    void keyRemoved(K key);

    K evictKey();
}
```

---

## LRUEvictionPolicy<K>

Maintains access order using a doubly linked list.

> Interview shortcut: use `LinkedHashSet`.
>
> Production-style implementation: custom doubly linked list + hashmap.

### Operations

#### keyAdded(K key)

```text
Add key to front of linked list
```

#### keyAccessed(K key)

```text
Remove key from current position
Move key to front
```

#### keyUpdated(K key)

```text
Remove key from current position
Move key to front
```

#### keyRemoved(K key)

```text
Remove key from linked list
```

#### evictKey()

```text
Remove and return key from end of linked list
(Least Recently Used)
```

---

## Other Eviction Policies

```java
class LFUEvictionPolicy<K>
        implements EvictionPolicy<K> {}

class FIFOEvictionPolicy<K>
        implements EvictionPolicy<K> {}
```

---

# Cache<K, V>

Main orchestrator that coordinates:

- Storage
- Eviction Policy
- Concurrency Control

## Fields

```java
class Cache<K, V> {

    private final int capacity;

    private final Storage<K, V> storage;

    private final EvictionPolicy<K> evictionPolicy;

    private final ReentrantReadWriteLock rwLock;
}
```

---

## get(K key)

### Phase 1: Read Data

```java
rwLock.readLock().lock();

V value = null;

try {
    value = storage.get(key);
} finally {
    rwLock.readLock().unlock();
}
```

### Phase 2: Update Metadata

```java
if (value != null) {

    rwLock.writeLock().lock();

    try {
        evictionPolicy.keyAccessed(key);
    } finally {
        rwLock.writeLock().unlock();
    }

    return value;
}

return null;
```

### Why?

- Actual lookup is read-only.
- Multiple readers can execute concurrently.
- Metadata updates require exclusive access.

---

## remove(K key)

```java
rwLock.writeLock().lock();

try {

    if (storage.contains(key)) {

        storage.remove(key);

        evictionPolicy.keyRemoved(key);
    }

} finally {
    rwLock.writeLock().unlock();
}
```

Entire operation requires a write lock because it modifies both:

- Storage
- Eviction metadata

---

## contains(K key)

```java
rwLock.readLock().lock();

try {
    return storage.contains(key);
} finally {
    rwLock.readLock().unlock();
}
```

Pure read operation.

---

## put(K key, V value)

```java
rwLock.writeLock().lock();

try {

    if (storage.contains(key)) {

        storage.put(key, value);

        evictionPolicy.keyUpdated(key);

    } else {

        if (storage.size() == capacity) {

            K evictedKey =
                    evictionPolicy.evictKey();

            storage.remove(evictedKey);
        }

        storage.put(key, value);

        evictionPolicy.keyAdded(key);
    }

} finally {
    rwLock.writeLock().unlock();
}
```

Entire operation runs under a write lock because:

- Cache size may change.
- Eviction may occur.
- Metadata structures are modified.

---

# Concurrency & Locking Strategy

## Global ReentrantReadWriteLock

The cache owns a single global:

```java
ReentrantReadWriteLock rwLock;
```

This lock acts as the **orchestrator** between:

- Storage
- Eviction Policy

ensuring both remain consistent.

---

## Why Split `get()` Into Two Halves?

### 1. Better Throughput

The actual lookup:

```java
storage.get(key)
```

is read-only.

Using a write lock for the entire method would serialize all reads.

Instead:

- Many threads can read simultaneously.
- Higher throughput under heavy load.

---

### 2. Explicit Write Lock Elevation

Java's `ReentrantReadWriteLock` does **not safely support upgrading**:

```text
Read Lock → Write Lock
```

inside the same critical section.

Attempting this can lead to deadlocks.

Therefore:

```text
Acquire Read Lock
↓
Read Data
↓
Release Read Lock
↓
Acquire Write Lock
↓
Update Eviction Metadata
```

---

# Responsibilities of Each Component

## Cache

- Orchestrates operations.
- Maintains consistency.
- Handles locking.
- Coordinates storage and eviction policy.

---

## Storage

- Stores data.
- Provides O(1) lookup.
- Thread-safe at storage level.

Implementation:

```java
ConcurrentHashMap
```

---

## EvictionPolicy

- Tracks usage metadata.
- Decides eviction candidate.
- Assumes caller already holds the write lock.

Eviction policies no longer own locks.

---

# Design Flow

```text
                +----------------+
                |     Cache      |
                +----------------+
                         |
          +--------------+--------------+
          |                             |
          v                             v

 +------------------+       +--------------------+
 |     Storage      |       |   EvictionPolicy   |
 +------------------+       +--------------------+
 | ConcurrentHashMap|       | LRU / LFU / FIFO   |
 +------------------+       +--------------------+

```

---

# Key Design Patterns Used

### Strategy Pattern

```text
EvictionPolicy
    ├── LRU
    ├── LFU
    └── FIFO
```

Allows plugging in new eviction algorithms without modifying cache logic.

---

### Composition

```text
Cache
 ├── Storage
 └── EvictionPolicy
```

Cache delegates responsibilities instead of implementing everything itself.

---

# Final Complexity

| Operation | Time Complexity |
|------------|------------|
| `get()` | O(1) |
| `put()` | O(1) |
| `remove()` | O(1) |
| `contains()` | O(1) |

### Space Complexity

```text
O(capacity)
```

for both storage and eviction metadata.