/*
 * © 2018 Match Group, LLC.
 */

package com.tinder.app.sse.view

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tinder.R
import com.tinder.app.sse.domain.MarketSnapshot

class MarketSnapshotView(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private lateinit var stockPriceList: RecyclerView
    private val stockPriceAdapter = StockPriceAdapter()

    override fun onFinishInflate() {
        super.onFinishInflate()
        stockPriceList = findViewById(R.id.stockPriceList)
        stockPriceList.adapter = stockPriceAdapter
        stockPriceList.layoutManager = LinearLayoutManager(context)
    }

    fun showMarketSnapshot(marketSnapshot: MarketSnapshot) {
        stockPriceAdapter.setStockPrices(marketSnapshot.stockPrices)
    }
}
