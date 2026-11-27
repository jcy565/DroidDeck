package com.droiddeck.launcher.compatibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstalledGameVersionTest {
    private fun manifest(fields: String) = "\"AppState\"\n{\n$fields\n}"

    @Test fun readsInstalledBuildForMatchingApp() {
        assertEquals("12345", InstalledGameVersion.steamBuildId(
            manifest("\"appid\" \"620\"\n\"buildid\" \"12345\""), 620))
    }

    @Test fun refusesWrongAppAndMissingBuild() {
        assertNull(InstalledGameVersion.steamBuildId(
            manifest("\"appid\" \"621\"\n\"buildid\" \"12345\""), 620))
        assertNull(InstalledGameVersion.steamBuildId(manifest("\"appid\" \"620\""), 620))
    }

    @Test fun nestedDepotMetadataIsNotTheInstalledBuild() {
        val nested = "\"InstalledDepots\"\n{\n\"buildid\" \"999\"\n}"
        assertNull(InstalledGameVersion.steamBuildId(manifest("\"appid\" \"620\"\n$nested"), 620))
        assertEquals("12345", InstalledGameVersion.steamBuildId(
            manifest("\"appid\" \"620\"\n\"buildid\" \"12345\"\n$nested"), 620))
    }

    @Test fun ambiguousTruncatedAndInvalidBuildsStayUnknown() {
        assertNull(InstalledGameVersion.steamBuildId(
            manifest("\"appid\" \"620\"\n\"buildid\" \"1\"\n\"buildid\" \"2\""), 620))
        assertNull(InstalledGameVersion.steamBuildId("\"AppState\"\n{\n\"appid\" \"620\"\n\"buildid\" \"1\"", 620))
        for (bad in listOf("0", "", "-1", "abc", "9".repeat(21))) {
            assertNull(InstalledGameVersion.steamBuildId(manifest("\"appid\" \"620\"\n\"buildid\" \"$bad\""), 620))
        }
    }

    @Test fun normalizesOnlyBoundedStoreVersions() {
        assertEquals("1.2.3", InstalledGameVersion.storeVersion(" 1.2.3 "))
        for (bad in listOf(null, "", " ", "1\n2", "x".repeat(129))) {
            assertNull(InstalledGameVersion.storeVersion(bad))
        }
    }
}
