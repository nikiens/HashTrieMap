package ru.nikiens.HashTrieMap.generators;

import com.google.common.collect.testing.Helpers;
import com.google.common.collect.testing.SampleElements;

import java.util.Map;

public class HashTrieMapCollisionGenerator extends HashTrieMapGenerator {
    @Override
    public SampleElements<Map.Entry<String, Integer>> samples() {
        return new SampleElements<>(
                Helpers.mapEntry("AaAaAa", 0),
                Helpers.mapEntry("AaAaBB", 1),
                Helpers.mapEntry("AaBBAa", 9),
                Helpers.mapEntry("BBBB", 7),
                Helpers.mapEntry("AaBB", 5)
        );
    }
}
