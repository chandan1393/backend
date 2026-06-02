package com.assignease.repository;

import com.assignease.entity.BlogPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BlogPostRepository extends JpaRepository<BlogPost, Long> {
    Page<BlogPost> findByPublishedTrueOrderByCreatedAtDesc(Pageable pageable);
    List<BlogPost> findByPublishedTrueAndFeaturedTrueOrderByCreatedAtDesc();
    Optional<BlogPost> findBySlugAndPublishedTrue(String slug);
    Optional<BlogPost> findBySlug(String slug);
    boolean existsBySlug(String slug);
    Page<BlogPost> findByCategoryAndPublishedTrueOrderByCreatedAtDesc(String category, Pageable pageable);
}
