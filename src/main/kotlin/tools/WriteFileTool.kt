package tools

import ContextManager
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

class WriteFileTool(
    private val contextManager: ContextManager
) {
    fun handle(arguments: Map<String, JsonElement>?): CallToolResult {
        if (arguments == null) return CallToolResult(content = listOf(TextContent("Error: Missing arguments")), isError = true)

        val path = arguments["path"]?.jsonPrimitive?.content ?: return CallToolResult(content = listOf(TextContent("Error: Missing path")), isError = true)
        val content = arguments["content"]?.jsonPrimitive?.content ?: return CallToolResult(content = listOf(TextContent("Error: Missing content")), isError = true)

        return try {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeText(content)
            
            contextManager.recordAction("Wrote to file: $path")
            CallToolResult(content = listOf(TextContent("Successfully wrote to $path")))
        } catch (e: Exception) {
            CallToolResult(content = listOf(TextContent("Error writing file: ${e.message}")), isError = true)
        }
    }
}
