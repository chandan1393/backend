package com.assignease.controller;

import com.assignease.entity.BlogPost;
import com.assignease.repository.BlogPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import jakarta.transaction.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Transactional
public class BlogController {

    private final BlogPostRepository repo;

    // Public endpoints
    @GetMapping("/api/public/blogs")
    public ResponseEntity<?> getPublished(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        Page<BlogPost> posts = repo.findByPublishedTrueOrderByCreatedAtDesc(
            PageRequest.of(page, size));
        return ResponseEntity.ok(posts);
    }

    @GetMapping("/api/public/blogs/{slug}")
    public ResponseEntity<?> getBySlug(@PathVariable String slug) {
        return repo.findBySlugAndPublishedTrue(slug)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/api/public/blogs/featured")
    public ResponseEntity<?> getFeatured() {
        return ResponseEntity.ok(repo.findByPublishedTrueAndFeaturedTrueOrderByCreatedAtDesc());
    }

    // Admin endpoints
    @GetMapping("/api/admin/blogs")
    public ResponseEntity<?> adminGetAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(repo.findAll(
            PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }

    @PostMapping("/api/admin/blogs")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        BlogPost post = new BlogPost();
        applyFields(post, body);
        return ResponseEntity.ok(repo.save(post));
    }

    @PutMapping("/api/admin/blogs/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BlogPost post = repo.findById(id).orElseThrow();
        applyFields(post, body);
        return ResponseEntity.ok(repo.save(post));
    }

    @DeleteMapping("/api/admin/blogs/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        repo.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }

    private void applyFields(BlogPost post, Map<String, Object> body) {
        if (body.containsKey("title"))   post.setTitle((String) body.get("title"));
        if (body.containsKey("slug"))    post.setSlug((String) body.get("slug"));
        if (body.containsKey("excerpt")) post.setExcerpt((String) body.get("excerpt"));
        if (body.containsKey("content")) post.setContent((String) body.get("content"));
        if (body.containsKey("category"))post.setCategory((String) body.get("category"));
        if (body.containsKey("author"))  post.setAuthor((String) body.get("author"));
        if (body.containsKey("readTimeMinutes")) post.setReadTimeMinutes((Integer) body.get("readTimeMinutes"));
        if (body.containsKey("published")) post.setPublished((Boolean) body.get("published"));
        if (body.containsKey("featured"))  post.setFeatured((Boolean) body.get("featured"));
        // Auto-generate slug from title if not provided
        if ((post.getSlug() == null || post.getSlug().isBlank()) && post.getTitle() != null) {
            post.setSlug(post.getTitle().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$",""));
        }
    }
}
