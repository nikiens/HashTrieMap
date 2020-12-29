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
import java.util.stream.Collectors;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        HashTrieMapTest.GenericTest.class,
        HashTrieMapTest.HashCollisionGenericTest.class,
        HashTrieMapTest.RegularNodeInsertDeleteTest.class,
        HashTrieMapTest.HashCollisionInsertDeleteTest.class
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

    public static class HashCollisionGenericTest {
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

    public static class RegularNodeInsertDeleteTest {
        public static final Map<String, Integer> controlMap = new HashMap<>();
        public static PersistentMap<String, Integer> testingMap = new HashTrieMap<>();

        static {
            for (int i = 0; i < 100000; i++) {
                controlMap.put(HashTrieMapGenerator.getRandomString(), i);
            }
            testingMap = testingMap.insertAll(controlMap);
        }

        @Test
        public void testRegularNodeInsertion() {
            Assert.assertEquals(controlMap, testingMap);
        }

        @Test
        public void testRegularNodeRemove() {
            String[] keys = controlMap.keySet().toArray(String[]::new);

            for (int i = 25000; i < 45000; i++) {
                String key = keys[i];

                controlMap.remove(key);
                testingMap = testingMap.delete(key);
            }
            Assert.assertEquals(controlMap, testingMap);
        }

        @Test
        public void testRegularNodeReplacement() {
            String[] keys = controlMap.keySet().toArray(String[]::new);

            for (int i = 0; i < 10; i++) {
                String key = keys[i];

                controlMap.put(key, i + 1);
                testingMap = testingMap.insert(key, i + 1);
            }
            Assert.assertEquals(controlMap, testingMap);
        }
    }

    public static class HashCollisionInsertDeleteTest {
        public static final Map<String, Integer> controlMap =
                new HashTrieMapCollisionGenerator().samples().asList().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        public PersistentMap<Object, Object> testingMap =
                new HashTrieMap<>().insertAll(controlMap);

        @Test
        public void testHashCollisionNodeInsertion() {
            Assert.assertEquals(controlMap, testingMap);
        }

        @Test
        public void testHashCollisionNodeRemove() {
            String[] keys = controlMap.keySet().toArray(String[]::new);

            for (int i = 2; i < 5; i++) {
                String key = keys[i];

                controlMap.remove(key);
                testingMap = testingMap.delete(key);
            }
            Assert.assertEquals(controlMap, testingMap);
        }

        @Test
        public void testHashCollisionNodeReplacement() {
            String[] keys = controlMap.keySet().toArray(String[]::new);

            for (int i = 0; i < 2; i++) {
                String key = keys[i];

                controlMap.put(key, i + 1);
                testingMap = testingMap.insert(key, i + 1);
            }
            Assert.assertEquals(controlMap, testingMap);
        }
    }
}
