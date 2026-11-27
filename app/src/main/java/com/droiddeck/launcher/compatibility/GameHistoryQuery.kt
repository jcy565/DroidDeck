package com.droiddeck.launcher.compatibility

/** A null version requests all versions; an exact known version never includes unknown rows. */
internal object GameHistoryQuery {
    data class Selection(val where: String, val args: List<String>, val limit: Int)

    fun validateIdentity(gameId: String, gameVersion: String?) {
        require(gameId.isNotBlank() && gameId.length <= 256)
        require(gameVersion == null || (gameVersion.isNotBlank() &&
            gameVersion.length <= 128 && gameVersion.none(Char::isISOControl)))
    }

    fun recent(gameId: String, limit: Int, gameVersion: String?): Selection {
        validateIdentity(gameId, gameVersion)
        return if (gameVersion == null)
            Selection("game_id = ?", listOf(gameId), limit.coerceIn(1, 100))
        else Selection("game_id = ? AND game_version = ?", listOf(gameId, gameVersion), limit.coerceIn(1, 100))
    }
}
