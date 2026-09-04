package com.team.careerlab.job.service;

import com.team.careerlab.job.dto.BookmarkResponse;
import com.team.careerlab.job.exception.JobException;
import com.team.careerlab.job.repository.BookmarkCommandRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostingBookmarkService {

    private final BookmarkCommandRepository bookmarks;

    public PostingBookmarkService(BookmarkCommandRepository bookmarks) {
        this.bookmarks = bookmarks;
    }

    @Transactional
    public BookmarkResponse update(Long userId, Long postingId, Boolean bookmarked) {
        if (bookmarked == null) {
            throw JobException.invalidBookmarkRequest();
        }
        if (!bookmarks.postingExists(postingId)) {
            throw JobException.postingNotFound();
        }
        if (bookmarked) {
            bookmarks.save(userId, postingId);
        } else {
            bookmarks.delete(userId, postingId);
        }
        return new BookmarkResponse(postingId, bookmarked);
    }
}
