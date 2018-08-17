/*
 * © 2018 Match Group, LLC.
 */

package com.tinder.app.sse.presenter

import com.tinder.app.sse.domain.StockPriceRepository
import com.tinder.app.sse.target.SseTarget
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import timber.log.Timber
import javax.inject.Inject

class SsePresenter @Inject constructor(
    private val repository: StockPriceRepository
) {
    private lateinit var target: SseTarget
    private val compositeDisposable = CompositeDisposable()

    fun takeTarget(target: SseTarget) {
        this.target = target

        repository.observeMarketSnapshot()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                target.showMarketSnapshot(it)
            }, {
                Timber.e(it)
            })
            .also { compositeDisposable.add(it) }
    }

    fun dropTarget() {
        compositeDisposable.clear()
    }
}
