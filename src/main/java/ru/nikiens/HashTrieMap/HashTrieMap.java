package ru.nikiens.HashTrieMap;

import java.util.*;

public class HashTrieMap<K, V> extends AbstractPersistentMap<K, V>
        implements PersistentMap<K, V> {

    private final Node<K,V> root;

    private int size;
    private int hash;

    private HashTrieMap(Node<K, V> root, int size, int hash) {
        this.root = root;
        this.size = size;
        this.hash = hash;
    }

    public HashTrieMap() {
        this.root = new BitmapIndexedNode<>(0, 0, new Object[0]);
    }

    private static final class Observer<V> {
        private boolean isModified;
        private V replaced;

        public Observer() {
        }

        public boolean isModified() {
            return isModified;
        }

        public V getReplaced() {
            return replaced;
        }

        public void setModified() {
            isModified = true;
        }

        public void setReplaced(V replaced) {
            this.replaced = replaced;
            this.isModified = true;
        }
    }

    private abstract static class Node<K, V> {
        enum Size {
            EMPTY, ONE, MORE
        }

        abstract V find(K key, int hash, int shift);

        abstract Node<K, V> insert(K key, V value, int hash, int shift, Observer<V> observer);

        abstract Node<K, V> delete(K key, int hash, int shift, Observer<V> observer);

        abstract K getKey(int index);

        abstract V getValue(int index);

        abstract Size sizePredicate();
    }

    private static final class BitmapIndexedNode<K, V> extends Node<K, V> {
        private static final int PARTITION_BITMASK = 0b11111;
        private static final int PARTITION_OFFSET = 5;
        private static final int HASH_CODE_LENGTH = 32;

        private final int dataMap;
        private final int nodeMap;

        private final Object[] contents;

        private BitmapIndexedNode(int nodeMap,int dataMap, Object[] contents) {
            this.dataMap = dataMap;
            this.nodeMap = nodeMap;
            this.contents = contents;
        }

        static int mask(int hash, int shift) {
            return (hash >>> shift) & PARTITION_BITMASK;
        }

        static int bitPosition(int hash, int shift) {
            return 1 << mask(hash, shift);
        }

        static int index(int bitmap, int bitPos) {
            return Integer.bitCount(bitmap & (bitPos - 1));
        }

        int getNodeArity() {
            return Integer.bitCount(nodeMap);
        }

        int getPayloadArity() {
            return 2 * Integer.bitCount(dataMap);
        }

        @SuppressWarnings("unchecked")
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
        V find(K key, int hash, int shift) {
            int bitPos = bitPosition(hash, shift);

            if ((bitPos & dataMap) != 0) {
                int index = index(dataMap, bitPos);

                return (getKey(index) == key) ? getValue(index) : null;
            }

            if ((bitPos & nodeMap) != 0) {
                return getNode(index(nodeMap, bitPos)).find(key, hash, shift + PARTITION_OFFSET);
            }

            return null;
        }

        @Override
        Size sizePredicate() {
            if (getNodeArity() != 0) {
                return Size.MORE;
            }

            switch (getPayloadArity()) {
                case 0: return Size.EMPTY;
                case 1: return Size.ONE;
                default: return Size.MORE;
            }
        }

        @Override
        Node<K, V> insert(K key, V value, int hash, int shift, Observer<V> observer) {
            int bitPos = bitPosition(hash, shift);

            if ((bitPos & dataMap) != 0) {
                int index = index(dataMap, bitPos);

                if (getKey(index) == key) {
                    observer.setReplaced(getValue(index));

                    return insertValue(getValue(index), bitPos);
                }

                int hash1 = getKey(index).hashCode();
                BitmapIndexedNode<K,V> subNode = (BitmapIndexedNode<K, V>)
                        merge(key, value, getKey(index), getValue(index), hash, hash1, shift + PARTITION_OFFSET);

                observer.setModified();
                return uninlineNode(subNode, bitPos);
            }

            if ((bitPos & nodeMap) != 0) {
                BitmapIndexedNode<K,V> subNode = (BitmapIndexedNode<K, V>) getNode(index(nodeMap, bitPos)).insert(key, value, hash, shift + PARTITION_OFFSET, observer);

                return (observer.isModified()) ? insertNode(subNode, bitPos) : this;
            }

            observer.setModified();
            return insertEntry(key, value, bitPos);
        }

        @Override
        Node<K, V> delete(K key, int hash, int shift, Observer<V> observer) {
            int bitPos = bitPosition(hash, shift);
            int dataIndex = index(dataMap, bitPos);

            if ((bitPos & dataMap) != 0) {
                if (!getKey(dataIndex).equals(key)) {
                    return this;
                }

                observer.setReplaced(getValue(dataIndex));

                if (getNodeArity() == 0 && getPayloadArity() == 2) {
                    int dataMap = (shift == 0) ? this.dataMap ^ bitPos : bitPosition(hash, 0);

                    return (dataIndex == 0)
                            ? new BitmapIndexedNode<>(0, dataMap, new Object[]{getKey(1), getValue(1)})
                            : new BitmapIndexedNode<>(0, dataMap, new Object[]{getKey(0), getValue(0)});
                } else {
                    return deleteEntry(bitPos);
                }
            }

            if ((bitPos & nodeMap) != 0) {
                BitmapIndexedNode<K, V> subNode = (BitmapIndexedNode<K, V>) getNode(index(nodeMap, bitPos))
                        .delete(key, hash, shift + PARTITION_OFFSET, observer);

                if (observer.isModified()) {
                    switch (sizePredicate()) {
                        case EMPTY: throw new IllegalStateException();

                        case ONE: {
                            return (getPayloadArity() == 0 && getNodeArity() == 1)
                                    ? subNode : inlineNode(subNode, bitPos);
                        }
                        case MORE: {
                            return insertNode(subNode, bitPos);
                        }
                    }
                }
            }

            return this;
        }

        private BitmapIndexedNode<K,V> insertEntry(K key, V value, int bitPos) {
            int index = 2 * index(dataMap, bitPos);
            Object[] contents = new Object[this.contents.length + 2];

            System.arraycopy(this.contents, 0, contents, 0, index);
            System.arraycopy(this.contents, index, contents, index + 2, this.contents.length - index);

            contents[index] = key;
            contents[index + 1] = value;

            return new BitmapIndexedNode<>(nodeMap, dataMap | bitPos, contents);
        }

        private BitmapIndexedNode<K, V> deleteEntry(int bitPos) {
            int index = 2 * index(dataMap, bitPos);
            Object[] contents = new Object[this.contents.length - 2];

            System.arraycopy(this.contents, 0, contents, 0, index);
            System.arraycopy(this.contents, index + 2, contents, index, this.contents.length - index - 2);

            return new BitmapIndexedNode<>(nodeMap, dataMap ^ bitPos, contents);
        }


        private BitmapIndexedNode<K, V> inlineNode(BitmapIndexedNode<K, V> subNode, int bitPos) {
            Object[] contents = new Object[this.contents.length + 1];

            int dataIndex = 2 * index(dataMap, bitPos);
            int nodeIndex = contents.length - 1 - index(nodeMap, bitPos);

            System.arraycopy(this.contents, 0, contents, 0, dataIndex);
            System.arraycopy(this.contents, dataIndex, contents, dataIndex + 2, nodeIndex - dataIndex);
            System.arraycopy(this.contents, nodeIndex + 1, contents, nodeIndex + 2, this.contents.length - nodeIndex - 1);

            contents[dataIndex] = subNode.getKey(0);
            contents[dataIndex + 1] = subNode.getValue(0);

            return new BitmapIndexedNode<>(nodeMap ^ bitPos, dataMap | bitPos, contents);
        }

        private BitmapIndexedNode<K,V> uninlineNode(BitmapIndexedNode<K,V> subNode, int bitPos) {
            Object[] contents = new Object[this.contents.length - 1];

            int dataIndex = 2 * index(dataMap, bitPos);
            int nodeIndex = contents.length - 1 - index(nodeMap, bitPos);

            System.arraycopy(this.contents, 0, contents, 0, dataIndex);
            System.arraycopy(this.contents, dataIndex + 2, contents, dataIndex, nodeIndex - dataIndex);
            System.arraycopy(this.contents, nodeIndex + 2, contents, nodeIndex + 1, this.contents.length - nodeIndex - 2);

            contents[nodeIndex] = subNode;

            return new BitmapIndexedNode<>(nodeMap | bitPos, dataMap ^ bitPos, contents);
        }

        private BitmapIndexedNode<K,V> insertValue(V value, int bitPos) {
            int index = 2 * index(nodeMap, bitPos) + 1;

            Object[] contents = this.contents.clone();
            contents[index] = value;

            return new BitmapIndexedNode<>(nodeMap, dataMap, contents);
        }

        private BitmapIndexedNode<K, V> insertNode(BitmapIndexedNode<K, V> node, int bitPos) {
            int index = contents.length - 1 - index(nodeMap, bitPos);

            Object[] contents = this.contents.clone();
            contents[index] = node;

            return new BitmapIndexedNode<>(nodeMap, dataMap, contents);
        }

        @SuppressWarnings("unchecked")
        private Node<K,V> merge(K key1, V value1, K key2, V value2, int hash1, int hash2, int shift) {
            if (shift > HASH_CODE_LENGTH) {
                return new HashCollisionNode<>((K[]) new Object[]{key1, key2}, (V[]) new Object[]{value1, value2}, hash1);
            }

            int mask1 = mask(hash1, shift);
            int mask2 = mask(hash2, shift);

            if (mask1 == mask2) {
                Node<K,V> subNode = merge(key1, value1, key2, value2, hash1, hash2, shift + PARTITION_OFFSET);

                return new BitmapIndexedNode<>(bitPosition(hash1, shift), 0, new Object[]{subNode});
            }

            int dataMap = bitPosition(mask1,shift) | bitPosition(mask2, shift);

            return (mask1 < mask2)
                    ? new BitmapIndexedNode<>(0, dataMap, new Object[]{key1, value1, key2, value2})
                    : new BitmapIndexedNode<>(0, dataMap, new Object[]{key2, value2, key1, value1});
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BitmapIndexedNode<?, ?> that = (BitmapIndexedNode<?, ?>) o;
            return dataMap == that.dataMap &&
                    nodeMap == that.nodeMap &&
                    Arrays.equals(contents, that.contents);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(dataMap, nodeMap);
            result = 31 * result + Arrays.hashCode(contents);
            return result;
        }
    }

    private static final class HashCollisionNode<K,V> extends Node<K,V> {
        private final K[] keys;
        private final V[] values;

        private final int hash;

        private HashCollisionNode(K[] keys, V[] values, int hash) {
            this.keys = keys;
            this.values = values;
            this.hash = hash;
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
        Size sizePredicate() {
            return Size.MORE;
        }

        @Override
        V find(K key, int hash, int shift) {
            for (int i = 0; i < keys.length; i++) {
                if (keys[i].equals(key)) {
                    return values[i];
                }
            }

            return null;
        }

        @SuppressWarnings("unchecked")
        @Override
        Node<K, V> insert(K key, V value, int hash, int shift, Observer<V> observer) {
            for (int i = 0; i < keys.length; i++) {
                if (keys[i].equals(key)) {
                    if (values[i].equals(value)) {
                        return this;
                    } else {
                        V[] newValues = values.clone();
                        newValues[i] = value;

                        observer.setReplaced(value);
                        return new HashCollisionNode<>(keys, newValues, hash);
                    }
                }
            }

            K[] keys = (K[]) new Object[this.keys.length + 1];
            V[] values = (V[]) new Object[this.values.length + 1];

            System.arraycopy(this.keys, 0, keys, 0, keys.length);
            keys[this.keys.length] = key;

            System.arraycopy(this.values, 0, values, 0, values.length);
            values[this.values.length] = value;

            observer.setModified();
            return new HashCollisionNode<>(keys, values, hash);
        }

        @SuppressWarnings("unchecked")
        @Override
        Node<K, V> delete(K key, int hash, int shift, Observer<V> observer) {
            for (int i = 0; i < keys.length; i++) {
                if (keys[i].equals(key)) {
                    observer.setReplaced(values[i]);

                    if (keys.length == 1) {
                        return new BitmapIndexedNode<>(0, 0, new Object[0]);
                    }

                    if (keys.length == 2) {
                        K key1 = (i == 0) ? keys[1] : keys[0];
                        V value1 = (i == 0) ? values[1] : values[0];

                        return (Node<K, V>) new BitmapIndexedNode<>(0, 0, new Object[0])
                                .insert(key1, value1, key1.hashCode(), 0, (Observer<Object>) observer);
                    }

                    K[] keys = (K[]) new Object[this.keys.length - 1];
                    V[] values = (V[]) new Object[this.values.length - 1];

                    System.arraycopy(this.keys, 0,     keys, 0, i);
                    System.arraycopy(this.keys, i + 1, keys, i, keys.length - i - 1);

                    System.arraycopy(this.values, 0,     values, 0, i);
                    System.arraycopy(this.values, i + 1, values, i, keys.length - i - 1);

                    return new HashCollisionNode<>(keys, values, hash);
                }
            }

            return this;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            HashCollisionNode<?, ?> that = (HashCollisionNode<?, ?>) o;
            return hash == that.hash && Arrays.equals(keys, that.keys);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(hash);
            result = 31 * result + Arrays.hashCode(keys);
            result = 31 * result + Arrays.hashCode(values);
            return result;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public V get(Object key) {
        return root.find((K) key, key.hashCode(), 0);
    }

    @Override
    public PersistentMap<K, V> insert(K k, V v) {
        Observer<V> observer = new Observer<>();

        int keyHash = k.hashCode();

        Node<K,V> root = this.root.insert(k, v, keyHash, 0, observer);

        if (!observer.isModified()) {
            return this;
        }

        V replaced = observer.getReplaced();
        int valueHash = v.hashCode();

        if (replaced == null) {
            int hash = this.hash + (keyHash ^ valueHash);

            return new HashTrieMap<>(root, size, hash);
        }

        int hash = this.hash + (keyHash ^ valueHash) - (keyHash ^ replaced.hashCode());

        return new HashTrieMap<>(root, size + 1, hash);
    }

    @Override
    public PersistentMap<K, V> delete(Object o) {
        Observer<V> observer = new Observer<>();

        int keyHash = o.hashCode();

        @SuppressWarnings("unchecked")
        Node<K,V> root = this.root.delete((K) o, keyHash, 0, observer);

        V replaced = observer.getReplaced();

        if (replaced == null) {
            return this;
        }

        return new HashTrieMap<>(root, size - 1, hash - (keyHash ^ replaced.hashCode()));
    }

    @Override
    public PersistentMap<K, V> insertAll(Map<? extends K, ? extends V> map) {
        PersistentMap<K,V> map1 = new HashTrieMap<>();

        for(Entry<? extends K, ? extends V> entry : map.entrySet()) {
            map1 = map1.insert(entry.getKey(), entry.getValue());
        }

        return map1;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        HashTrieMap<?, ?> that = (HashTrieMap<?, ?>) o;
        return size == that.size &&
                hash == that.hash &&
                Objects.equals(root, that.root);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public int size() {
        return size;
    }
}
