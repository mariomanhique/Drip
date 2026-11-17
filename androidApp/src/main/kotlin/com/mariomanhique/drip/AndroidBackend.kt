package com.mariomanhique.drip

import android.content.Context
import android.net.Uri
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.mariomanhique.drip.data.Backend
import org.json.JSONArray
import org.json.JSONObject

class AndroidBackend(private val context: Context) : Backend {
    private val db get() = FirebaseFirestore.getInstance()
    private val functions get() = FirebaseFunctions.getInstance("europe-west1")
    private val listeners = mutableMapOf<String, ListenerRegistration>()

    override fun listen(
        id: String,
        path: String,
        group: Boolean,
        whereField: String?,
        whereValue: String?,
        onData: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        stopListening(id)
        var q: Query = if (group) db.collectionGroup(path) else db.collection(path)
        if (whereField != null) q = q.whereEqualTo(whereField, whereValue)
        listeners[id] = q.addSnapshotListener { snap, e ->
            if (e != null) { onError(e.message ?: "Listen failed"); return@addSnapshotListener }
            val arr = JSONArray()
            snap?.documents?.forEach { d ->
                arr.put(JSONObject().put("id", d.id).put("path", d.reference.path).put("data", toJson(d)))
            }
            onData(arr.toString())
        }
    }

    override fun stopListening(id: String) {
        listeners.remove(id)?.remove()
    }

    override fun set(path: String, json: String, merge: Boolean, onDone: (String?) -> Unit) {
        val data = fromJson(JSONObject(json))
        val task = if (merge) db.document(path).set(data, SetOptions.merge()) else db.document(path).set(data)
        task.addOnCompleteListener { onDone(it.exception?.message) }
    }

    override fun delete(path: String, onDone: (String?) -> Unit) {
        db.document(path).delete().addOnCompleteListener { onDone(it.exception?.message) }
    }

    override fun call(name: String, argsJson: String, onDone: (String?, String?) -> Unit) {
        functions.getHttpsCallable(name).call(fromJson(JSONObject(argsJson))).addOnCompleteListener { t ->
            if (t.isSuccessful) {
                val data = t.result?.getData()
                onDone(if (data == null) "{}" else toJsonValue(data).let { if (it is JSONObject || it is JSONArray) it.toString() else "{}" }, null)
            } else {
                onDone(null, t.exception?.message ?: "Request failed")
            }
        }
    }

    override fun upload(storagePath: String, localUri: String, onDone: (String?, String?) -> Unit) {
        val uri = Uri.parse(localUri)
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val ref = FirebaseStorage.getInstance().reference.child(storagePath)
        val meta = StorageMetadata.Builder().setContentType(mime).build()
        ref.putFile(uri, meta)
            .continueWithTask { t -> if (t.isSuccessful) ref.downloadUrl else throw t.exception ?: Exception("Upload failed") }
            .addOnCompleteListener { t ->
                if (t.isSuccessful) onDone(t.result.toString(), null) else onDone(null, t.exception?.message ?: "Upload failed")
            }
    }

    private fun toJson(d: DocumentSnapshot): JSONObject {
        val out = JSONObject()
        d.getData(ServerTimestampBehavior.ESTIMATE)?.forEach { (k, v) -> out.put(k, toJsonValue(v)) }
        return out
    }

    private fun toJsonValue(v: Any?): Any? = when (v) {
        null -> JSONObject.NULL
        is Timestamp -> v.toDate().time
        is Map<*, *> -> JSONObject().also { o -> v.forEach { (k, x) -> o.put(k.toString(), toJsonValue(x)) } }
        is Iterable<*> -> JSONArray().also { a -> v.forEach { a.put(toJsonValue(it)) } }
        else -> v
    }

    private fun fromJson(o: JSONObject): Map<String, Any?> =
        o.keys().asSequence().associateWith { fromJsonValue(o.get(it)) }

    private fun fromJsonValue(v: Any?): Any? = when (v) {
        JSONObject.NULL -> null
        Backend.SERVER_TIME -> FieldValue.serverTimestamp()
        is JSONObject -> fromJson(v)
        is JSONArray -> (0 until v.length()).map { fromJsonValue(v.get(it)) }
        else -> v
    }
}
