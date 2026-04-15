import SwiftUI

/// Google Sign-In screen — shown when the user is not authenticated
struct SignInView: View {
    @EnvironmentObject var auth: GoogleAuthService

    var body: some View {
        VStack(spacing: 32) {
            Spacer()

            // App icon area
            Image(systemName: "headphones.circle.fill")
                .font(.system(size: 80))
                .foregroundStyle(.tint)

            VStack(spacing: 8) {
                Text("Shelf")
                    .font(.largeTitle)
                    .fontWeight(.bold)

                Text("Your media, on the go")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            // Sign in button
            Button(action: { auth.signIn() }) {
                HStack(spacing: 12) {
                    Image(systemName: "person.crop.circle")
                        .font(.title3)
                    Text("Sign in with Google")
                        .font(.headline)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .padding(.horizontal, 40)

            if let error = auth.error {
                Text(error)
                    .font(.caption)
                    .foregroundStyle(.red)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 40)
            }

            Text("Shelf connects to Google Drive to access your files. Your files stay on your device after download.")
                .font(.caption)
                .foregroundStyle(.tertiary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)

            Spacer()
                .frame(height: 40)
        }
    }
}
