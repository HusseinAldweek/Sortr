package com.sortr.data.db

import com.sortr.domain.model.LibrarySummary
import com.sortr.domain.model.Photo
import com.sortr.domain.model.VaultItem

fun PhotoEntity.toDomain() = Photo(
    id = mediaStoreId,
    uri = uri,
    displayName = displayName,
    mimeType = mimeType,
    dateTaken = dateTaken,
    sizeBytes = size,
    width = width,
    height = height,
    bucketName = bucketName,
    relativePath = relativePath,
    status = status,
    mediaType = mediaType,
    durationMs = durationMs,
    blurScore = blurScore,
)

fun VaultItemEntity.toDomain() = VaultItem(
    id = id,
    originalName = originalName,
    mimeType = mimeType,
    originalSize = originalSize,
    dateTaken = dateTaken,
    addedAt = addedAt,
    width = width,
    height = height,
    sourcePhotoId = sourceMediaStoreId.takeUnless { originalRemoved },
)

fun SummaryRow.toDomain() = LibrarySummary(
    total = total,
    videos = videos,
    unreviewed = unreviewed,
    skipped = skipped,
    kept = kept,
    trashedCount = trashedCount,
    trashedBytes = trashedBytes,
)
