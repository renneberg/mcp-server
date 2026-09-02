package tools

import ChunkManager
import ContextManager
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent

class ResetTool(
    private val chunkManager: ChunkManager,
    private val contextManager: ContextManager
) {
    fun handle(): CallToolResult {
        chunkManager.reset()
        contextManager.reset()
        return CallToolResult(content = listOf(TextContent("State reset successfully.")))
    }
}
