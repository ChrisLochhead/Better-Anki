package com.betteranki.util

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class AdHelper(private val context: Context) {
    
    // Test ad unit ID for rewarded ads (replace with real ID for production)
    private val rewardedAdUnitId = "ca-app-pub-3940256099942544/5224354917"
    
    private var rewardedAd: RewardedAd? = null
    private var isLoading = false
    
    init {
        // Initialize Mobile Ads SDK
        MobileAds.initialize(context) {}
    }
    
    fun loadRewardedAd(onAdLoaded: () -> Unit = {}, onAdFailed: () -> Unit = {}) {
        if (isLoading || rewardedAd != null) {
            if (rewardedAd != null) onAdLoaded()
            return
        }
        
        isLoading = true
        val adRequest = AdRequest.Builder().build()
        
        RewardedAd.load(
            context,
            rewardedAdUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isLoading = false
                    onAdLoaded()
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    isLoading = false
                    onAdFailed()
                }
            }
        )
    }
    
    fun showRewardedAd(activity: Activity, onAdComplete: () -> Unit, onAdFailed: () -> Unit) {
        rewardedAd?.let { ad ->
            ad.show(activity) { rewardItem ->
                // User earned reward
                rewardedAd = null
                onAdComplete()
            }
        } ?: run {
            // No ad loaded
            onAdFailed()
        }
    }
    
    fun isAdReady(): Boolean = rewardedAd != null
}
