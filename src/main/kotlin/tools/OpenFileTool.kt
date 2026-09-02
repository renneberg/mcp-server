package tools

import FileParser
import ChunkManager
import ContextManager
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

class OpenFileTool(
    private val fileParser: FileParser,
    private val chunkManager: ChunkManager,
    private val contextManager: ContextManager
) {
    fun handle(arguments: Map<String, JsonElement>?): CallToolResult {
        if (arguments == null) return CallToolResult(content = listOf(TextContent("Error: Missing arguments")), isError = true)

        val path = arguments["path"]?.jsonPrimitive?.content ?: return CallToolResult(content = listOf(TextContent("Error: Missing path")), isError = true)
        val mode = arguments["mode"]?.jsonPrimitive?.content ?: "line"

        return try {
            // Automatisches Erstellen der AGENTS.md im Projekt-Root (Elternverzeichnis der Datei)
            val file = File(path)
            file.parentFile?.let { contextManager.ensureAgentsMdExists(it.absolutePath) }

            val chunks = fileParser.parseFile(path, mode)
            chunkManager.setChunks(path, chunks)
            
            contextManager.recordAction("Opened file: $path (Mode: $mode)")
            
            val firstChunk = chunks.firstOrNull() ?: ""
            CallToolResult(content = listOf(TextContent(firstChunk)))
        } catch (e: Exception) {
            CallToolResult(content = listOf(TextContent("Error opening file: ${e.message}")), isError = true)
        }
    }
}
