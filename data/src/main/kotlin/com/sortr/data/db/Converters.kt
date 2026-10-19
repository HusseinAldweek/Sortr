package com.sortr.data.db

import androidx.room.TypeConverter
import com.sortr.domain.model.ActionType
import com.sortr.domain.model.MediaType
import com.sortr.domain.model.PhotoStatus

/** Enums are stored by name so the raw SQL in the DAOs stays readable. */
class Converters {

    @TypeConverter
    fun photoStatusToString(value: PhotoStatus?): String? = value?.name

    @TypeConverter
    fun stringToPhotoStatus(value: String?): PhotoStatus? = value?.let(PhotoStatus::valueOf)

    @TypeConverter
    fun actionTypeToString(value: ActionType?): String? = value?.name

    @TypeConverter
    fun stringToActionType(value: String?): ActionType? = value?.let(ActionType::valueOf)

    @TypeConverter
    fun mediaTypeToString(value: MediaType?): String? = value?.name

    @TypeConverter
    fun stringToMediaType(value: String?): MediaType? = value?.let(MediaType::valueOf)
}
