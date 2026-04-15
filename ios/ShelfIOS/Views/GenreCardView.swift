import SwiftUI

/// A visual card for the genre browse sheet — shows a cover art collage, genre name, and book count.
struct GenreCardView: View {
    let genre: String
    let bookCount: Int
    let covers: [UIImage]
    let isSelected: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Cover art collage
            coverCollage
                .frame(height: 140)
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(isSelected ? Color.accentColor : Color.clear, lineWidth: 3)
                )

            // Genre name and book count
            VStack(alignment: .leading, spacing: 2) {
                Text(genre)
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .lineLimit(1)

                Text("\(bookCount) \(bookCount == 1 ? "book" : "books")")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    // MARK: - Cover Collage Layouts

    @ViewBuilder
    private var coverCollage: some View {
        switch covers.count {
        case 0:
            // Placeholder icon
            Rectangle()
                .fill(Color(.secondarySystemFill))
                .overlay {
                    Image(systemName: "books.vertical")
                        .font(.largeTitle)
                        .foregroundStyle(.tertiary)
                }

        case 1:
            // Single cover fills the card
            Image(uiImage: covers[0])
                .resizable()
                .aspectRatio(contentMode: .fill)

        case 2:
            // Side by side
            HStack(spacing: 2) {
                Image(uiImage: covers[0])
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .clipped()
                Image(uiImage: covers[1])
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .clipped()
            }

        case 3:
            // 1 large on the left + 2 stacked on the right
            HStack(spacing: 2) {
                Image(uiImage: covers[0])
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .clipped()
                    .frame(maxWidth: .infinity)

                VStack(spacing: 2) {
                    Image(uiImage: covers[1])
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .clipped()
                    Image(uiImage: covers[2])
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .clipped()
                }
                .frame(maxWidth: .infinity)
            }

        default:
            // 2x2 grid (4+ covers, use first 4)
            VStack(spacing: 2) {
                HStack(spacing: 2) {
                    Image(uiImage: covers[0])
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .clipped()
                    Image(uiImage: covers[1])
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .clipped()
                }
                HStack(spacing: 2) {
                    Image(uiImage: covers[2])
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .clipped()
                    Image(uiImage: covers[3])
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .clipped()
                }
            }
        }
    }
}
