package ru.nikiens.HashTrieMap;

import org.jetbrains.annotations.NotNull;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

public class HashTrieMap<K, V> extends AbstractPersistentMap<K, V>
        implements PersistentMap<K, V> {

    private final Node<K, V> root;
    private final int size;

    private HashTrieMap(Node<K, V> root, int size) {
        this.root = root;
        this.size = size;
    }

    public HashTrieMap() {
        this(new BitmapIndexedNode<>(0, 0, new Object[0]), 0);
    }

    private static final class Observer {
        private boolean isModified;
        private boolean isReplaced;

        private Observer() {
        }

        private boolean isModified() {
            return this.isModified;
        }

        private void setModified() {
            this.isModified = true;
        }

        private boolean isReplaced() {
            return this.isReplaced;
        }

        private void setReplaced() {
            this.isReplaced = true;
            this.isModified = true;
        }
    }

    private abstract static class Node<K, V> {
        private enum Size {
            EMPTY, ONE, MORE
        }

        abstract V find(K key, int hash, int shift);

        abstract Node<K, V> insert(K key, V value, int hash, int shift, Observer observer);

        abstract Node<K, V> delete(K key, int hash, int shift, Observer observer);

        abstract Node<K, V> getNode(int index);

        abstract boolean containsKey(K key, int hash, int shift);

        abstract K getKey(int index);

        abstract V getValue(int index);

        abstract int getNodeArity();

        abstract int getPayloadArity();

        private Size sizePredicate() {
            if (getNodeArity() != 0) {
                return Size.MORE;
            }

            switch (getPayloadArity()) {
                case 0:
                    return Size.EMPTY;
                case 1:
                    return Size.ONE;
                default:
                    return Size.MORE;
            }
        }
    }

    private static final class BitmapIndexedNode<K, V> extends Node<K, V> {
        private static final int PARTITION_BITMASK = 0b11111;
        private static final int PARTITION_OFFSET = 5;
        private static final int HASH_CODE_LENGTH = 32;

        private enum Operation {
            INSERT_ENTRY, DELETE_ENTRY,
            INLINE_ENTRY, DEINLINE_ENTRY,
            INSERT_VALUE, INSERT_NODE
        }

        private final int payloadMap;
        private final int nodeMap;

        private final Object[] contents;

        private BitmapIndexedNode(int nodeMap, int payloadMap, Object[] contents) {
            this.payloadMap = payloadMap;
            this.nodeMap = nodeMap;
            this.contents = contents;
        }

        private static int mask(int hash, int shift) {
            return (hash >>> shift) & PARTITION_BITMASK;
        }

        private static int getBitPosition(int hash, int shift) {
            return 1 << mask(hash, shift);
        }

        private static int getIndex(int bitmap, int bitPos) {
            return Integer.bitCount(bitmap & (bitPos - 1));
        }

        @SuppressWarnings("unchecked")
        @Override
        Node<K, V> getNode(int index) {
            return (Node<K, V>) contents[contents.length - 1 - index];
        }

        @SuppressWarnings("unchecked")
        @Override
        K getKey(int index) {
            return (K) contents[2 * index];
        }

        @SuppressWarnings("unchecked")
        @Override
        V getValue(int index) {
            return (V) contents[2 * index + 1];
        }

        @Override
        int getNodeArity() {
            return Integer.bitCount(nodeMap);
        }

        @Override
        int getPayloadArity() {
            return Integer.bitCount(payloadMap);
        }

        @Override
        boolean containsKey(K key, int hash, int shift) {
            int bitPos = getBitPosition(hash, shift);

            if ((bitPos & payloadMap) != 0) {
                int index = getIndex(payloadMap, bitPos);

                return Objects.equals(getKey(index), key);
            }

            if ((bitPos & nodeMap) != 0) {
                return getNode(getIndex(nodeMap, bitPos)).containsKey(key, hash, shift + PARTITION_OFFSET);
            }

            return false;
        }

        @Override
        V find(K key, int hash, int shift) {
            int bitPos = getBitPosition(hash, shift);

            if ((bitPos & payloadMap) != 0) {
                int index = getIndex(payloadMap, bitPos);

                return (Objects.equals(getKey(index), key)) ? getValue(index) : null;
            }

            if ((bitPos & nodeMap) != 0) {
                return getNode(getIndex(nodeMap, bitPos)).find(key, hash, shift + PARTITION_OFFSET);
            }

            return null;
        }

        @Override
        Node<K, V> insert(K key, V value, int hash, int shift, Observer observer) {
            int bitPos = getBitPosition(hash, shift);
            int payloadIndex = getIndex(payloadMap, bitPos);
            int nodeIndex = contents.length - 1 - getIndex(nodeMap, bitPos);

            if ((bitPos & payloadMap) != 0) {
                if (getKey(payloadIndex) == key) {
                    observer.setReplaced();

                    Object[] modified = copyAndModifyContents(Operation.INSERT_VALUE, bitPos);
                    modified[2 * payloadIndex + 1] = value;

                    return new BitmapIndexedNode<>(nodeMap, payloadMap, modified);

                }

                Node<K, V> subNode = merge(
                        key, value,
                        getKey(payloadIndex), getValue(payloadIndex),
                        hash, Objects.hashCode(getKey(payloadIndex)),
                        shift + PARTITION_OFFSET
                );

                observer.setModified();

                Object[] modified = copyAndModifyContents(Operation.DEINLINE_ENTRY, bitPos);
                modified[nodeIndex - 1] = subNode;

                return new BitmapIndexedNode<>(nodeMap | bitPos, payloadMap ^ bitPos, modified);
            }

            if ((bitPos & nodeMap) != 0) {
                Node<K, V> subNode = getNode(getIndex(nodeMap, bitPos))
                        .insert(key, value, hash, shift + PARTITION_OFFSET, observer);

                if (observer.isModified()) {
                    Object[] modified = copyAndModifyContents(Operation.INSERT_NODE, bitPos);
                    modified[nodeIndex] = subNode;

                    return new BitmapIndexedNode<>(nodeMap, payloadMap, modified);
                }

                return this;
            }

            observer.setModified();

            Object[] modified = copyAndModifyContents(Operation.INSERT_ENTRY, bitPos);
            modified[2 * payloadIndex] = key;
            modified[2 * payloadIndex + 1] = value;

            return new BitmapIndexedNode<>(nodeMap, payloadMap | bitPos, modified);
        }

        @Override
        Node<K, V> delete(K key, int hash, int shift, Observer observer) {
            int bitPos = getBitPosition(hash, shift);
            int payloadIndex = getIndex(payloadMap, bitPos);
            int nodeIndex = contents.length - 1 - getIndex(nodeMap, bitPos);

            if ((bitPos & payloadMap) != 0) {
                if (!getKey(payloadIndex).equals(key)) {
                    return this;
                }

                observer.setModified();

                if (getNodeArity() == 0 && getPayloadArity() == 2) {
                    int payloadMap = (shift == 0) ? this.payloadMap ^ bitPos : getBitPosition(hash, 0);

                    return (payloadIndex == 0)
                            ? new BitmapIndexedNode<>(0, payloadMap, new Object[]{getKey(1), getValue(1)})
                            : new BitmapIndexedNode<>(0, payloadMap, new Object[]{getKey(0), getValue(0)});
                } else {
                    Object[] modified = copyAndModifyContents(Operation.DELETE_ENTRY, bitPos);
                    return new BitmapIndexedNode<>(nodeMap, payloadMap ^ bitPos, modified);
                }
            }

            if ((bitPos & nodeMap) != 0) {
                Node<K, V> subNode = getNode(getIndex(nodeMap, bitPos))
                        .delete(key, hash, shift + PARTITION_OFFSET, observer);

                if (observer.isModified()) {
                    switch (subNode.sizePredicate()) {
                        case EMPTY:
                            throw new IllegalStateException();

                        case ONE: {
                            if (!(getPayloadArity() == 0 && getNodeArity() == 1)) {
                                Object[] modified = copyAndModifyContents(Operation.INLINE_ENTRY, bitPos);
                                modified[2 * payloadIndex] = subNode.getKey(0);
                                modified[2 * payloadIndex + 1] = subNode.getValue(0);

                                return new BitmapIndexedNode<>(nodeMap ^ bitPos, payloadMap | bitPos, modified);
                            }
                            return subNode;
                        }
                        case MORE: {
                            Object[] modified = copyAndModifyContents(Operation.INSERT_NODE, bitPos);
                            modified[nodeIndex] = subNode;

                            return new BitmapIndexedNode<>(nodeMap, payloadMap, modified);
                        }
                    }
                }
            }
            return this;
        }

        private Object[] copyAndModifyContents(Operation operation, int bitPos) {
            int payloadIndex = 2 * getIndex(payloadMap, bitPos);
            int nodeIndex = contents.length - 2 - getIndex(nodeMap, bitPos);

            Object[] modified;

            switch (operation) {
                case INSERT_ENTRY: {
                    modified = new Object[contents.length + 2];
                    System.arraycopy(contents, 0, modified, 0, payloadIndex);
                    System.arraycopy(contents, payloadIndex, modified, payloadIndex + 2, contents.length - payloadIndex);
                    break;
                }
                case DELETE_ENTRY: {
                    modified = new Object[contents.length - 2];
                    System.arraycopy(contents, 0, modified, 0, payloadIndex);
                    System.arraycopy(contents, payloadIndex + 2, modified, payloadIndex, contents.length - payloadIndex - 2);
                    break;
                }
                case INLINE_ENTRY: {
                    modified = new Object[contents.length + 1];
                    System.arraycopy(contents, 0, modified, 0, payloadIndex);
                    System.arraycopy(contents, payloadIndex, modified, payloadIndex + 2, nodeIndex + 1 - payloadIndex);
                    System.arraycopy(contents, nodeIndex + 2, modified, nodeIndex + 3, contents.length - nodeIndex - 2);
                    break;
                }
                case DEINLINE_ENTRY: {
                    modified = new Object[contents.length - 1];
                    System.arraycopy(contents, 0, modified, 0, payloadIndex);
                    System.arraycopy(contents, payloadIndex + 2, modified, payloadIndex, nodeIndex - payloadIndex);
                    System.arraycopy(contents, nodeIndex + 2, modified, nodeIndex + 1, contents.length - nodeIndex - 2);
                    break;
                }
                default: {
                    modified = new Object[contents.length];
                    System.arraycopy(contents, 0, modified, 0, contents.length);
                }
            }
            return modified;
        }

        @SuppressWarnings("unchecked")
        private Node<K, V> merge(K key1, V value1, K key2, V value2, int hash1, int hash2, int shift) {
            if (shift > HASH_CODE_LENGTH) {
                return new HashCollisionNode<>((K[]) new Object[]{key1, key2}, (V[]) new Object[]{value1, value2});
            }

            int mask1 = mask(hash1, shift);
            int mask2 = mask(hash2, shift);

            if (mask1 == mask2) {
                Node<K, V> subNode = merge(key1, value1, key2, value2, hash1, hash2, shift + PARTITION_OFFSET);

                return new BitmapIndexedNode<>(getBitPosition(hash1, shift), 0, new Object[]{subNode});
            }

            int payloadMap = getBitPosition(hash1, shift) | getBitPosition(hash2, shift);

            return (mask1 < mask2)
                    ? new BitmapIndexedNode<>(0, payloadMap, new Object[]{key1, value1, key2, value2})
                    : new BitmapIndexedNode<>(0, payloadMap, new Object[]{key2, value2, key1, value1});
        }
    }

    private static final class HashCollisionNode<K, V> extends Node<K, V> {
        private final K[] keys;
        private final V[] values;

        private HashCollisionNode(K[] keys, V[] values) {
            this.keys = keys;
            this.values = values;
        }

        @Override
        Node<K, V> getNode(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        K getKey(int index) {
            return keys[index];
        }

        @Override
        V getValue(int index) {
            return values[index];
        }

        @Override
        int getNodeArity() {
            return 0;
        }

        @Override
        int getPayloadArity() {
            return keys.length;
        }

        @Override
        boolean containsKey(K key, int hash, int shift) {
            for (int i = 0; i < getPayloadArity(); i++) {
                if (keys[i].equals(key)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        V find(K key, int hash, int shift) {
            for (int i = 0; i < getPayloadArity(); i++) {
                if (keys[i].equals(key)) {
                    return values[i];
                }
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        @Override
        Node<K, V> insert(K key, V value, int hash, int shift, Observer observer) {
            for (int i = 0; i < getPayloadArity(); i++) {
                if (keys[i].equals(key)) {
                    if (values[i].equals(value)) {
                        return this;
                    } else {
                        V[] newValues = (V[]) new Object[getPayloadArity()];
                        System.arraycopy(this.values, 0, newValues, 0, getPayloadArity());
                        newValues[i] = value;

                        observer.setReplaced();
                        return new HashCollisionNode<>(keys, newValues);
                    }
                }
            }

            K[] keys = (K[]) new Object[getPayloadArity() + 1];
            V[] values = (V[]) new Object[getPayloadArity() + 1];

            System.arraycopy(this.keys, 0, keys, 0, getPayloadArity());
            keys[getPayloadArity()] = key;

            System.arraycopy(this.values, 0, values, 0, getPayloadArity());
            values[getPayloadArity()] = value;

            observer.setModified();
            return new HashCollisionNode<>(keys, values);
        }

        @SuppressWarnings("unchecked")
        @Override
        Node<K, V> delete(K key, int hash, int shift, Observer observer) {
            for (int i = 0; i < getPayloadArity(); i++) {
                if (keys[i].equals(key)) {
                    observer.setModified();

                    if (getPayloadArity() == 1) {
                        return new BitmapIndexedNode<>(0, 0, new Object[0]);
                    }

                    if (getPayloadArity() == 2) {
                        K key1 = (i == 0) ? keys[1] : keys[0];
                        V value1 = (i == 0) ? values[1] : values[0];

                        return (Node<K, V>) new BitmapIndexedNode<>(0, 0, new Object[0])
                                .insert(key1, value1, key1.hashCode(), 0, observer);
                    }

                    K[] keys = (K[]) new Object[getPayloadArity() - 1];
                    System.arraycopy(this.keys, 0, keys, 0, i);
                    System.arraycopy(this.keys, i + 1, keys, i, keys.length - i);

                    V[] values = (V[]) new Object[getPayloadArity() - 1];
                    System.arraycopy(this.values, 0, values, 0, i);
                    System.arraycopy(this.values, i + 1, values, i, keys.length - i);

                    return new HashCollisionNode<>(keys, values);
                }
            }
            return this;
        }
    }

    /* ------------- PersistentMap API ---------- */

    @Override
    public PersistentMap<K, V> insert(K k, V v) {
        Observer observer = new Observer();

        Node<K, V> root = this.root.insert(k, v, Objects.hashCode(k), 0, observer);

        return (observer.isModified())
                ? new HashTrieMap<>(root, (!observer.isReplaced()) ? size + 1 : size)
                : this;
    }

    @Override
    public PersistentMap<K, V> delete(Object o) {
        Observer observer = new Observer();

        @SuppressWarnings("unchecked")
        Node<K, V> root = this.root.delete((K) o, Objects.hashCode(o), 0, observer);

        return (!observer.isModified()) ? this : new HashTrieMap<>(root, size - 1);
    }

    @Override
    public PersistentMap<K, V> insertAll(Map<? extends K, ? extends V> map) {
        PersistentMap<K, V> dst = new HashTrieMap<>();

        for (Entry<? extends K, ? extends V> entry : map.entrySet()) {
            dst = dst.insert(entry.getKey(), entry.getValue());
        }

        return dst;
    }

    /* ---------------- Iterators ---------------- */

    private class EntryIterator implements Iterator<Map.Entry<K, V>> {
        private final Deque<Node<K, V>> nodes = new ArrayDeque<>();
        private Node<K, V> currentPayloadNode = root;

        private int currentEntryIndex;
        private int currentNodeIndex;

        private EntryIterator() {
            nodes.push(root);
        }

        private void advanceToNextPayloadNode() {
            while (!nodes.isEmpty()) {
                while (currentNodeIndex < nodes.getLast().getNodeArity()) {
                    Node<K, V> next = nodes.getLast().getNode(currentNodeIndex++);

                    if (next.getNodeArity() != 0) {
                        nodes.push(next);
                    }

                    if (next.getPayloadArity() != 0) {
                        currentPayloadNode = next;
                        currentEntryIndex = 0;

                        return;
                    }
                }
                nodes.removeLast();
                currentNodeIndex = 0;
            }
        }

        @Override
        public boolean hasNext() {
            return currentEntryIndex < currentPayloadNode.getPayloadArity()
                    || currentPayloadNode.getNodeArity() != 0;
        }

        @Override
        public Entry<K, V> next() {
            if (!hasNext()) throw new NoSuchElementException();

            if (currentEntryIndex >= currentPayloadNode.getPayloadArity()) {
                advanceToNextPayloadNode();
            }

            return new AbstractMap.SimpleImmutableEntry<>(
                    currentPayloadNode.getKey(currentEntryIndex),
                    currentPayloadNode.getValue(currentEntryIndex++)
            );
        }
    }

    private class KeyIterator implements Iterator<K> {
        private final Iterator<Map.Entry<K,V>> i = entrySet().iterator();

        @Override
        public boolean hasNext() {
            return i.hasNext();
        }

        @Override
        public K next() {
            return i.next().getKey();
        }
    }

    /* ----------------- Map API ---------------- */

    private EntrySet entrySet;
    private KeySet keySet;

    @Override
    @NotNull
    public Set<Entry<K, V>> entrySet() {
        EntrySet es = entrySet;
        return (es != null) ? es : (entrySet = new EntrySet());
    }

    private class EntrySet extends AbstractSet<Map.Entry<K, V>> {
        @Override
        @NotNull
        public Iterator<Entry<K, V>> iterator() {
            return new EntryIterator();
        }

        @Override
        public int size() {
            return HashTrieMap.this.size();
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry)) return false;

            Map.Entry<?,?> entry = (Entry<?, ?>) o;
            Object value = HashTrieMap.this.get(entry.getKey());

            return HashTrieMap.this.containsKey(entry.getKey())
                    && Objects.equals(entry.getValue(), value);
        }
    }

    @Override
    @NotNull
    public Set<K> keySet() {
        KeySet ks = keySet;
        return (ks != null) ? ks : (keySet = new KeySet());
    }

    private class KeySet extends AbstractSet<K> {
        @Override
        @NotNull
        public Iterator<K> iterator() {
            return new KeyIterator();
        }

        @Override
        public int size() {
            return HashTrieMap.this.size();
        }

        @Override
        public boolean contains(Object o) {
            return HashTrieMap.this.containsKey(o);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean containsKey(Object key) {
        return root.containsKey((K) key, Objects.hashCode(key), 0);
    }

    @SuppressWarnings("unchecked")
    @Override
    public V get(Object key) {
        return root.find((K) key, Objects.hashCode(key), 0);
    }

    @Override
    public int size() {
        return size;
    }
}