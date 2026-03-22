package dev.onelenyk.bringalive.workspace

import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

internal actual fun readFile(path: String): String? {
    return NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
}

internal actual fun writeFile(path: String, content: String) {
    (content as NSString).writeToFile(path, true, NSUTF8StringEncoding, null)
}

internal actual fun fileExists(path: String): Boolean {
    return NSFileManager.defaultManager.fileExistsAtPath(path)
}

internal actual fun ensureDir(path: String) {
    NSFileManager.defaultManager.createDirectoryAtPath(path, true, null, null)
}

internal actual fun listDir(path: String): List<String>? {
    return NSFileManager.defaultManager.contentsOfDirectoryAtPath(path, null)
        ?.mapNotNull { it as? String }
}
