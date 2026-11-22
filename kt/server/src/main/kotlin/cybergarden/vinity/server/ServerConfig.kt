package cybergarden.vinity.server

import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.exists
import kotlin.io.path.inputStream

class ServerConfig private constructor(private val file: Properties, val source: Path?) {

    fun value(key: String, variable: String? = null): String? =
        System.getProperty("$PREFIX$key")?.takeIf { it.isNotBlank() }
            ?: variable?.let { System.getenv(it) }?.takeIf { it.isNotBlank() }
            ?: file.getProperty(key)?.trim()?.takeIf { it.isNotBlank() }

    companion object {
        private const val PREFIX = "vinity."
        private const val FILE = "vinity.properties"

        fun load(): ServerConfig {
            val explicit = (System.getProperty("${PREFIX}config") ?: System.getenv("VINITY_CONFIG"))?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
            val path = explicit ?: Path.of(FILE).takeIf { it.exists() }
            val properties = Properties()
            path?.takeIf { it.exists() }?.inputStream()?.reader(Charsets.UTF_8)?.use { properties.load(it) }
            return ServerConfig(properties, path?.toAbsolutePath()?.normalize())
        }
    }
}
