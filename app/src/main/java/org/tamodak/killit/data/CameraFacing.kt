package org.tamodak.killit.data

enum class CameraFacing {
    BACK,
    FRONT;

    companion object {
        val DEFAULT = BACK

        fun fromName(name: String?): CameraFacing =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
