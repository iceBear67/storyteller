package io.ib67.mods.storyteller.dialog.runner

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CommandExecutorTest {

    @Test
    fun `bare flow commands are namespaced`() {
        assertEquals("storyteller:expect a b", normalizeFlowCommand("expect a b"))
        assertEquals("storyteller:update B 1", normalizeFlowCommand("update B 1"))
        assertEquals("storyteller:get B", normalizeFlowCommand("get B"))
        assertEquals("storyteller:goto dialog", normalizeFlowCommand("goto dialog"))
        assertEquals("storyteller:delete B", normalizeFlowCommand("delete B"))
        assertEquals("storyteller:return all", normalizeFlowCommand("return all"))
    }

    @Test
    fun `ordinary commands are unchanged`() {
        assertEquals("say hi", normalizeFlowCommand("say hi"))
        assertEquals("storyteller:expect a", normalizeFlowCommand("storyteller:expect a"))
        assertEquals("minecraft:return 1", normalizeFlowCommand("minecraft:return 1"))
    }

    @Test
    fun `leading whitespace is removed before namespacing`() {
        assertEquals("storyteller:get B", normalizeFlowCommand("  get B"))
    }
}
