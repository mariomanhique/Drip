import Foundation
import Shared
import FirebaseFirestore
import FirebaseFunctions
import FirebaseStorage

final class IosBackend: Backend {
    private let db = Firestore.firestore()
    private let functions = Functions.functions(region: "europe-west1")
    private var listeners: [String: ListenerRegistration] = [:]
    private let serverTime = "__serverTime__"

    func listen(id: String, path: String, group: Bool, whereField: String?, whereValue: String?,
                onData: @escaping (String) -> Void, onError: @escaping (String) -> Void) {
        stopListening(id: id)
        var query: Query = group ? db.collectionGroup(path) : db.collection(path)
        if let field = whereField { query = query.whereField(field, isEqualTo: whereValue as Any) }
        listeners[id] = query.addSnapshotListener { snap, error in
            if let error = error { onError(error.localizedDescription); return }
            let docs: [[String: Any]] = (snap?.documents ?? []).map { d in
                ["id": d.documentID, "path": d.reference.path,
                 "data": self.toJson(d.data(with: .estimate))]
            }
            onData(self.encode(docs))
        }
    }

    func stopListening(id: String) {
        listeners.removeValue(forKey: id)?.remove()
    }

    func set(path: String, json: String, merge: Bool, onDone: @escaping (String?) -> Void) {
        guard let data = decode(json) as? [String: Any] else { onDone("Bad data"); return }
        db.document(path).setData(fromJson(data) as? [String: Any] ?? [:], merge: merge) { error in
            onDone(error?.localizedDescription)
        }
    }

    func delete(path: String, onDone: @escaping (String?) -> Void) {
        db.document(path).delete { error in onDone(error?.localizedDescription) }
    }

    func call(name: String, argsJson: String, onDone: @escaping (String?, String?) -> Void) {
        let args = decode(argsJson)
        functions.httpsCallable(name).call(args) { result, error in
            if let error = error { onDone(nil, error.localizedDescription); return }
            let value = self.toJson(result?.data as Any)
            onDone(JSONSerialization.isValidJSONObject(value) ? self.encode(value) : "{}", nil)
        }
    }

    func upload(storagePath: String, localUri: String, onDone: @escaping (String?, String?) -> Void) {
        guard let url = URL(string: localUri) else { onDone(nil, "Bad file"); return }
        let ref = Storage.storage().reference().child(storagePath)
        let meta = StorageMetadata()
        meta.contentType = "image/jpeg"
        ref.putFile(from: url, metadata: meta) { _, error in
            if let error = error { onDone(nil, error.localizedDescription); return }
            ref.downloadURL { url, error in
                onDone(url?.absoluteString, error?.localizedDescription)
            }
        }
    }

    private func encode(_ value: Any) -> String {
        guard JSONSerialization.isValidJSONObject(value),
              let data = try? JSONSerialization.data(withJSONObject: value),
              let s = String(data: data, encoding: .utf8) else { return "[]" }
        return s
    }

    private func decode(_ json: String) -> Any {
        guard let data = json.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed]) else { return [:] }
        return obj
    }

    private func toJson(_ v: Any) -> Any {
        switch v {
        case let t as Timestamp: return Int64(t.dateValue().timeIntervalSince1970 * 1000)
        case let d as [String: Any]: return d.mapValues { toJson($0) }
        case let a as [Any]: return a.map { toJson($0) }
        default: return v
        }
    }

    private func fromJson(_ v: Any) -> Any {
        switch v {
        case let s as String where s == serverTime: return FieldValue.serverTimestamp()
        case let d as [String: Any]: return d.mapValues { fromJson($0) }
        case let a as [Any]: return a.map { fromJson($0) }
        case is NSNull: return NSNull()
        default: return v
        }
    }
}
