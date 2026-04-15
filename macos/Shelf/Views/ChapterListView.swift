import SwiftUI

/// Scrollable list of chapters with tap-to-jump
struct ChapterListView: View {
    @EnvironmentObject var playerVM: PlayerViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Chapters")
                .font(.caption)
                .fontWeight(.semibold)
                .foregroundColor(.secondary)
                .padding(.horizontal, 4)

            ScrollViewReader { proxy in
                List(Array(playerVM.chapters.enumerated()), id: \.element.id) { index, chapter in
                    Button {
                        playerVM.goToChapter(chapter)
                    } label: {
                        HStack {
                            // Playing indicator
                            if isCurrentChapter(index) {
                                Image(systemName: "speaker.wave.2.fill")
                                    .font(.caption2)
                                    .foregroundColor(.accentColor)
                                    .frame(width: 16)
                            } else {
                                Text("\(index + 1)")
                                    .font(.caption2)
                                    .foregroundColor(.secondary)
                                    .frame(width: 16)
                            }

                            Text(chapter.title)
                                .font(.caption)
                                .lineLimit(1)
                                .foregroundColor(isCurrentChapter(index) ? .accentColor : .primary)

                            Spacer()

                            Text(Book.formatScrubberTime(chapter.startTime))
                                .font(.system(size: 10, design: .monospaced))
                                .foregroundColor(.secondary)
                        }
                        .padding(.vertical, 2)
                    }
                    .buttonStyle(.plain)
                    .id(index)
                }
                .listStyle(.plain)
                .frame(maxHeight: 200)
                .onAppear {
                    // Scroll to the current chapter
                    playerVM.updateCurrentChapter()
                    proxy.scrollTo(playerVM.currentChapterIndex, anchor: .center)
                }
            }
        }
    }

    private func isCurrentChapter(_ index: Int) -> Bool {
        let time = playerVM.audioService.currentTime
        guard index < playerVM.chapters.count else { return false }
        let chapter = playerVM.chapters[index]
        let nextStart = playerVM.chapters[safe: index + 1]?.startTime ?? Double.infinity
        return time >= chapter.startTime && time < nextStart
    }
}
