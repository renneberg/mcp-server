import java.io.File

class FileParser(private val languageParser: LanguageParser) {
    private val MAX_CHARS_PER_CHUNK = 16000 // Roughly 4000 tokens (assuming ~4 chars per token)

    fun parseFile(path: String, mode: String): List<String> {
        val file = File(path)
        if (!file.exists() || !file.isFile) {
            throw IllegalArgumentException("File not found at $path")
        }
        val content = file.readText()
        
        val rawChunks = if (mode == "function") {
            languageParser.splitByFunctions(content)
        } else {
            content.lines().chunked(50).map { it.joinToString("\n") }
        }

        // Apply safety net: Truncate each chunk if it's too large
        return rawChunks.map { chunk ->
            if (chunk.length > MAX_CHARS_PER_CHUNK) {
                chunk.take(MAX_CHARS_PER_CHUNK) + "\n\n[... Chunk truncated due to size limits (4000 tokens) ...]"
            } else {
                chunk
            }
        }
    }

    fun searchText(query: String, rootPath: String): List<String> {
        val results = mutableListOf<String>()
        File(rootPath).walk().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.forEach { file ->
            val lines = try { file.readLines() } catch (e: Exception) { emptyList() }
            lines.forEachIndexed { index, line ->
                if (line.contains(query, ignoreCase = true)) {
                    val result = "${file.path}:${index + 1}: $line"
                    results.add(result.take(1000)) // Safety: limit line length in search
                }
            }
        }
        return results.take(20) // Limit total search results
    }
}
