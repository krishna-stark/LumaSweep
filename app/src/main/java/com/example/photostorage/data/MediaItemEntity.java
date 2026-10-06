package com.example.photostorage.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "media_items")
public class MediaItemEntity {
    @PrimaryKey
    public long mediaStoreId;

    @NonNull public String uri = "";
    @NonNull public String displayName = "";
    @NonNull public String mimeType = "image/*";
    public int width;
    public int height;
    public long fileSize;
    public long dateTaken;
    public long dateModified;
    @NonNull public String relativePath = "";
    public String sha256;
    public String perceptualHash;
    public long scanVersion;
    public Float ocrConfidence;
    public Float blurScore;
    public boolean screenshot;
    public long optimizedBytes;
    public int optimizedWidth;
    public int optimizedHeight;
    public int analysisVersion;
    @NonNull public String semanticLabelsCsv = "";
    public boolean favorite;
    public String protectionReason;
    public int optimizationQuality;
    public float optimizationSimilarity;
    @NonNull public String optimizationMode = "NONE";
    @NonNull public String optimizedMimeType = "";
    @NonNull public String optimizedExtension = "";
    @NonNull public String screenshotCategory = "OTHER";
    @NonNull public String optimizationStatus = "UNKNOWN";
    @NonNull public String optimizationSettingsFingerprint = "";
    public boolean optimizationRequiresReview;

    public MediaItemEntity copyForScan(long version) {
        MediaItemEntity copy = new MediaItemEntity();
        copy.mediaStoreId = mediaStoreId;
        copy.uri = uri;
        copy.displayName = displayName;
        copy.mimeType = mimeType;
        copy.width = width;
        copy.height = height;
        copy.fileSize = fileSize;
        copy.dateTaken = dateTaken;
        copy.dateModified = dateModified;
        copy.relativePath = relativePath;
        copy.sha256 = sha256;
        copy.perceptualHash = perceptualHash;
        copy.scanVersion = version;
        copy.ocrConfidence = ocrConfidence;
        copy.blurScore = blurScore;
        copy.screenshot = screenshot;
        copy.optimizedBytes = optimizedBytes;
        copy.optimizedWidth = optimizedWidth;
        copy.optimizedHeight = optimizedHeight;
        copy.analysisVersion = analysisVersion;
        copy.semanticLabelsCsv = semanticLabelsCsv;
        copy.favorite = favorite;
        copy.protectionReason = protectionReason;
        copy.optimizationQuality = optimizationQuality;
        copy.optimizationSimilarity = optimizationSimilarity;
        copy.optimizationMode = optimizationMode;
        copy.optimizedMimeType = optimizedMimeType;
        copy.optimizedExtension = optimizedExtension;
        copy.screenshotCategory = screenshotCategory;
        copy.optimizationStatus = optimizationStatus;
        copy.optimizationSettingsFingerprint = optimizationSettingsFingerprint;
        copy.optimizationRequiresReview = optimizationRequiresReview;
        return copy;
    }
}
