package com.satvault.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class StorageService {

    private final Path rootDir;
    private final EncryptionService encryptionService;

    public StorageService(
            @Value("${satvault.storage.dir}") String storageDir,
            EncryptionService encryptionService) throws IOException {
        this.rootDir = Paths.get(storageDir).toAbsolutePath().normalize();
        this.encryptionService = encryptionService;
        Files.createDirectories(this.rootDir);
    }

    public String store(Long userId, byte[] content, boolean encrypt) throws IOException {
        Path userDir = resolveUserDir(userId);
        Files.createDirectories(userDir);

        String storedFilename = UUID.randomUUID() + (encrypt ? ".enc" : ".bin");
        Path target = userDir.resolve(storedFilename).normalize();
        if (!target.startsWith(userDir)) {
            throw new SecurityException("Invalid storage path");
        }

        byte[] bytesToWrite = encrypt ? encryptionService.encrypt(content) : content;
        Files.write(target, bytesToWrite);
        return storedFilename;
    }

    public byte[] load(Long userId, String storedFilename, boolean encrypted) throws IOException {
        Path file = resolveUserFile(userId, storedFilename);
        if (!Files.exists(file)) {
            throw new IOException("Stored file not found");
        }
        byte[] raw = Files.readAllBytes(file);
        return encrypted ? encryptionService.decrypt(raw) : raw;
    }

    public void delete(Long userId, String storedFilename) {
        try {
            Path file = resolveUserFile(userId, storedFilename);
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // Best-effort cleanup
        }
    }

    private Path resolveUserDir(Long userId) {
        return rootDir.resolve("user-" + userId).normalize();
    }

    private Path resolveUserFile(Long userId, String storedFilename) {
        Path userDir = resolveUserDir(userId);
        Path target = userDir.resolve(storedFilename).normalize();
        if (!target.startsWith(userDir)) {
            throw new SecurityException("Invalid file path");
        }
        return target;
    }
}
