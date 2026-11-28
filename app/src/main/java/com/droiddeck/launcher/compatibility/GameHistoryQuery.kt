package com.droiddeck.launcher.compatibility

/** A null version requests all versions; an exact known version never includes unknown rows. */
internal object GameHistoryQuery {
    data class Selection(val where: String, val args: List<String>, val limit: Int)

    private fun validText(value: String, max: Int): Boolean =
        value.isNotBlank() && value.length <= max && value.none(Char::isISOControl) &&
            Charsets.UTF_8.newEncoder().canEncode(value)

    fun validVersion(version: String?): Boolean = version == null || validText(version, 128)

    fun observationVersion(recentlyObserved: String?, confirmed: Boolean): String? =
        recentlyObserved?.takeIf { confirmed && validVersion(it) }

    fun validateIdentity(gameId: String, gameVersion: String?) {
        require(validText(gameId, 256))
        require(validVersion(gameVersion))
    }

    /** Check the returned row as well as binding the requested SQL parameters. */
    fun requireScopedRow(gameId: String, gameVersion: String?, observedId: String, observedVersion: String?) {
        validateIdentity(gameId, gameVersion)
        validateIdentity(observedId, observedVersion)
        require(observedId == gameId && (gameVersion == null || gameVersion == observedVersion)) {
            "실행 기록의 게임 또는 버전 범위가 일치하지 않습니다."
        }
    }

    fun recent(gameId: String, limit: Int, gameVersion: String?): Selection {
        validateIdentity(gameId, gameVersion)
        return if (gameVersion == null)
            Selection("game_id = ?", listOf(gameId), limit.coerceIn(1, 100))
        else Selection("game_id = ? AND game_version = ?", listOf(gameId, gameVersion), limit.coerceIn(1, 100))
    }
}
