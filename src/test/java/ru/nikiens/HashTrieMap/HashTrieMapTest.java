package ru.nikiens.HashTrieMap;

import com.google.common.collect.testing.MapTestSuiteBuilder;
import com.google.common.collect.testing.features.CollectionSize;
import com.google.common.collect.testing.features.MapFeature;

import junit.framework.TestSuite;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import ru.nikiens.HashTrieMap.generators.HashTrieMapCollisionGenerator;
import ru.nikiens.HashTrieMap.generators.HashTrieMapGenerator;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        HashTrieMapTest.GenericTest.class,
        HashTrieMapTest.HashCollisionTest.class
})

public class HashTrieMapTest {
    public static class GenericTest {
        public static TestSuite suite() {
            return MapTestSuiteBuilder
                    .using(new HashTrieMapGenerator())
                    .named("HashTrieMap tests")
                    .withFeatures(
                            MapFeature.ALLOWS_NULL_KEYS,
                            MapFeature.ALLOWS_NULL_VALUES,
                            CollectionSize.ANY
                    ).createTestSuite();
        }
    }

    public static class HashCollisionTest {
        public static TestSuite suite() {
            return MapTestSuiteBuilder
                    .using(new HashTrieMapCollisionGenerator())
                    .named("HashTrieMap HashCollisionNode tests")
                    .withFeatures(
                            MapFeature.ALLOWS_NULL_KEYS,
                            MapFeature.ALLOWS_NULL_VALUES,
                            CollectionSize.ANY
                    ).createTestSuite();
        }
    }
}
