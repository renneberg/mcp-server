class SummaryManager {
    fun summarizeChunk(chunk: String): String {
        // Simple heuristic summary: first line + length
        val lines = chunk.lines()
        return "Chunk starting with: '${lines.firstOrNull()?.take(50)}...' (Total lines: ${lines.size})"
    }
}
