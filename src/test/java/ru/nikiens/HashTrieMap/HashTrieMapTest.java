package ru.nikiens.HashTrieMap;

import com.google.common.collect.testing.MapTestSuiteBuilder;
import com.google.common.collect.testing.features.CollectionSize;
import com.google.common.collect.testing.features.MapFeature;

import junit.framework.TestSuite;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        HashTrieMapTest.GuavaTests.class
})

public class HashTrieMapTest {
    public static class GuavaTests {
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
}
