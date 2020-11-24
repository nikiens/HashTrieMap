package ru.nikiens.HashTrieMap;

import com.google.common.collect.testing.MapTestSuiteBuilder;
import com.google.common.collect.testing.features.CollectionSize;
import com.google.common.collect.testing.features.MapFeature;

import junit.framework.TestSuite;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import ru.nikiens.HashTrieMap.generators.HashTrieMapCollisionGenerator;
import ru.nikiens.HashTrieMap.generators.HashTrieMapGenerator;

import java.util.HashMap;
import java.util.Map;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        HashTrieMapTest.GenericTest.class,
        HashTrieMapTest.HashCollisionTest.class,
        HashTrieMapTest.BigDataTest.class
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

    public static class BigDataTest {
        public static final Map<String,Integer> controlMap = new HashMap<>();
        public static PersistentMap<String, Integer> testingMap = new HashTrieMap<>();

        static {
            for (int i = 0; i < 100000; i++) {
                controlMap.put(HashTrieMapGenerator.getRandomString(), i);
            }
        }

        @Test
        public void testBigDataInsertion() {
            testingMap = testingMap.insertAll(controlMap);
            Assert.assertEquals(controlMap, testingMap);
        }
    }
}
