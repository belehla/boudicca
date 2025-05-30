package base.boudicca.model.structured

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class KeyTest {

    @Test
    fun testAlreadySortedList() {
        val sorted = listOf(Key.parse("prop1"), Key.parse("prop2"))
        val unsorted = listOf(Key.parse("prop1"), Key.parse("prop2"))

        assertEquals(sorted, unsorted.sorted())
    }

    @Test
    fun testOnlyPropertySorting() {
        val sorted = listOf(Key.parse("prop1"), Key.parse("prop2"))
        val unsorted = listOf(Key.parse("prop2"), Key.parse("prop1"))

        assertEquals(sorted, unsorted.sorted())
    }

    @Test
    fun testSortingWithOneVariant() {
        val sorted = listOf(
            Key.parse("prop1:variant2=value2"),
            Key.parse("prop2:variant1=value1"),
            Key.parse("prop2:variant2=value2")
        )
        val unsorted = listOf(
            Key.parse("prop2:variant2=value2"),
            Key.parse("prop2:variant1=value1"),
            Key.parse("prop1:variant2=value2")
        )

        assertEquals(sorted, unsorted.sorted())
    }

    @Test
    fun testSortingWithDifferentVariants() {
        val sorted = listOf(
            Key.parse("prop1:variant1=value1"),
            Key.parse("prop1:variant1=value1:variant3=value3"),
            Key.parse("prop1:variant1=value2"),
            Key.parse("prop1:variant1=value2:variant3=value3"),
            Key.parse("prop1:variant2=value1"),
            Key.parse("prop1:variant2=value2"),
        )
        val unsorted = listOf(
            Key.parse("prop1:variant2=value2"),
            Key.parse("prop1:variant1=value2:variant3=value3"),
            Key.parse("prop1:variant2=value1"),
            Key.parse("prop1:variant1=value1:variant3=value3"),
            Key.parse("prop1:variant1=value1"),
            Key.parse("prop1:variant1=value2"),
        )

        assertEquals(sorted, unsorted.sorted())
    }

    @Test
    fun testInvalidKeys() {
        assertThrows<IllegalArgumentException> { Key("*") }
        assertThrows<IllegalArgumentException> { Key("asd:asd") }
        assertThrows<IllegalArgumentException> { Key("key", listOf(Variant("*","value"))) }
        assertThrows<IllegalArgumentException> { Key("key", listOf(Variant("asd=asd","value"))) }
        assertThrows<IllegalArgumentException> { Key("key", listOf(Variant("asd:asd","value"))) }
        assertThrows<IllegalArgumentException> { Key("key", listOf(Variant("name","*"))) }
        assertThrows<IllegalArgumentException> { Key("key", listOf(Variant("name",""))) }
        assertThrows<IllegalArgumentException> { Key("key", listOf(Variant("name","asd:asd"))) }
        assertThrows<IllegalArgumentException> { Key("key", listOf(Variant("name","asd=asd"))) }
    }

    @Test
    fun testParseAndToKey() {
        val keyAsString = "description:variant1=value2:variant2=value3"

        val parsedAndToKey = Key.parse(keyAsString).toKeyString()

        assertThat(parsedAndToKey).isEqualTo(keyAsString)
    }


}
