package com.litus_animae.refitted.models

import androidx.paging.DataSource
import androidx.paging.PositionalDataSource

class LiveDataSource<T : Any>(val dataSource: List<T>) :
        PositionalDataSource<T>() {
    override fun loadRange(params: LoadRangeParams,
                           callback: LoadRangeCallback<T>) {
        callback.onResult(dataSource)
    }

    override fun loadInitial(params: LoadInitialParams,
                             callback: LoadInitialCallback<T>) {
        callback.onResult(dataSource, 0)
    }
}

class EmptyDataSourceFactory<T : Any>(private val dataSource: List<T>):
        DataSource.Factory<Int, T>() {

    override fun create(): DataSource<Int, T> {
        return LiveDataSource(dataSource)
    }
}