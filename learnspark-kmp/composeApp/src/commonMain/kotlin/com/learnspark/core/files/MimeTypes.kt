package com.learnspark.core.files

/**
 * R5d：根据文件扩展名返回 MIME（用于 openWithSystem + HTTP download）。
 * 与服务端 FileDownloadController.guessMediaType 保持一致。
 */
fun guessMimeType(fileType: String?, fileName: String = ""): String {
    val ext = (fileType?.removePrefix(".") ?: fileName.substringAfterLast('.', "")).lowercase()
    return when (ext) {
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "doc" -> "application/msword"
        "ppt" -> "application/vnd.ms-powerpoint"
        "xls" -> "application/vnd.ms-excel"
        "md", "markdown", "txt", "log", "rst", "adoc", "org", "tex" -> "text/plain"
        "html", "htm" -> "text/html"
        "xml" -> "application/xml"
        "json" -> "application/json"
        "csv" -> "text/csv"
        "enex" -> "application/xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "tiff", "tif" -> "image/tiff"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "mp4" -> "video/mp4"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
    }
}

/** 客户端 view strategy 判定（按扩展名）。 */
enum class ViewKind { IMAGE, TEXT, OPEN_EXTERNAL }

/** 根据文件类型决定如何呈现。 */
fun classifyForView(fileType: String?, fileName: String = ""): ViewKind {
    val ext = (fileType?.removePrefix(".") ?: fileName.substringAfterLast('.', "")).lowercase()
    return when (ext) {
        "png", "jpg", "jpeg", "gif", "bmp", "webp", "svg" -> ViewKind.IMAGE
        "md", "markdown", "txt", "log", "rst", "adoc", "org", "tex",
        "json", "csv", "xml", "html", "htm", "enex" -> ViewKind.TEXT
        else -> ViewKind.OPEN_EXTERNAL
    }
}
