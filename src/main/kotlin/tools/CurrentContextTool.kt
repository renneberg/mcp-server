package tools

import ChunkManager
import ContextManager
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent

class CurrentContextTool(
    private val chunkManager: ChunkManager,
    private val contextManager: ContextManager
) {
    fun handle(): CallToolResult {
        val status = chunkManager.getStatus()
        val history = contextManager.getSummary()
        return CallToolResult(content = listOf(TextContent("$status\n\n$history")))
    }
}
