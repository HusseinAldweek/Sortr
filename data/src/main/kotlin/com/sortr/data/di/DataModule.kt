package com.sortr.data.di

import android.content.Context
import androidx.room.Room
import com.sortr.data.crypto.AesGcmKeyWrapper
import com.sortr.data.crypto.HmacPinHasher
import com.sortr.data.crypto.KeyWrapper
import com.sortr.data.crypto.KeystoreKeys
import com.sortr.data.crypto.PinHasher
import com.sortr.data.db.SortrDatabase
import com.sortr.data.media.GallerySyncManager
import com.sortr.data.repository.AppSettingsImpl
import com.sortr.data.repository.LibraryRepositoryImpl
import com.sortr.data.repository.PeopleRepositoryImpl
import com.sortr.data.repository.PhotoRepositoryImpl
import com.sortr.data.vault.VaultAuthImpl
import com.sortr.data.vault.VaultRepositoryImpl
import com.sortr.domain.repository.AppSettings
import com.sortr.domain.repository.GallerySync
import com.sortr.domain.repository.LibraryRepository
import com.sortr.domain.repository.PeopleRepository
import com.sortr.domain.repository.PhotoRepository
import com.sortr.domain.repository.VaultAuth
import com.sortr.domain.repository.VaultRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** A scope that lives as long as the process, for work that must outlive any one screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object DataProvidersModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): SortrDatabase =
        Room.databaseBuilder(context, SortrDatabase::class.java, "sortr.db").build()

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun keyWrapper(): KeyWrapper {
        // Looked up once: Keystore lookups are slow-ish and the key handle stays valid.
        val masterKey = lazy { KeystoreKeys.vaultMasterKey() }
        return AesGcmKeyWrapper { masterKey.value }
    }

    @Provides
    @Singleton
    fun pinHasher(): PinHasher {
        val hmacKey = lazy { KeystoreKeys.pinHmacKey() }
        return HmacPinHasher { hmacKey.value }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {

    @Binds
    abstract fun photoRepository(impl: PhotoRepositoryImpl): PhotoRepository

    @Binds
    abstract fun gallerySync(impl: GallerySyncManager): GallerySync

    @Binds
    abstract fun libraryRepository(impl: LibraryRepositoryImpl): LibraryRepository

    @Binds
    abstract fun appSettings(impl: AppSettingsImpl): AppSettings

    @Binds
    abstract fun vaultRepository(impl: VaultRepositoryImpl): VaultRepository

    @Binds
    abstract fun vaultAuth(impl: VaultAuthImpl): VaultAuth

    @Binds
    abstract fun peopleRepository(impl: PeopleRepositoryImpl): PeopleRepository
}
