package ru.nikiens.HashTrieMap;

import java.util.Map;

public interface PersistentMap<K, V> extends Map<K,V> {

    PersistentMap<K, V> insert(K k, V v);

    PersistentMap<K, V> delete(Object o);

    PersistentMap<K, V> insertAll(Map<? extends K, ? extends V> map);
}
