package io.github.avohra25.kcrdt

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RgaTest {
    private fun Rga<Char>.receive(ops: List<RgaOp<Char>>) = ops.forEach { apply(it) }

    @Test
    fun `local editing behaves like a list`() {
        val doc = Rga<Char>("a")
        doc.insertText(0, "hello world")
        doc.deleteRange(5, 6)
        doc.insertText(5, ", kotlin")
        doc.insert(0, '>')
        assertEquals(">hello, kotlin", doc.text())
        assertEquals('h', doc[1])
        assertEquals(6, doc.tombstones)
        assertFailsWith<IndexOutOfBoundsException> { doc.insert(99, 'x') }
        assertFailsWith<IndexOutOfBoundsException> { doc.delete(doc.size) }
    }

    @Test
    fun `concurrent runs at the same position never interleave`() {
        val a = Rga<Char>("a")
        val b = Rga<Char>("b")
        val base = a.insertText(0, "[]")
        b.receive(base)
        val fromA = a.insertText(1, "alpha")
        val fromB = b.insertText(1, "beta")
        a.receive(fromB)
        b.receive(fromA)
        assertEquals(a.text(), b.text())
        assertTrue(a.text() == "[alphabeta]" || a.text() == "[betaalpha]", a.text())
    }

    @Test
    fun `insert anchored on a concurrently deleted element still lands`() {
        val a = Rga<Char>("a")
        val b = Rga<Char>("b")
        b.receive(a.insertText(0, "abc"))
        val del = a.delete(1) // a deletes 'b'
        val ins = b.insert(2, 'X') // b inserts after 'b'
        a.apply(ins)
        b.apply(del)
        assertEquals("aXc", a.text())
        assertEquals(a.text(), b.text())
    }

    @Test
    fun `out-of-order and duplicate delivery converge`() {
        val a = Rga<Char>("a")
        val ops = a.insertText(0, "causal") + a.deleteRange(0, 1)
        val b = Rga<Char>("b")
        val shuffled = (ops + ops).shuffled(Random(3))
        for (op in shuffled) b.apply(op)
        assertEquals(0, b.pendingCount)
        assertEquals("ausal", b.text())
        assertFalse(b.apply(ops.first()), "duplicate must be ignored")
    }

    @Test
    fun `history replays onto a fresh replica`() {
        val a = Rga<Char>("a")
        a.insertText(0, "replay me")
        a.deleteRange(7, 2)
        a.insertText(7, "us")
        val fresh = Rga<Char>("z")
        fresh.receive(a.operations())
        assertEquals(a.text(), fresh.text())
        // The fresh replica's clock caught up, so its own edits sort correctly.
        fresh.insertText(fresh.size, "!")
        a.receive(fresh.operations().takeLast(1))
        assertEquals("replay us!", a.text())
    }

    @Test
    fun `single replica matches a mutable list model`() {
        val rnd = Random(11)
        val doc = Rga<Int>("a")
        val model = ArrayList<Int>()
        repeat(3_000) { step ->
            if (model.isEmpty() || rnd.nextInt(3) > 0) {
                val i = rnd.nextInt(model.size + 1)
                doc.insert(i, step); model.add(i, step)
            } else {
                val i = rnd.nextInt(model.size)
                doc.delete(i); model.removeAt(i)
            }
        }
        assertEquals(model, doc.toList())
    }
}
