import SwiftUI

/// Compact floating player view — hosted in the NSPanel mini player
struct MiniPlayerView: View {
    @EnvironmentObject var playerVM: PlayerViewModel

    var body: some View {
        VStack(spacing: 12) {
            // Book info row
            HStack(spacing: 12) {
                // Cover art
                if let book = playerVM.currentBook {
                    Image(nsImage: book.coverImage)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .frame(width: 80, height: 80)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }

                // Title and author
                VStack(alignment: .leading, spacing: 4) {
                    Text(playerVM.currentBook?.displayTitle ?? "No Book")
                        .font(.caption)
                        .fontWeight(.semibold)
                        .lineLimit(2)

                    Text(playerVM.currentBook?.displayAuthor ?? "")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                        .lineLimit(1)

                    if let chapter = playerVM.currentChapterName {
                        Text(chapter)
                            .font(.caption2)
                            .foregroundColor(.secondary)
                            .lineLimit(1)
                    }
                }

                Spacer()
            }

            // Scrubber
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
                    Text("-" + Book.formatScrubberTime(max(playerVM.audioService.duration - playerVM.audioService.currentTime, 0)))
                        .font(.system(size: 9, design: .monospaced))
                        .foregroundColor(.secondary)
                }
            }

            // Transport controls
            HStack(spacing: 20) {
                Button { playerVM.audioService.skipBackward() } label: {
                    Image(systemName: "gobackward.30")
                        .font(.title3)
                }
                .buttonStyle(.plain)

                Button { playerVM.audioService.togglePlayPause() } label: {
                    Image(systemName: playerVM.audioService.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                        .font(.system(size: 36))
                }
                .buttonStyle(.plain)

                Button { playerVM.audioService.skipForward() } label: {
                    Image(systemName: "goforward.30")
                        .font(.title3)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(16)
        .frame(width: 300, height: 200)
    }
}
