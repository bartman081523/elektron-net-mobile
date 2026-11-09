package org.elektronnet.mobile.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletRegistryTest {
    @Test
    fun askingForTheActiveWalletNeverCreatesOne() {
        val registry = WalletRegistry(MemoryWalletRegistryStore())
        assertEquals(WalletBootstrap.DEFAULT_WALLET, registry.active().name)
        assertTrue(registry.wallets().isEmpty())
        assertEquals(WalletBootstrap.DEFAULT_WALLET, registry.nextName())
    }

    @Test
    fun namesAlreadyOnDiskAreSkipped() {
        val def = WalletBootstrap.DEFAULT_WALLET
        val registry = WalletRegistry(MemoryWalletRegistryStore())
        registry.add(def, "")
        assertEquals("${def}_3", registry.nextName(setOf("${def}_2")))
        assertEquals("${def}_2", registry.nextName())
    }

    @Test
    fun theActiveWalletIsTheChosenOneOrTheFirst() {
        val def = WalletBootstrap.DEFAULT_WALLET
        val registry = WalletRegistry(MemoryWalletRegistryStore())
        registry.add(def, "")
        registry.add("${def}_2", "second")
        assertEquals(def, registry.active().name)
        registry.setActive("${def}_2")
        assertEquals("${def}_2", registry.active().name)
        registry.remove("${def}_2")
        assertEquals(def, registry.active().name)
    }
}
