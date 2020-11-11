package ru.nikiens.HashTrieMap

import spock.lang.Specification

class SimpleTest extends Specification {
    static def dataMap = new HashMap() {{
        put("one", 1)
        put("two", 2)
        put("ten", 10)
        put("three", 3)
        put("ninety-nine", 99)
    }}

    static def testMap = new HashTrieMap().insertAll(dataMap)

    def 'Test get()'() {
        when:
            def one = testMap.get("one")
            def two = testMap.get("two")
            def ten = testMap.get("ten")
            def three = testMap.get("three")
            def ninetynine = testMap.get("ninety-nine")
        then:
            one == dataMap.get("one")
            two == dataMap.get("two")
            ten == dataMap.get("ten")
            three == dataMap.get("three")
            ninetynine == dataMap.get("ninety-nine")
    }

    def 'Test delete()'() {
        when:
            def newMap = testMap.delete("ten").delete("three")
        then:
            newMap.get("ten") == null
            newMap.get("three") == null
    }

    def 'Test insert()'() {
        when:
            def newMap = testMap.insert("sixty", 60).insert("eleven", 11)
        then:
            newMap.get("sixty") == 60
            newMap.get("eleven") == 11
    }
}
