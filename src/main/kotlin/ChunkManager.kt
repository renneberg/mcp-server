class ChunkManager {
    private var chunks: List<String> = emptyList()
    private var currentChunkIndex: Int = -1
    private var currentFilePath: String? = null

    fun setChunks(path: String, newChunks: List<String>) {
        currentFilePath = path
        chunks = newChunks
        currentChunkIndex = 0
    }

    fun next(): String? {
        if (currentChunkIndex + 1 < chunks.size) {
            currentChunkIndex++
            return chunks[currentChunkIndex]
        }
        return null
    }

    fun previous(): String? {
        if (currentChunkIndex > 0) {
            currentChunkIndex--
            return chunks[currentChunkIndex]
        }
        return null
    }

    fun jumpTo(predicate: (String) -> Boolean): String? {
        val index = chunks.indexOfFirst(predicate)
        if (index != -1) {
            currentChunkIndex = index
            return chunks[currentChunkIndex]
        }
        return null
    }

    fun getStatus(): String {
        return "File: ${currentFilePath ?: "None"}, Chunk: ${currentChunkIndex + 1}/${chunks.size}"
    }

    fun getCurrentChunk(): String? = chunks.getOrNull(currentChunkIndex)

    fun reset() {
        chunks = emptyList()
        currentChunkIndex = -1
        currentFilePath = null
    }
}
