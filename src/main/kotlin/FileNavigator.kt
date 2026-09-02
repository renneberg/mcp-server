import java.io.File

class FileNavigator {
    private var currentFile: File? = null
    private var chunks: List<String> = emptyList()
    private var currentChunkIndex: Int = -1

    fun openFile(path: String, mode: String = "line"): String {
        val file = File(path)
        if (!file.exists() || !file.isFile) {
            return "Error: File not found at $path"
        }
        currentFile = file
        val content = file.readText()
        
        chunks = if (mode == "function") {
            splitByFunctions(content)
        } else {
            // Default: chunk by lines (e.g., 50 lines per chunk)
            content.lines().chunked(50).map { it.joinToString("\n") }
        }
        
        currentChunkIndex = 0
        return if (chunks.isNotEmpty()) chunks[0] else "File is empty"
    }

    fun nextChunk(): String {
        if (currentFile == null) return "Error: No file open"
        if (currentChunkIndex + 1 < chunks.size) {
            currentChunkIndex++
            return chunks[currentChunkIndex]
        }
        return "Error: Already at the last chunk"
    }

    fun previousChunk(): String {
        if (currentFile == null) return "Error: No file open"
        if (currentChunkIndex > 0) {
            currentChunkIndex--
            return chunks[currentChunkIndex]
        }
        return "Error: Already at the first chunk"
    }

    fun jumpToFunction(name: String): String {
        if (currentFile == null) return "Error: No file open"
        val index = chunks.indexOfFirst { it.contains("fun $name") || it.contains("fun  $name") }
        if (index != -1) {
            currentChunkIndex = index
            return chunks[currentChunkIndex]
        }
        return "Error: Function '$name' not found in current file"
    }

    fun getCurrentContext(): String {
        val fileName = currentFile?.name ?: "None"
        return "File: $fileName, Chunk: ${currentChunkIndex + 1}/${chunks.size}"
    }

    fun reset() {
        currentFile = null
        chunks = emptyList()
        currentChunkIndex = -1
    }
    
    fun searchText(query: String, rootPath: String = "."): List<String> {
        val results = mutableListOf<String>()
        File(rootPath).walk().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.forEach { file ->
            val lines = file.readLines()
            lines.forEachIndexed { index, line ->
                if (line.contains(query, ignoreCase = true)) {
                    results.add("${file.path}:${index + 1}: $line")
                }
            }
        }
        return results.take(20) // Limit results
    }
}
