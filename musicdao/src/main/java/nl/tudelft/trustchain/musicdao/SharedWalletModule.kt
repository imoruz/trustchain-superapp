package nl.tudelft.trustchain.musicdao

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import nl.tudelft.ipv8.attestation.trustchain.TrustChainSettings
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainSQLiteStore
import nl.tudelft.ipv8.sqldelight.Database
import nl.tudelft.trustchain.musicdao.core.sharedwallet.SharedWalletCommunity

import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SharedWalletModule {

    @Provides
    @Singleton
    fun provideTrustChainSettings(): TrustChainSettings {
        return TrustChainSettings()
    }

    @Provides
    @Singleton
    fun provideTrustChainStore(@ApplicationContext context: Context): TrustChainSQLiteStore {
        val driver: SqlDriver =
            AndroidSqliteDriver(Database.Schema, context, "sharedwallet-trustchain.db")
        val database = Database(driver)
        return TrustChainSQLiteStore(database)
    }

//    @Provides
//    @Singleton
//    fun provideSharedWalletCommunity(): SharedWalletCommunity {
//        return IPv8Android.getInstance().getOverlay()!!
//    }
}
