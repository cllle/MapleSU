package dev.maplesu.manager.liteweb

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.Properties

/** Shared root-side file source; each document is tied to this installation. */
internal class LiteWebModuleFiles(private val modulesRoot: File = File("/data/adb/modules")) {
    data class Module(val id: String, val directory: File, val identity: String, val properties: Map<String, String>)

    fun module(id: String): Module? {
        if (!LiteWebModWeb.validModuleId(id)) return null
        return runCatching {
            val directory = File(modulesRoot, id).canonicalFile
            val prop = File(directory, "module.prop")
            if (!directory.isDirectory || !prop.isFile || prop.length() > 65536L ||
                listOf("disable", "remove", "update").any { File(directory, it).exists() } ||
                !File(directory, "webroot").isDirectory) return null
            val attrs = Files.readAttributes(prop.toPath(), BasicFileAttributes::class.java)
            val props = Properties().apply { prop.reader(Charsets.UTF_8).use(::load) }
            val identity = "${directory.path}:${attrs.fileKey()}:${attrs.lastModifiedTime()}:${attrs.size()}"
            Module(id, directory, identity, props.stringPropertyNames().associateWith { props.getProperty(it) })
        }.getOrNull()
    }

    fun identity(id: String): String? = module(id)?.identity

    fun read(id: String, relativePath: String): LiteWebModuleWeb {
        val normalized = LiteWebModWeb.normalizeRel(relativePath) ?: return LiteWebModuleWeb.NotFound
        val module = module(id) ?: return LiteWebModuleWeb.Unavailable("MODULE_WEB_SESSION_UNAVAILABLE")
        return runCatching {
            val root = File(module.directory, "webroot").canonicalFile
            val target = File(root, normalized).canonicalFile
            if (!target.path.startsWith(root.path + File.separator) || !target.isFile) return LiteWebModuleWeb.NotFound
            if (target.length() > LiteWebModWeb.MAX_FILE_BYTES)
                return LiteWebModuleWeb.Unavailable("MODULE_WEB_FILE_TOO_LARGE")
            val bytes = target.readBytes()
            if (identity(id) != module.identity) return LiteWebModuleWeb.Unavailable("MODULE_WEB_SESSION_CHANGED")
            LiteWebModuleWeb.Found(LiteWebModWeb.contentType(normalized), bytes)
        }.getOrElse { LiteWebModuleWeb.Unavailable("MODULE_WEB_READ_FAILED") }
    }
}

interface LiteWebModuleBridgePort : AutoCloseable {
    fun handle(request: LiteWebHttpRequest, token: String): LiteWebHttpResponse
    override fun close() {}
}

object UnavailableModuleBridgePort : LiteWebModuleBridgePort {
    override fun handle(request: LiteWebHttpRequest, token: String) =
        LiteWebHttpResponse.error(501, "MODULE_BRIDGE_UNAVAILABLE", "module bridge is not connected")
}
