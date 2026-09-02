class LanguageParser {
    fun splitByFunctions(content: String): List<String> {
        val lines = content.lines()
        val result = mutableListOf<String>()
        var currentBlock = StringBuilder()
        var braceCount = 0
        var inFunction = false

        // Simple heuristic: looks for lines starting with 'fun' or having 'fun' after visibility modifiers
        val functionStartRegex = Regex("""^\s*(?:(?:private|public|protected|internal|override|suspend)\s+)*fun\s+\w+""")

        for (line in lines) {
            if (!inFunction && functionStartRegex.containsMatchIn(line)) {
                inFunction = true
            }

            if (inFunction) {
                currentBlock.append(line).append("\n")
                braceCount += line.count { it == '{' }
                braceCount -= line.count { it == '}' }

                if (braceCount <= 0 && line.contains('}')) {
                    result.add(currentBlock.toString().trim())
                    currentBlock = StringBuilder()
                    inFunction = false
                    braceCount = 0
                }
            }
        }
        
        if (currentBlock.isNotEmpty()) {
            val last = currentBlock.toString().trim()
            if (last.isNotEmpty()) result.add(last)
        }

        return if (result.isEmpty() && content.isNotEmpty()) listOf(content) else result
    }
}
