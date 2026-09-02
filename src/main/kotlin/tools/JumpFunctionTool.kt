package tools

import ChunkManager
import ContextManager
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

class JumpFunctionTool(
    private val chunkManager: ChunkManager,
    private val contextManager: ContextManager
) {
    fun handle(arguments: Map<String, JsonElement>?): CallToolResult {
        val name = arguments?.get("name")?.jsonPrimitive?.content ?: return CallToolResult(content = listOf(TextContent("Error: Missing function name")), isError = true)
        
        val chunk = chunkManager.jumpTo { it.contains("fun $name") || it.contains("fun  $name") }
        return if (chunk != null) {
            contextManager.recordAction("Jumped to function: $name")
            CallToolResult(content = listOf(TextContent(chunk)))
        } else {
            CallToolResult(content = listOf(TextContent("Error: Function '$name' not found")), isError = true)
        }
    }
}
