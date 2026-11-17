import UIKit
import Shared
import FirebaseAuth
import FirebaseCore
import GoogleSignIn

final class IosAuthService: AuthService {

    func currentUser() -> AuthUser? {
        guard let user = Auth.auth().currentUser else { return nil }
        return Self.map(user)
    }

    func signUp(email: String, password: String, displayName: String, onResult: @escaping (AuthResult) -> Void) {
        Auth.auth().createUser(withEmail: email, password: password) { result, error in
            guard let user = result?.user else {
                onResult(AuthResult(user: nil, error: Self.message(error)))
                return
            }
            let change = user.createProfileChangeRequest()
            change.displayName = displayName
            change.commitChanges { _ in
                onResult(AuthResult(user: Self.map(user), error: nil))
            }
        }
    }

    func signIn(email: String, password: String, onResult: @escaping (AuthResult) -> Void) {
        Auth.auth().signIn(withEmail: email, password: password) { result, error in
            if let user = result?.user {
                onResult(AuthResult(user: Self.map(user), error: nil))
            } else {
                onResult(AuthResult(user: nil, error: Self.message(error)))
            }
        }
    }

    func signInWithGoogle(onResult: @escaping (AuthResult) -> Void) {
        guard let clientID = FirebaseApp.app()?.options.clientID,
              let presenter = Self.topViewController() else {
            onResult(AuthResult(user: nil, error: "Google sign-in unavailable"))
            return
        }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { signInResult, error in
            guard let gUser = signInResult?.user, let idToken = gUser.idToken?.tokenString else {
                let cancelled = (error as NSError?)?.code == GIDSignInError.canceled.rawValue
                onResult(AuthResult(user: nil, error: cancelled ? "Sign-in cancelled" : Self.message(error)))
                return
            }
            let credential = GoogleAuthProvider.credential(
                withIDToken: idToken,
                accessToken: gUser.accessToken.tokenString
            )
            Auth.auth().signIn(with: credential) { result, error in
                if let user = result?.user {
                    onResult(AuthResult(user: Self.map(user), error: nil))
                } else {
                    onResult(AuthResult(user: nil, error: Self.message(error)))
                }
            }
        }
    }

    func signOut() {
        try? Auth.auth().signOut()
        GIDSignIn.sharedInstance.signOut()
    }

    private static func map(_ user: FirebaseAuth.User) -> AuthUser {
        AuthUser(uid: user.uid, email: user.email ?? "", displayName: user.displayName ?? "")
    }

    private static func message(_ error: Error?) -> String {
        guard let error = error as NSError? else { return "Something went wrong" }
        switch AuthErrorCode(rawValue: error.code) {
        case .invalidEmail: return "That email address looks invalid"
        case .emailAlreadyInUse: return "An account with this email already exists"
        case .weakPassword: return "Password is too weak (min 6 characters)"
        case .wrongPassword, .invalidCredential, .userNotFound: return "Wrong email or password"
        case .networkError: return "No connection, try again"
        case .tooManyRequests: return "Too many attempts, try later"
        default: return error.localizedDescription
        }
    }

    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        var top = scene?.windows.first(where: { $0.isKeyWindow })?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
