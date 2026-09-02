package tools

import ChunkManager
import ContextManager
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent

class NextChunkTool(
    private val chunkManager: ChunkManager,
    private val contextManager: ContextManager
) {
    fun handle(): CallToolResult {
        val chunk = chunkManager.next()
        return if (chunk != null) {
            contextManager.recordAction("Moved to next chunk")
            CallToolResult(content = listOf(TextContent(chunk)))
        } else {
            CallToolResult(content = listOf(TextContent("Error: Already at the last chunk")), isError = true)
        }
    }
}
