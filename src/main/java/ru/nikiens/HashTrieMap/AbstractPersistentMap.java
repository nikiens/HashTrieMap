package ru.nikiens.HashTrieMap;

import java.util.AbstractMap;
import java.util.Map;

public abstract class AbstractPersistentMap<K,V> extends AbstractMap<K,V>
        implements PersistentMap<K,V> {

    @Deprecated
    @Override
    public final V put(K key, V value) {
        throw new UnsupportedOperationException();
    }

    @Deprecated
    @Override
    public final V remove(Object key) {
        throw new UnsupportedOperationException();
    }

    @Deprecated
    @Override
    public final void putAll(Map<? extends K, ? extends V> m) {
        throw new UnsupportedOperationException();
    }

    @Deprecated
    @Override
    public final void clear() {
        throw new UnsupportedOperationException();
    }
}
