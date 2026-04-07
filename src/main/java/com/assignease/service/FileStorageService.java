package com.assignease.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import java.util.zip.*;

/**
 * Central file storage service.
 *
 * ALL file reads and writes go through this service.
 * Files are stored under app.upload.dir (configurable per profile).
 * The DB stores only the relative path from upload.dir root.
 *
 * Example:
 *   file saved to:  /app/uploads/enrollments/receipts/abc123_receipt.jpg
 *   DB stores:      enrollments/receipts/abc123_receipt.jpg
 *   download via:   GET /api/files/{encoded-path}  (auth-gated)
 *
 * NEVER expose raw disk paths to the frontend.
 * NEVER serve files directly via static resource mapping (/uploads/**).
 */
@Slf4j
@Service
public class FileStorageService {

    @Value("${app.upload.dir:uploads/}")
    private String uploadDir;

    // ── Save a raw file ─────────────────────────────────────────────────────
    public String save(MultipartFile file, String subDir) throws IOException {
        String cleanSub = sanitizeSubDir(subDir);
        Path dir = Paths.get(uploadDir, cleanSub);
        Files.createDirectories(dir);

        String ext      = getExtension(file.getOriginalFilename());
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;
        Files.write(dir.resolve(filename), file.getBytes());

        log.info("Saved file: {}/{}", cleanSub, filename);
        return cleanSub + "/" + filename;
    }

    // ── Save and wrap in ZIP (for writer submission ZIPs) ──────────────────
    public String saveAsZip(MultipartFile file, String subDir, String label) throws IOException {
        String cleanSub = sanitizeSubDir(subDir);
        Path dir = Paths.get(uploadDir, cleanSub);
        Files.createDirectories(dir);

        String safe    = label.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        String zipName = UUID.randomUUID().toString().substring(0, 8) + "_" + safe + ".zip";
        Path   zipPath = dir.resolve(zipName);

        String innerName = file.getOriginalFilename() != null
            ? file.getOriginalFilename() : "work.pdf";

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            zos.putNextEntry(new ZipEntry(innerName));
            zos.write(file.getBytes());
            zos.closeEntry();
        }

        log.info("Saved ZIP: {}/{}", cleanSub, zipName);
        return cleanSub + "/" + zipName;
    }

    // ── Read file bytes (for streaming download) ────────────────────────────
    public byte[] read(String relativePath) throws IOException {
        Path path = resolveAndValidate(relativePath);
        return Files.readAllBytes(path);
    }

    // ── Delete a file ───────────────────────────────────────────────────────
    public void delete(String relativePath) {
        if (relativePath == null) return;
        try {
            Path path = resolveAndValidate(relativePath);
            Files.deleteIfExists(path);
            log.info("Deleted file: {}", relativePath);
        } catch (Exception e) {
            log.warn("Could not delete file {}: {}", relativePath, e.getMessage());
        }
    }

    // ── Resolve a relative path — prevents path traversal attacks ──────────
    public Path resolveAndValidate(String relativePath) throws IOException {
        if (relativePath == null || relativePath.isBlank())
            throw new IOException("Empty file path");

        // Reject path traversal attempts
        if (relativePath.contains("..") || relativePath.contains("~") || relativePath.startsWith("/"))
            throw new SecurityException("Invalid file path: " + relativePath);

        Path base     = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path resolved = base.resolve(relativePath).normalize();

        // Ensure resolved path is still inside the upload directory
        if (!resolved.startsWith(base))
            throw new SecurityException("Path traversal detected: " + relativePath);

        if (!Files.exists(resolved))
            throw new IOException("File not found: " + relativePath);

        return resolved;
    }

    // ── Guess content type from extension ──────────────────────────────────
    public String contentType(String filename) {
        if (filename == null) return "application/octet-stream";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf"))  return "application/pdf";
        if (lower.endsWith(".zip"))  return "application/zip";
        if (lower.endsWith(".png"))  return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".doc"))  return "application/msword";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".xls"))  return "application/vnd.ms-excel";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) return "application/vnd.ms-powerpoint";
        if (lower.endsWith(".txt"))  return "text/plain";
        return "application/octet-stream";
    }

    // ── Helpers ─────────────────────────────────────────────────────────────
    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".bin";
        return filename.substring(filename.lastIndexOf('.'));
    }

    private String sanitizeSubDir(String subDir) {
        // Only allow alphanumeric, /, -
        return subDir.replaceAll("[^a-zA-Z0-9/_\\-]", "").replaceAll("\\.\\.", "");
    }
}
