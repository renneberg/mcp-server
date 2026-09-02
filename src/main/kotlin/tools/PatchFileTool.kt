package tools

import ContextManager
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

class PatchFileTool(
    private val contextManager: ContextManager
) {
    fun handle(arguments: Map<String, JsonElement>?): CallToolResult {
        if (arguments == null) return CallToolResult(content = listOf(TextContent("Error: Missing arguments")), isError = true)

        val path = arguments["path"]?.jsonPrimitive?.content ?: return CallToolResult(content = listOf(TextContent("Error: Missing path")), isError = true)
        val search = arguments["search"]?.jsonPrimitive?.content ?: return CallToolResult(content = listOf(TextContent("Error: Missing search string")), isError = true)
        val replace = arguments["replace"]?.jsonPrimitive?.content ?: return CallToolResult(content = listOf(TextContent("Error: Missing replace string")), isError = true)

        return try {
            val file = File(path)
            if (!file.exists()) return CallToolResult(content = listOf(TextContent("Error: File not found")), isError = true)
            
            val content = file.readText()
            if (!content.contains(search)) {
                return CallToolResult(content = listOf(TextContent("Error: Search string not found in file")), isError = true)
            }
            
            val newContent = content.replace(search, replace)
            file.writeText(newContent)
            
            contextManager.recordAction("Patched file: $path")
            CallToolResult(content = listOf(TextContent("Successfully patched $path")))
        } catch (e: Exception) {
            CallToolResult(content = listOf(TextContent("Error patching file: ${e.message}")), isError = true)
        }
    }
}
