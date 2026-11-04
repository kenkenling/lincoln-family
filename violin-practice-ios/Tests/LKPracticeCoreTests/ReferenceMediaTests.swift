import Testing
@testable import LKPracticeCore

@Suite("Reference media")
struct ReferenceMediaTests {
    @Test
    func parsesCommonYouTubeLinks() {
        let videoID = "c79nrCCcKuM"
        #expect(
            YouTubeReference.videoID(
                from: "https://www.youtube.com/watch?v=\(videoID)&feature=shared"
            ) == videoID
        )
        #expect(
            YouTubeReference.videoID(from: "https://youtu.be/\(videoID)?si=test") == videoID
        )
        #expect(
            YouTubeReference.videoID(from: "https://youtube.com/shorts/\(videoID)") == videoID
        )
        #expect(
            YouTubeReference.videoID(
                from: "https://www.youtube-nocookie.com/embed/\(videoID)"
            ) == videoID
        )
        #expect(YouTubeReference.videoID(from: videoID) == videoID)
    }

    @Test
    func rejectsInvalidYouTubeLinks() {
        #expect(YouTubeReference.videoID(from: "https://example.com/video") == nil)
        #expect(YouTubeReference.videoID(from: "https://youtube.com/watch?v=short") == nil)
        #expect(YouTubeReference.videoID(from: "") == nil)
    }

    @Test
    func assignmentValidatesReferenceSource() throws {
        let date = try DateKey("2026-11-04")
        let valid = try Assignment(
            id: "media",
            childProfileID: "student",
            title: "Tremolo",
            startDate: date,
            endDate: date,
            enabledModes: [.listen, .playAlong],
            sourceType: .youtube,
            referenceURL: "https://youtu.be/c79nrCCcKuM",
            referenceStartSeconds: 15,
            sheetMusicReference: "lkpractice-media://sheet-music/id/music.pdf",
            createdAtMillis: 1,
            updatedAtMillis: 1
        )
        #expect(valid.referenceStartSeconds == 15)
        #expect(valid.sheetMusicReference != nil)

        #expect(throws: AssignmentValidationError.invalidReferenceVideo) {
            _ = try Assignment(
                id: "invalid-media",
                childProfileID: "student",
                title: "Tremolo",
                startDate: date,
                endDate: date,
                enabledModes: [.listen],
                sourceType: .youtube,
                referenceURL: "https://example.com/not-youtube",
                createdAtMillis: 1,
                updatedAtMillis: 1
            )
        }
    }
}
