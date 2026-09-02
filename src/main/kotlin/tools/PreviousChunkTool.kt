package tools

import ChunkManager
import ContextManager
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent

class PreviousChunkTool(
    private val chunkManager: ChunkManager,
    private val contextManager: ContextManager
) {
    fun handle(): CallToolResult {
        val chunk = chunkManager.previous()
        return if (chunk != null) {
            contextManager.recordAction("Moved to previous chunk")
            CallToolResult(content = listOf(TextContent(chunk)))
        } else {
            CallToolResult(content = listOf(TextContent("Error: Already at the first chunk")), isError = true)
        }
    }
}
