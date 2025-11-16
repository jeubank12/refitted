package com.litus_animae.refitted.module

import android.content.Context
import com.litus_animae.refitted.R
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AwsConfigModule {

    @Provides
    @Singleton
    @Named("cognitoIdentityPoolId")
    fun provideCognitoIdentityPoolId(@ApplicationContext context: Context): String {
        return context.getString(R.string.cognito_identity_pool_id)
    }

    @Provides
    @Singleton
    @Named("dynamoTable")
    fun provideDynamoTable(@ApplicationContext context: Context): String {
        return context.getString(R.string.dynamo_table)
    }

    @Provides
    @Singleton
    @Named("firebaseIdSource")
    fun provideFirebaseIdSource(@ApplicationContext context: Context): String {
        return context.getString(R.string.firebase_id_source)
    }
}
