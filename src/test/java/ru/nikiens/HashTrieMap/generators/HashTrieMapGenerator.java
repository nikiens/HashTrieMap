package ru.nikiens.HashTrieMap.generators;

import com.google.common.collect.testing.Helpers;
import com.google.common.collect.testing.SampleElements;
import com.google.common.collect.testing.TestMapGenerator;
import ru.nikiens.HashTrieMap.HashTrieMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class HashTrieMapGenerator implements TestMapGenerator<String, Integer> {
    private static final List<String> strings = new ArrayList<>();

    static {
        for (int i = 0; i < 5; i++) {
            strings.add(getRandomString());
        }
    }

    public static String getRandomString() {
        Random random = new Random();
        return random.ints(97, 123)
                .limit(50)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    @Override
    public String[] createKeyArray(int length) {
        return new String[length];
    }

    @Override
    public Integer[] createValueArray(int length) {
        return new Integer[length];
    }

    @Override
    public SampleElements<Map.Entry<String, Integer>> samples() {
        return new SampleElements<>(
                Helpers.mapEntry(strings.get(0), 0),
                Helpers.mapEntry(strings.get(1), 1),
                Helpers.mapEntry(strings.get(2), 2),
                Helpers.mapEntry(strings.get(3), 3),
                Helpers.mapEntry(strings.get(4), 4)
        );
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Integer> create(Object... elements) {
        HashTrieMap<String, Integer> dst = new HashTrieMap<>();

        for (Object o : elements) {
            Map.Entry<String, Integer> e = (Map.Entry<String, Integer>) o;
            dst = (HashTrieMap<String, Integer>) dst.insert(e.getKey(), e.getValue());
        }
        return dst;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map.Entry<String, Integer>[] createArray(int length) {
        return new Map.Entry[length];
    }

    @Override
    public Iterable<Map.Entry<String, Integer>> order(List<Map.Entry<String, Integer>> insertionOrder) {
        return insertionOrder;
    }
}
