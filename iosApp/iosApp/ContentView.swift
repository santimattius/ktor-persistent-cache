import Foundation
import KtorPersistentCacheSample
import SwiftUI

struct ContentView: View {
    @State private var okioState: BackendCheckState = .idle
    @State private var kotlinxIoState: BackendCheckState = .idle

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Persistent cache backend sample")
                        .font(.title2)
                        .bold()
                    Text("Each check uses an offline MockEngine and a fresh client to verify persisted data.")
                        .font(.body)
                        .foregroundStyle(.secondary)
                    BackendCheckCard(
                        backend: "Okio",
                        state: okioState,
                        onRun: runOkio
                    )
                    BackendCheckCard(
                        backend: "kotlinx-io",
                        state: kotlinxIoState,
                        onRun: runKotlinxIo
                    )
                }
                .padding()
            }
            .navigationTitle("Backend checks")
        }
    }

    private func runOkio() {
        okioState = .running
        PersistentCacheSample(rootDirectory: CacheRoot.path).verifyOkio { result, error in
            Task { @MainActor in
                okioState = BackendCheckState(result: result, error: error)
            }
        }
    }

    private func runKotlinxIo() {
        kotlinxIoState = .running
        PersistentCacheSample(rootDirectory: CacheRoot.path).verifyKotlinxIo { result, error in
            Task { @MainActor in
                kotlinxIoState = BackendCheckState(result: result, error: error)
            }
        }
    }
}

private struct BackendCheckCard: View {
    let backend: String
    let state: BackendCheckState
    let onRun: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(backend)
                .font(.headline)
            Text(state.status)
                .foregroundStyle(state.color)
            if let result = state.result {
                Text("First client engine requests: \(result.firstClientEngineRequests)")
                Text("Second client engine requests: \(result.secondClientEngineRequests)")
                Text(verbatim: "Second client read persisted data: \(result.secondClientReadPersistedData)")
                Text("Response: \(result.responseBody)")
            }
            if let message = state.message {
                Text(message)
                    .foregroundStyle(.red)
            }
            Button("Run \(backend) check", action: onRun)
                .buttonStyle(.borderedProminent)
                .disabled(state.isRunning)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(.thinMaterial, in: .rect(cornerRadius: 12))
        .accessibilityElement(children: .contain)
        .accessibilityLabel("\(backend) backend check")
    }
}

private enum BackendCheckState {
    case idle
    case running
    case passed(PersistenceCheckResult)
    case failed(result: PersistenceCheckResult?, message: String)

    init(result: PersistenceCheckResult?, error: Error?) {
        if let error {
            self = .failed(result: nil, message: error.localizedDescription)
        } else if let result, result.secondClientReadPersistedData {
            self = .passed(result)
        } else if let result {
            self = .failed(
                result: result,
                message: "The second client made an engine request instead of reading persisted data."
            )
        } else {
            self = .failed(result: nil, message: "The check completed without a result.")
        }
    }

    var isRunning: Bool {
        if case .running = self {
            return true
        }
        return false
    }

    var status: String {
        switch self {
        case .idle:
            "Idle"
        case .running:
            "Running"
        case .passed:
            "Pass"
        case .failed:
            "Fail"
        }
    }

    var color: Color {
        switch self {
        case .idle, .running:
            .primary
        case .passed:
            .green
        case .failed:
            .red
        }
    }

    var result: PersistenceCheckResult? {
        switch self {
        case let .passed(result):
            result
        case let .failed(result, _):
            result
        case .idle, .running:
            nil
        }
    }

    var message: String? {
        guard case let .failed(_, message) = self else { return nil }
        return message
    }

}

private enum CacheRoot {
    static var path: String {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first?.path
            ?? FileManager.default.temporaryDirectory.path
    }
}

#Preview {
    ContentView()
}
