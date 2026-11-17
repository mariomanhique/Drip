package com.mariomanhique.drip.data

/**
 * Thin, platform-implemented bridge to Firebase (Firestore, Functions, Storage).
 * Everything crosses the boundary as JSON strings and plain callbacks so Swift can implement it too.
 * Native code converts Firestore Timestamps to epoch millis, and writes the string
 * [SERVER_TIME] as a server timestamp.
 */
interface Backend {
    /**
     * Streams a collection (or collection group) as a JSON array of `{id, path, data}` objects.
     * [whereField] and [whereValue] optionally filter with equality.
     */
    fun listen(
        id: String,
        path: String,
        group: Boolean,
        whereField: String?,
        whereValue: String?,
        onData: (String) -> Unit,
        onError: (String) -> Unit,
    )

    fun stopListening(id: String)
    fun set(path: String, json: String, merge: Boolean, onDone: (String?) -> Unit)
    fun delete(path: String, onDone: (String?) -> Unit)
    fun call(name: String, argsJson: String, onDone: (String?, String?) -> Unit)

    /** Uploads the local file and returns its download URL (or an error). */
    fun upload(storagePath: String, localUri: String, onDone: (String?, String?) -> Unit)

    companion object {
        const val SERVER_TIME = "__serverTime__"
    }
}
