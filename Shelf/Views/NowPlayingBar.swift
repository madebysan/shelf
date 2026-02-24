import SwiftUI

/// Persistent mini player bar at the bottom of the library view
struct NowPlayingBar: View {
    @EnvironmentObject var playerVM: PlayerViewModel
    @EnvironmentObject var miniPlayerController: MiniPlayerController
    @Binding var showPlayer: Bool

    var body: some View {
        HStack(spacing: 12) {
            // Cover art thumbnail
            if let book = playerVM.currentBook {
                Image(nsImage: book.coverImage)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: 44, height: 44)
                    .clipShape(RoundedRectangle(cornerRadius: 6))
            }

            // Book info
            VStack(alignment: .leading, spacing: 2) {
                Text(playerVM.currentBook?.displayTitle ?? "")
                    .font(.caption)
                    .fontWeight(.medium)
                    .lineLimit(1)

                Text(playerVM.currentBook?.displayAuthor ?? "")
                    .font(.caption2)
                    .foregroundColor(.secondary)
                    .lineLimit(1)
            }
            .frame(maxWidth: 200, alignment: .leading)

            Spacer()

            // Scrubber (mini)
            VStack(spacing: 2) {
                Slider(
                    value: Binding(
                        get: { playerVM.audioService.currentTime },
                        set: { playerVM.audioService.seek(to: $0) }
                    ),
                    in: 0...max(playerVM.audioService.duration, 1)
                )
                .controlSize(.small)

                HStack {
                    Text(Book.formatScrubberTime(playerVM.audioService.currentTime))
                        .font(.system(size: 9, design: .monospaced))
                        .foregroundColor(.secondary)
                    Spacer()
                    Text(Book.formatScrubberTime(playerVM.audioService.duration))
                        .font(.system(size: 9, design: .monospaced))
                        .foregroundColor(.secondary)
                }
            }
            .frame(maxWidth: 300)

            Spacer()

            // Transport controls
            HStack(spacing: 16) {
                Button { playerVM.audioService.skipBackward() } label: {
                    Image(systemName: "gobackward.30")
                        .font(.title3)
                }
                .buttonStyle(.plain)
                .help("Skip Back 30s")

                Button { playerVM.audioService.togglePlayPause() } label: {
                    Image(systemName: playerVM.audioService.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                        .font(.title)
                }
                .buttonStyle(.plain)
                .help(playerVM.audioService.isPlaying ? "Pause" : "Play")

                Button { playerVM.audioService.skipForward() } label: {
                    Image(systemName: "goforward.30")
                        .font(.title3)
                }
                .buttonStyle(.plain)
                .help("Skip Forward 30s")
            }
            .contentShape(Rectangle())

            // Mini player toggle
            Button { miniPlayerController.toggle(playerVM: playerVM) } label: {
                Image(systemName: "pip.fill")
                    .font(.title3)
                    .foregroundColor(miniPlayerController.isVisible ? .accentColor : .secondary)
            }
            .buttonStyle(.plain)
            .help("Mini Player")

            // Expand to full player
            Button { showPlayer.toggle() } label: {
                Image(systemName: "chevron.up.circle")
                    .font(.title3)
                    .foregroundColor(.secondary)
            }
            .buttonStyle(.plain)
            .help("Open Player")
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(.bar)
    }
}
